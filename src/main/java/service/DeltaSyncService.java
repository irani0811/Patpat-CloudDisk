package service;

import dto.ChunkDescriptor;
import dto.ChunkIndexResponse;
import dto.DeltaApplyRequest;
import dto.DeltaApplyResponse;
import model.FileChunk;
import model.FileMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import repository.FileChunkRepository;
import repository.FileMetadataRepository;
import utils.ChunkingUtil;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

@Service
public class DeltaSyncService {

    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    @Autowired
    private FileChunkRepository fileChunkRepository;

    private String normalizeStoragePath(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return rawPath;
        }
        String s = rawPath.trim().replace('\\', '/');
        if (s.startsWith("./")) {
            s = s.substring(2);
        }
        if (!s.startsWith("storage/")) {
            int idx = s.indexOf("storage/");
            if (idx >= 0) {
                s = s.substring(idx);
            } else {
                s = "storage/" + s;
            }
        }
        return java.nio.file.Paths.get(s).normalize().toString();
    }

    public ChunkIndexResponse getChunkIndex(Long fileId, int chunkSize) throws IOException {
        FileMetadata meta = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("文件不存在: " + fileId));
        String storage = normalizeStoragePath(meta.getStoragePath());
        Path path = java.nio.file.Paths.get(storage);
        if (!Files.exists(path)) {
            throw new RuntimeException("存储文件不存在: " + storage);
        }
        long size = Files.size(path);
        int totalChunks = (int) ((size + chunkSize - 1) / chunkSize);

        // 构建索引
        java.util.List<ChunkDescriptor> list = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(path.toFile());
             FileChannel ch = fis.getChannel()) {
            ByteBuffer buf = ByteBuffer.allocate(chunkSize);
            long offset = 0L;
            int idx = 0;
            while (offset < size) {
                buf.clear();
                int toRead = (int) Math.min(chunkSize, size - offset);
                buf.limit(toRead);
                ch.position(offset);
                int read = ch.read(buf);
                buf.flip();
                byte[] data = new byte[buf.remaining()];
                buf.get(data);

                ChunkDescriptor cd = new ChunkDescriptor();
                cd.setIndex(idx);
                cd.setOffset(offset);
                cd.setSize(toRead);
                cd.setSha256(bytesToHex(ChunkingUtil.sha256(data)));
                cd.setWeak32(utils.RollingHashUtil.checksum(data));
                list.add(cd);

                offset += toRead;
                idx++;
            }
        }

        // 持久化块索引（覆盖更新）
        // 简化：先删除原有条目，再批量保存
        List<FileChunk> old = fileChunkRepository.findByFile_IdOrderByChunkIndex(fileId);
        fileChunkRepository.deleteAll(old);
        List<FileChunk> toSave = new ArrayList<>();
        for (ChunkDescriptor cd : list) {
            FileChunk fc = new FileChunk();
            fc.setFile(meta);
            fc.setChunkIndex(cd.getIndex());
            fc.setChunkSize((long) cd.getSize());
            fc.setChunkHash(cd.getSha256());
            fc.setStoragePath(null); // 可选：如果单独存块
            toSave.add(fc);
        }
        fileChunkRepository.saveAll(toSave);

        ChunkIndexResponse resp = new ChunkIndexResponse();
        resp.setFileId(fileId);
        resp.setVersion(meta.getVersion());
        resp.setFileSize(size);
        resp.setFileHashSha256(computeFileSHA256(path));
        resp.setChunkSize(chunkSize);
        resp.setChunks(list);
        return resp;
    }

    public DeltaApplyResponse applyDelta(Long fileId, DeltaApplyRequest req) throws Exception {
        FileMetadata meta = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("文件不存在: " + fileId));
        if (req.getBaseVersion() != null && !req.getBaseVersion().equals(meta.getVersion())) {
            throw new IllegalStateException("版本冲突: base=" + req.getBaseVersion() + ", current=" + meta.getVersion());
        }
        String storage = normalizeStoragePath(meta.getStoragePath());
        Path base = java.nio.file.Paths.get(storage);
        // 先确定目标目录，再在同目录下创建临时文件，确保与目标在同一卷可原子移动
        String newStorage = "storage/" + (meta.getFileHash() == null ? "placeholder" : meta.getFileHash()) + ".dat"; // 仅用于拿到目录
        Path targetDir = java.nio.file.Paths.get("storage");
        Files.createDirectories(targetDir);
        Path tmp = Files.createTempFile(targetDir, "delta_apply_", ".dat");

        int chunkSize = req.getChunkSize();
        long total = req.getTargetFileSize();
        int opsCount = 0;
        long copyBytes = 0L;
        long dataBytes = 0L;
        String moveMode = "";

        try (FileChannel outCh = FileChannel.open(tmp, java.nio.file.StandardOpenOption.WRITE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
            try (FileChannel baseCh = Files.exists(base) ? FileChannel.open(base) : null) {
                // 优先使用 rsync 风格 ops
                if (req.getOps() != null && !req.getOps().isEmpty()) {
                    for (dto.DeltaOp op : req.getOps()) {
                        opsCount++;
                        if (op.getType() == dto.DeltaOp.Type.COPY) {
                            if (baseCh == null) throw new IllegalStateException("缺少基线文件以执行COPY");
                            long pos = op.getSrcOffset();
                            long remaining = op.getLength();
                            copyBytes += op.getLength();
                            while (remaining > 0) {
                                long n = baseCh.transferTo(pos, remaining, outCh);
                                if (n <= 0) break;
                                pos += n;
                                remaining -= n;
                            }
                        } else if (op.getType() == dto.DeltaOp.Type.DATA) {
                            byte[] data = Base64.getDecoder().decode(op.getDataBase64());
                            if (data.length != op.getLength()) {
                                throw new IllegalArgumentException("DATA长度不匹配");
                            }
                            dataBytes += op.getLength();
                            outCh.write(ByteBuffer.wrap(data));
                        }
                    }
                } else {
                    // 兼容旧 blocks 路径（固定分块对齐）
                    int totalChunks = (int) ((total + chunkSize - 1) / chunkSize);
                    Map<Integer, dto.DeltaBlock> map = new HashMap<>();
                    if (req.getBlocks() != null) {
                        for (dto.DeltaBlock b : req.getBlocks()) {
                            map.put(b.getIndex(), b);
                        }
                    }
                    ByteBuffer buffer = ByteBuffer.allocate(chunkSize);
                    for (int i = 0; i < totalChunks; i++) {
                        long off = (long) i * chunkSize;
                        int len = (int) Math.min(chunkSize, total - off);
                        dto.DeltaBlock blk = map.get(i);
                        if (blk != null && blk.getDataBase64() != null) {
                            dataBytes += len;
                            byte[] data = Base64.getDecoder().decode(blk.getDataBase64());
                            if (data.length != len) {
                                throw new IllegalArgumentException("块大小不匹配 index=" + i);
                            }
                            outCh.write(ByteBuffer.wrap(data));
                        } else {
                            copyBytes += len;
                            if (baseCh == null) throw new IllegalStateException("缺少基线文件且未提供块数据");
                            long pos = off;
                            long remaining = len;
                            while (remaining > 0) {
                                long n = baseCh.transferTo(pos, remaining, outCh);
                                if (n <= 0) break;
                                pos += n;
                                remaining -= n;
                            }
                        }
                    }
                }
            }
        }

        // 校验SHA-256
        String actualSha256 = computeFileSHA256(tmp);
        if (req.getTargetFileHashSha256() != null && !req.getTargetFileHashSha256().equalsIgnoreCase(actualSha256)) {
            throw new IllegalStateException("校验失败: 目标SHA-256不匹配");
        }

        // 计算MD5作为系统fileHash
        String oldHash = meta.getFileHash();
        String oldStoragePath = normalizeStoragePath(meta.getStoragePath());
        String md5 = computeFileMD5(tmp);
        newStorage = "storage/" + md5 + ".dat";
        Path target = java.nio.file.Paths.get(newStorage);
        Files.createDirectories(target.getParent());
        try {
            Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            moveMode = "ATOMIC_MOVE";
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            // 回退为非原子替换
            Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            moveMode = "REPLACE_EXISTING";
        } catch (java.nio.file.AccessDeniedException ex) {
            // 某些Windows环境下目标被占用或策略限制，退回为 copy + delete
            Files.copy(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            moveMode = "COPY_DELETE";
            try { Files.deleteIfExists(tmp); } catch (Exception ignore) {}
        }

        // 更新元数据
        meta.setFileSize(total);
        meta.setFileHash(md5);
        meta.setStoragePath(newStorage);
        meta.setUpdatedAt(java.time.LocalDateTime.now());
        meta.setVersion(meta.getVersion() == null ? 1 : meta.getVersion() + 1);
        fileMetadataRepository.save(meta);

        // 清理旧存储文件（当且仅当无其他文件仍引用旧hash）
        try {
            if (oldHash != null && oldStoragePath != null && !oldStoragePath.equals(newStorage)) {
                java.util.List<FileMetadata> refs = fileMetadataRepository.findByFileHash(oldHash);
                if (refs == null || refs.isEmpty()) {
                    java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(oldStoragePath));
                }
            }
        } catch (Exception ignore) {}

        // 重建块索引
        getChunkIndex(meta.getId(), chunkSize);

        System.out.println(
                "Delta apply summary: fileId=" + meta.getId()
                        + ", baseVersion=" + req.getBaseVersion()
                        + ", opsCount=" + (req.getOps() != null ? req.getOps().size() : 0)
                        + ", copyBytes=" + copyBytes
                        + ", dataBytes=" + dataBytes
                        + ", moveMode=" + moveMode
                        + ", newVersion=" + meta.getVersion()
                        + ", storagePath=" + newStorage
        );

        DeltaApplyResponse resp = new DeltaApplyResponse();
        resp.setFileId(meta.getId());
        resp.setNewVersion(meta.getVersion());
        resp.setFileSize(total);
        resp.setFileHashMd5(md5);
        resp.setFileHashSha256(actualSha256);
        resp.setStoragePath(newStorage);
        return resp;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String computeFileSHA256(Path file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file.toFile())) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = fis.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private String computeFileMD5(Path file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file.toFile())) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = fis.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
