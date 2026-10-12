package service;

import model.FileChunk;
import model.FileMetadata;
import model.FileVersion;
import model.FileVersionChunk;
import model.User;
import repository.FileMetadataRepository;
import repository.FileVersionChunkRepository;
import repository.FileVersionRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.io.ByteArrayOutputStream;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import utils.EncryptionUtil;
import utils.CompressionUtil;


/**
 * 版本控制服务类
 */
@Service

public class VersionService {
    
    @Autowired
    private FileMetadataRepository fileMetadataRepository;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private utils.DeduplicationUtil deduplicationUtil;

    
    
    @Autowired
    private ShareService shareService;

    @Autowired
    private FileVersionChunkRepository versionChunkRepository;

    @Autowired
    private EncryptionUtil encryptionUtil;

    @Autowired
    private CompressionUtil compressionUtil;

    @Autowired
private FileVersionRepository fileVersionRepository;

    private String buildVersionStoragePath(FileMetadata file, int versionNumber) {
        return file.getStoragePath() + ".v" + versionNumber;
    }

    public FileVersion createSnapshot(FileMetadata file,
                                    List<FileChunk> orderedChunks,
                                    String description,
                                    String fileHash,
                                    long fileSize,
                                    boolean markCurrent) {

        int newVersionNumber = getNextVersionNumber(file);
        markPreviousVersionsAsNonCurrent(file);

        FileVersion version = new FileVersion();
        version.setVersionNumber(newVersionNumber);
        version.setFileHash(fileHash);
        version.setFileSize(fileSize);
        version.setChangeDescription(description);
        version.setIsCurrent(markCurrent);
        version.setFile(file);
        version.setCreatedAt(LocalDateTime.now());

        List<FileVersionChunk> links = new ArrayList<>();
        for (int i = 0; i < orderedChunks.size(); i++) {
            FileVersionChunk link = new FileVersionChunk();
            link.setVersion(version);
            link.setChunk(orderedChunks.get(i));
            link.setChunkIndex(i);
            links.add(link);
        }
        version.setVersionChunks(links);
        if (file.getVersions() == null) {
            file.setVersions(new ArrayList<>());
        }

        file.getVersions().add(version);
        file.setVersion(newVersionNumber);
        file.setUpdatedAt(LocalDateTime.now());

        fileMetadataRepository.save(file); // cascade saves version + links
        return version;
    }

    public void duplicateCurrentAsNewVersion(FileMetadata file, String desc) {
        FileVersion current = file.getVersions().stream()
            .filter(FileVersion::getIsCurrent)
            .findFirst()
            .orElse(null);
        if (current == null) {
            return;
        }
        List<FileVersionChunk> links =
            versionChunkRepository.findByVersionOrderByChunkIndex(current);

        List<FileChunk> orderedChunks = links.stream()
            .sorted(Comparator.comparing(FileVersionChunk::getChunkIndex))
            .map(FileVersionChunk::getChunk)
            .collect(Collectors.toList());

        createSnapshot(file, orderedChunks,
                desc != null ? desc : "复制未变更版本",
                current.getFileHash(),
                current.getFileSize(),
                true);
    }

    public byte[] assembleVersion(FileVersion version) throws Exception {
    List<FileVersionChunk> links =
        versionChunkRepository.findByVersionOrderByChunkIndex(version);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (FileVersionChunk link : links) {
            FileChunk chunk = link.getChunk();
            byte[] bytes = Files.readAllBytes(Paths.get(chunk.getStoragePath()));
            if (Boolean.TRUE.equals(chunk.getIsEncrypted())) {
                bytes = encryptionUtil.decryptCBC(bytes, version.getFile().getEncryptionKey());
            }
            if (Boolean.TRUE.equals(chunk.getIsCompressed())) {
                bytes = compressionUtil.decompressFile(bytes, "GZIP");
            }
            out.write(bytes);
        }
        return out.toByteArray();
    }
    /**
     * 创建文件版本
     */
    public FileVersion createVersion(Long fileId, Long userId, String changeDescription) throws Exception {
        FileMetadata file = fileMetadataRepository.findById(fileId)
            .orElseThrow(() -> new RuntimeException("文件不存在"));
        
        if (!file.getOwner().getId().equals(userId)) {
            throw new RuntimeException("无权限为此文件创建版本");
        }

        if (file.getVersions() == null) {
            file.setVersions(new ArrayList<>());
        }

        Path sourcePath = Paths.get(file.getStoragePath());
        if (!Files.exists(sourcePath)) {
            throw new RuntimeException("原文件不存在，无法创建版本");
        }

        int newVersionNumber = getNextVersionNumber(file);
        String fileHash = calculateFileHash(file.getStoragePath());

        FileVersion current = file.getVersions().stream()
            .filter(FileVersion::getIsCurrent)
            .findFirst()
            .orElse(null);
        if (current != null && fileHash.equals(current.getFileHash())) {
            if (changeDescription != null && !changeDescription.isBlank() && !changeDescription.equals(current.getChangeDescription())) {
                current.setChangeDescription(changeDescription);
                fileMetadataRepository.save(file);
            }
            return current;
 }

        markPreviousVersionsAsNonCurrent(file);

        FileVersion version = new FileVersion();
        version.setVersionNumber(newVersionNumber);
        version.setFileHash(fileHash);
        version.setFileSize(file.getFileSize());
        version.setChangeDescription(changeDescription);
        version.setIsCurrent(true);
        version.setFile(file);
        version.setCreatedAt(LocalDateTime.now());

        String versionPath = buildVersionStoragePath(file, newVersionNumber);
        Path versionFilePath = Paths.get(versionPath);
        Path parent = versionFilePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        Files.copy(sourcePath, versionFilePath, StandardCopyOption.REPLACE_EXISTING);
        version.setVersionStoragePath(versionPath);

        file.getVersions().add(version);
        file.setVersion(newVersionNumber);
        file.setUpdatedAt(LocalDateTime.now());
        fileMetadataRepository.save(file);

        return version;
    }


public Map<String, Object> downloadVersion(Long fileId, Integer versionNumber, Long userId) throws Exception {
    FileMetadata file = fileMetadataRepository.findById(fileId)
        .orElseThrow(() -> new RuntimeException("文件不存在"));

    if (!file.getOwner().getId().equals(userId)) {
        if (!shareService.hasFileAccess(fileId, userId, "READ")) {
            throw new RuntimeException("无权限下载该版本");
        }
    }

    FileVersion version = file.getVersions().stream()
        .filter(v -> v.getVersionNumber().equals(versionNumber))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("版本不存在"));

    byte[] data;
    if (version.getVersionStoragePath() != null) {
        data = Files.readAllBytes(Paths.get(version.getVersionStoragePath()));
        if (Boolean.TRUE.equals(file.getIsEncrypted())) {
            data = encryptionUtil.decryptCBC(data, file.getEncryptionKey());
        }
        if (Boolean.TRUE.equals(file.getIsCompressed())) {
            data = compressionUtil.decompressFile(data, "GZIP");
        }
    } else {
        data = assembleVersion(version);
    }

    Map<String, Object> result = new HashMap<>();
    result.put("data", data);
    result.put("filename", buildVersionedFilename(file.getFilename(), versionNumber));
    result.put("mimeType", file.getMimeType() != null ? file.getMimeType() : "application/octet-stream");
    return result;
}

private String buildVersionedFilename(String originalName, int versionNumber) {
    if (originalName == null || originalName.isBlank()) {
        originalName = "file";
    }
    String suffix = "_v" + versionNumber;
    int dotIndex = originalName.lastIndexOf('.');
    if (dotIndex > 0 && dotIndex < originalName.length() - 1) {
        return originalName.substring(0, dotIndex) + suffix + originalName.substring(dotIndex);
    }
    return originalName + suffix;
}
    
    /**
     * 回滚到指定版本
     */
    public boolean rollbackToVersion(Long fileId, Long userId, Integer versionNumber) {
        try {
            FileMetadata file = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("文件不存在"));
            
            if (!file.getOwner().getId().equals(userId)) {
                if (!shareService.hasFileAccess(fileId, userId, "WRITE")) {
                    throw new RuntimeException("无权限回滚此文件");
                }
            }

            if (file.getVersions() == null || file.getVersions().isEmpty()) {
                throw new RuntimeException("没有可用的历史版本");
            }
            
            Optional<FileVersion> targetVersion = file.getVersions().stream()
                .filter(v -> v.getVersionNumber().equals(versionNumber))
                .findFirst();
            
            if (targetVersion.isEmpty()) {
                throw new RuntimeException("指定版本不存在");
            }
            
            FileVersion version = targetVersion.get();
            byte[] assembled = assembleVersion(version);
            Path filePath = Paths.get(file.getStoragePath());
            Path parent = filePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.write(filePath, assembled, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            markPreviousVersionsAsNonCurrent(file);
            version.setIsCurrent(true);

            file.setFileHash(version.getFileHash());
            file.setFileSize(version.getFileSize());
            file.setVersion(versionNumber);
            file.setUpdatedAt(LocalDateTime.now());
            fileMetadataRepository.save(file);

            markPreviousVersionsAsNonCurrent(file);
            version.setIsCurrent(true);
            
            file.setFileHash(version.getFileHash());
            file.setFileSize(version.getFileSize());
            file.setVersion(versionNumber);
            file.setUpdatedAt(LocalDateTime.now());
            fileMetadataRepository.save(file);
            
            return true;
            
        } catch (Exception e) {
            System.err.println("回滚版本失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取文件版本列表
     */
    public List<FileVersion> getFileVersions(Long fileId, Long userId) {
        FileMetadata file = fileMetadataRepository.findById(fileId)
            .orElseThrow(() -> new RuntimeException("文件不存在"));
        
        if (!file.getOwner().getId().equals(userId)) {
            if (!shareService.hasFileAccess(fileId, userId, "READ")) {
                throw new RuntimeException("无权限查看此文件的版本");
            }
        }
        
        return file.getVersions();
    }
    
    /**
     * 比较两个版本的差异
     */
    public Map<String, Object> compareVersions(Long fileId, Long userId, Integer version1, Integer version2) {
        FileMetadata file = fileMetadataRepository.findById(fileId)
            .orElseThrow(() -> new RuntimeException("文件不存在"));
        
        if (!file.getOwner().getId().equals(userId)) {
            throw new RuntimeException("无权限比较此文件的版本");
        }
        
        Optional<FileVersion> v1 = file.getVersions().stream()
            .filter(v -> v.getVersionNumber().equals(version1))
            .findFirst();
        
        Optional<FileVersion> v2 = file.getVersions().stream()
            .filter(v -> v.getVersionNumber().equals(version2))
            .findFirst();
        
        if (v1.isEmpty() || v2.isEmpty()) {
            throw new RuntimeException("指定版本不存在");
        }
        
        Map<String, Object> comparison = new HashMap<>();
        comparison.put("version1", v1.get());
        comparison.put("version2", v2.get());
        comparison.put("sizeDifference", v2.get().getFileSize() - v1.get().getFileSize());
        comparison.put("hashDifferent", !v1.get().getFileHash().equals(v2.get().getFileHash()));
        
        return comparison;
    }
    
    /**
     * 删除指定版本
     */
    public boolean deleteVersion(Long fileId, Long userId, Integer versionNumber) {
        try {
            FileMetadata file = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("文件不存在"));
            
            if (!file.getOwner().getId().equals(userId)) {
                throw new RuntimeException("无权限删除此文件的版本");
            }
            
            if (file.getVersion().equals(versionNumber)) {
                throw new RuntimeException("不能删除当前版本");
            }

            if (file.getVersions() == null || file.getVersions().isEmpty()) {
                return false;
            }

            Optional<FileVersion> targetVersion = file.getVersions().stream()
                .filter(v -> v.getVersionNumber().equals(versionNumber))
                .findFirst();

            if (targetVersion.isEmpty()) {
                return false;
            }

            FileVersion version = targetVersion.get();
            if (version.getVersionStoragePath() != null) {
                Path versionFilePath = Paths.get(version.getVersionStoragePath());
                try {
                    Files.deleteIfExists(versionFilePath);
                } catch (IOException ioe) {
                    System.err.println("删除版本文件失败: " + ioe.getMessage());
                }
            }


            boolean removed = file.getVersions().remove(version);

            if (removed) {
                fileMetadataRepository.save(file);
                fileVersionRepository.delete(version);
            }
            
            return removed;
            
        } catch (Exception e) {
            System.err.println("删除版本失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取下一个版本号
     */
    private int getNextVersionNumber(FileMetadata file) {
        if (file.getVersions() == null || file.getVersions().isEmpty()) {
            return 1;
        }
        
        return file.getVersions().stream()
            .mapToInt(FileVersion::getVersionNumber)
            .max()
            .orElse(0) + 1;
    }
    
    /**
     * 计算文件哈希
     */
    private String calculateFileHash(String filePath) throws IOException, Exception {
        File file = new File(filePath);
        return deduplicationUtil.calculateFileHash(file);
    }
    
    /**
     * 将之前的版本标记为非当前版本
     */
    private void markPreviousVersionsAsNonCurrent(FileMetadata file) {
        if (file.getVersions() == null) {
            return;
        }
        file.getVersions().forEach(v -> v.setIsCurrent(false));
    }
    
   
}

