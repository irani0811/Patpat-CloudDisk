package service;

import dto.ChunkDedupInitRequest;
import dto.ChunkDedupInitResponse;
import dto.ChunkUploadRequest;
import dto.FinalizeAssembleRequest;
import model.FileChunk;
import model.FileMetadata;
import model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import repository.FileChunkRepository;
import repository.FileMetadataRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class DedupService {

    @Autowired
    private FileChunkRepository fileChunkRepository;

    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    @Autowired
    private UserService userService;

    @Value("${file.storage.path:./storage/}")
    private String storagePath;

    public ChunkDedupInitResponse initDedup(ChunkDedupInitRequest req, Long userId) {
        List<FileMetadata> existing = fileMetadataRepository.findByFileHash(req.getFileHash());
        ChunkDedupInitResponse resp = new ChunkDedupInitResponse();
        if (!existing.isEmpty()) {
            resp.setFileAlreadyExists(true);
            resp.setExistingFileId(existing.get(0).getId());
            resp.setChunkSize(req.getChunkSize());
            resp.setTotalChunks(req.getChunkHashes() != null ? req.getChunkHashes().size() : 0);
            resp.setNeedUploadIndices(Collections.emptyList());
            resp.setNeedUploadHashes(Collections.emptyList());
            return resp;
        }
        List<Integer> needIdx = new ArrayList<>();
        List<String> needHashes = new ArrayList<>();
        List<String> hashes = req.getChunkHashes();
        if (hashes != null) {
            for (int i = 0; i < hashes.size(); i++) {
                String h = hashes.get(i);
                if (!fileChunkRepository.existsByChunkHash(h)) {
                    needIdx.add(i);
                    needHashes.add(h);
                }
            }
        }
        resp.setFileAlreadyExists(false);
        resp.setExistingFileId(null);
        resp.setChunkSize(req.getChunkSize());
        resp.setTotalChunks(hashes != null ? hashes.size() : 0);
        resp.setNeedUploadIndices(needIdx);
        resp.setNeedUploadHashes(needHashes);
        return resp;
    }

    public Map<String, Object> uploadChunk(ChunkUploadRequest req, Long userId) throws IOException {
        Map<String, Object> result = new HashMap<>();
        if (!fileChunkRepository.existsByChunkHash(req.getChunkHash())) {
            byte[] data = Base64.getDecoder().decode(req.getDataBase64());
            String p = saveChunkToStorage(data, req.getChunkHash());
            FileChunk chunk = new FileChunk();
            chunk.setChunkHash(req.getChunkHash());
            chunk.setChunkSize((long) data.length);
            chunk.setChunkIndex(req.getChunkIndex());
            chunk.setStoragePath(p);
            chunk.setIsCompressed(false);
            chunk.setIsEncrypted(false);
            chunk.setCreatedAt(LocalDateTime.now());
            fileChunkRepository.save(chunk);
        }
        result.put("success", true);
        return result;
    }

    public Map<String, Object> finalizeAssemble(FinalizeAssembleRequest req, Long userId) throws Exception {
        User user = userService.getUserById(userId).orElseThrow(() -> new RuntimeException("用户不存在"));
        // 确保所有块都存在
        for (String hash : req.getChunkHashes()) {
            if (!fileChunkRepository.existsByChunkHash(hash)) {
                throw new RuntimeException("缺失块: " + hash);
            }
        }
        // 组装文件
        String assembledPath = assembleFromChunks(req.getChunkHashes(), req.getFileHash());
        // 创建文件元数据
        FileMetadata meta = new FileMetadata();
        meta.setFilename(req.getFilename());
        meta.setFilePath(req.getPath());
        long size = Files.size(Paths.get(assembledPath));
        meta.setFileSize(size);
        meta.setFileHash(req.getFileHash());
        meta.setMimeType("application/octet-stream");
        meta.setIsDirectory(false);
        meta.setVersion(1);
        meta.setIsCompressed(false);
        meta.setIsEncrypted(false);
        meta.setStoragePath(assembledPath);
        meta.setOwner(user);
        meta.setCreatedAt(LocalDateTime.now());
        meta.setUpdatedAt(LocalDateTime.now());
        FileMetadata saved = fileMetadataRepository.save(meta);
        // 建立块与文件的关联（复制行，绑定 file 和 index）
        for (int i = 0; i < req.getChunkHashes().size(); i++) {
            String h = req.getChunkHashes().get(i);
            Optional<FileChunk> any = fileChunkRepository.findByChunkHash(h);
            FileChunk c = new FileChunk();
            c.setChunkHash(h);
            c.setChunkSize(any.map(FileChunk::getChunkSize).orElse(0L));
            c.setChunkIndex(i);
            c.setStoragePath(any.map(FileChunk::getStoragePath).orElse(null));
            c.setIsCompressed(false);
            c.setIsEncrypted(false);
            c.setCreatedAt(LocalDateTime.now());
            c.setFile(saved);
            fileChunkRepository.save(c);
        }
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("fileId", saved.getId());
        res.put("storagePath", assembledPath);
        res.put("fileSize", size);
        return res;
    }

    private String saveChunkToStorage(byte[] chunkData, String chunkHash) throws IOException {
        String fileName = chunkHash + ".chunk";
        Path p = Paths.get(this.storagePath + "chunks/" + fileName);
        Files.createDirectories(p.getParent());
        if (!Files.exists(p)) {
            Files.write(p, chunkData);
        }
        return p.toString();
    }

    private String assembleFromChunks(List<String> chunkHashes, String fileHash) throws IOException {
        String fileName = fileHash + ".dat";
        Path out = Paths.get(this.storagePath + fileName);
        Files.createDirectories(out.getParent());
        // 覆盖写入
        if (Files.exists(out)) Files.delete(out);
        Files.createFile(out);
        for (String h : chunkHashes) {
            Path cp = Paths.get(this.storagePath + "chunks/" + h + ".chunk");
            byte[] data = Files.readAllBytes(cp);
            Files.write(out, data, java.nio.file.StandardOpenOption.APPEND);
        }
        return out.toString();
    }
}
