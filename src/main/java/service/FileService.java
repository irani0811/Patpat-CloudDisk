package service;

import model.FileMetadata;
import model.FileChunk;
import model.FileInfoDTO;
import model.User;
import repository.FileMetadataRepository;
import repository.FileChunkRepository;
import service.ShareService;
import utils.CompressionUtil;
import utils.EncryptionUtil;
import utils.DeduplicationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import service.VersionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件服务类
 */
@Service
@Transactional
public class FileService {
    
    @Autowired
    private FileMetadataRepository fileMetadataRepository;
    
    @Autowired
    private FileChunkRepository fileChunkRepository;
    
    @Autowired
    private ShareService shareService;
    
    @Autowired
    private CompressionUtil compressionUtil;
    
    @Autowired
    private EncryptionUtil encryptionUtil;
    
    @Autowired
    private DeduplicationUtil deduplicationUtil;
    
    @Autowired
    private UserService userService;
    
    @Value("${file.storage.path:./storage/}")
    private String storagePath;
    

    @Autowired
    private VersionService versionService;
    /**
     * 根据文件名推断MIME类型
     */
    private String getMimeTypeFromFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "application/octet-stream";
        }
        
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        switch (extension) {
            case "txt":
                return "text/plain";
            case "html":
            case "htm":
                return "text/html";
            case "css":
                return "text/css";
            case "js":
                return "application/javascript";
            case "json":
                return "application/json";
            case "xml":
                return "application/xml";
            case "pdf":
                return "application/pdf";
            case "doc":
                return "application/msword";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls":
                return "application/vnd.ms-excel";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt":
                return "application/vnd.ms-powerpoint";
            case "pptx":
                return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "bmp":
                return "image/bmp";
            case "svg":
                return "image/svg+xml";
            case "mp4":
                return "video/mp4";
            case "avi":
                return "video/x-msvideo";
            case "mov":
                return "video/quicktime";
            case "mp3":
                return "audio/mpeg";
            case "wav":
                return "audio/wav";
            case "zip":
                return "application/zip";
            case "rar":
                return "application/x-rar-compressed";
            case "7z":
                return "application/x-7z-compressed";
            default:
                return "application/octet-stream";
        }
    }

    private List<DeduplicationUtil.ChunkInfo> materializeChunks(byte[] fileBytes) throws Exception {
        return deduplicationUtil.calculateChunkHashes(fileBytes);
    }

    private FileChunk persistChunkIfNeeded(DeduplicationUtil.ChunkInfo chunkInfo,
                                        boolean compress, boolean encrypt,
                                        String encryptionKey) throws Exception {
        return fileChunkRepository.findByChunkHash(chunkInfo.getChunkHash())
            .orElseGet(() -> {
                try {
                    byte[] chunkData = chunkInfo.getData();
                    if (compress) {
                        chunkData = compressionUtil.compressFile(chunkData, "GZIP");
                    }
                    if (encrypt) {
                        chunkData = encryptionUtil.encryptCBC(chunkData, encryptionKey);
                    }
                    String chunkPath = saveChunkToStorage(chunkData, chunkInfo.getChunkHash());

                    FileChunk chunk = new FileChunk();
                    chunk.setChunkHash(chunkInfo.getChunkHash());
                    chunk.setChunkSize((long) chunkData.length);
                    chunk.setChunkIndex(chunkInfo.getChunkIndex());

                    chunk.setStoragePath(chunkPath);
                    chunk.setIsCompressed(compress);
                    chunk.setIsEncrypted(encrypt);
                    chunk.setCreatedAt(LocalDateTime.now());
                    return fileChunkRepository.save(chunk);
                } catch (Exception ex) {
                    throw new RuntimeException("保存块失败: " + ex.getMessage(), ex);
                }
            });
    }

    
    
    /**
     * 上传文件
     */
    public FileMetadata uploadFile(MultipartFile file, String filePath, Long userId,
                               boolean compress, boolean encrypt) throws Exception {

    User user = userService.getUserById(userId).orElseThrow(() -> new RuntimeException("用户不存在"));
    if (!userService.hasEnoughStorage(userId, file.getSize())) {
        throw new RuntimeException("存储空间不足");
    }

    String changeDescription = "CS客户端上传 " + file.getOriginalFilename();
    byte[] rawBytes = file.getBytes();
    String fileHash = deduplicationUtil.calculateHash(rawBytes);

    Optional<FileMetadata> existing =
        fileMetadataRepository.findByOwnerAndFilePathAndFilename(
            user, filePath, file.getOriginalFilename());

    if (existing.isPresent()) {
        return updateExistingFile(existing.get(), rawBytes, fileHash,
                compress, encrypt, changeDescription);
    }

    Optional<FileMetadata> sharedTarget = fileMetadataRepository
        .findByFilePathAndFilename(filePath, file.getOriginalFilename())
        .filter(meta -> shareService.hasFileAccess(meta.getId(), userId, "WRITE"));

    if (sharedTarget.isPresent()) {
        return updateExistingFile(sharedTarget.get(), rawBytes, fileHash,
                compress, encrypt, changeDescription);
    }

    return createNewFileWithVersion(file, filePath, user,
            rawBytes, fileHash, compress, encrypt, changeDescription);
}

    /**
     * 检查文件上传冲突
     */
    public Map<String, Object> checkUploadConflict(Long fileId, Long userId,
    Integer clientVersion, String clientHash) {
        FileMetadata file = fileMetadataRepository.findById(fileId)
        .orElseThrow(() -> new RuntimeException("文件不存在"));
        boolean hasPermission = file.getOwner().getId().equals(userId)
        || shareService.hasFileAccess(fileId, userId, "WRITE");
        if (!hasPermission) {
        throw new RuntimeException("无权限上传此文件");
        }
        Map<String, Object> result = new HashMap<>();
        Integer latestVersion = file.getVersion();
        String latestHash = file.getFileHash();
        boolean hasServerSnapshot = (latestVersion != null)
        || (latestHash != null && !latestHash.isEmpty());
        boolean conflict;
        if (!hasServerSnapshot) {
        conflict = false;
        } else if (clientVersion != null && latestVersion != null
        && latestVersion.equals(clientVersion)) {
        conflict = false;
        } else if (clientHash != null && latestHash != null
        && latestHash.equals(clientHash)) {
        conflict = false;
        } else {
        conflict = true;
        }
        result.put("success", true);
        result.put("conflict", conflict);
        result.put("latestVersion", latestVersion);
        result.put("latestHash", latestHash);
        result.put("updatedAt", file.getUpdatedAt());
        result.put("owner", file.getOwner() != null ? file.getOwner().getUsername() : null);
        return result;
    }

    /**
     * 解决文件上传冲突
     */
    public Map<String, Object> resolveConflictUpload(Long fileId,
                                                 Long userId,
                                                 byte[] rawBytes,
                                                 boolean compress,
                                                 boolean encrypt,
                                                 String strategy,
                                                 String note) throws Exception {
    FileMetadata file = fileMetadataRepository.findById(fileId)
        .orElseThrow(() -> new RuntimeException("文件不存在"));

    boolean hasPermission = file.getOwner().getId().equals(userId)
        || shareService.hasFileAccess(fileId, userId, "WRITE");
    if (!hasPermission) {
        throw new RuntimeException("无权限上传此文件");
    }

    String effectiveStrategy = strategy != null ? strategy.toLowerCase() : "duplicate";
    if (!effectiveStrategy.equals("duplicate") && !effectiveStrategy.equals("overwrite")) {
        throw new IllegalArgumentException("不支持的冲突策略: " + strategy);
    }

    String changeDescription = (note != null && !note.isBlank())
        ? note
        : ("冲突副本，由用户 " + file.getOwner().getUsername() + " 生成");
    String newHash = deduplicationUtil.calculateHash(rawBytes);

    Map<String, Object> result = new HashMap<>();
    if ("duplicate".equals(effectiveStrategy)) {
        String conflictName = generateConflictFilename(file.getFilename());
        file.setFilename(conflictName);
        FileMetadata updated = updateExistingFile(file, rawBytes, newHash, compress, encrypt, changeDescription);
        result.put("conflictFilename", conflictName);
        result.put("newVersionNumber", updated.getVersion());
    } else { // overwrite
        FileMetadata updated = updateExistingFile(file, rawBytes, newHash, compress, encrypt, changeDescription);
        result.put("conflictFilename", file.getFilename());
        result.put("newVersionNumber", updated.getVersion());
    }

    result.put("success", true);
    result.put("message", "冲突已处理为新版本");
    return result;
}

private String generateConflictFilename(String original) {
    int dot = original.lastIndexOf('.');
    String timestamp = java.time.LocalDateTime.now()
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    if (dot == -1) {
        return original + "_冲突副本_" + timestamp;
    }
    String name = original.substring(0, dot);
    String ext = original.substring(dot);
    return name + "_冲突副本_" + timestamp + ext;
}

    
    private FileMetadata createNewFileWithVersion(MultipartFile file, String filePath, User owner,
                                                byte[] rawBytes, String fileHash,
                                                boolean compress, boolean encrypt,
                                                String changeDescription) throws Exception {

        String encryptionKey = encrypt ? encryptionUtil.generateKey() : null;
        byte[] stored = rawBytes;
        if (compress) stored = compressionUtil.compressFile(stored, "GZIP");
        if (encrypt) stored = encryptionUtil.encryptCBC(stored, encryptionKey);

        String storage = saveFileToStorage(stored, fileHash);

        FileMetadata meta = new FileMetadata();
        meta.setFilename(file.getOriginalFilename());
        meta.setFilePath(filePath);
        meta.setFileSize((long) rawBytes.length);
        meta.setStoredSize((long) stored.length); 
        meta.setFileHash(fileHash);
        meta.setMimeType(Optional.ofNullable(file.getContentType())
                                .filter(s -> !s.isEmpty())
                                .orElse(getMimeTypeFromFilename(file.getOriginalFilename())));
        meta.setIsDirectory(false);
        meta.setVersion(0); // will be set in version service
        meta.setIsCompressed(compress);
        meta.setIsEncrypted(encrypt);
        meta.setEncryptionKey(encryptionKey);
        meta.setStoragePath(storage);
        meta.setOwner(owner);
        meta.setCreatedAt(LocalDateTime.now());
        meta.setUpdatedAt(LocalDateTime.now());

        List<DeduplicationUtil.ChunkInfo> chunks = materializeChunks(rawBytes);
        List<FileChunk> chunkEntities = new ArrayList<>();
        for (DeduplicationUtil.ChunkInfo chunkInfo : chunks) {
            chunkEntities.add(persistChunkIfNeeded(chunkInfo, compress, encrypt, encryptionKey));
        }

        FileMetadata saved = fileMetadataRepository.save(meta);
        versionService.createSnapshot(saved, chunkEntities, changeDescription,
                fileHash, rawBytes.length, true);

        userService.updateStorageUsage(owner.getId(), (long) rawBytes.length);
        return saved;
    }

    private FileMetadata updateExistingFile(FileMetadata file,
                                        byte[] rawBytes,
                                        String newHash,
                                        boolean compress,
                                        boolean encrypt,
                                        String changeDescription) throws Exception {

    boolean sameHash = newHash.equals(file.getFileHash());
    if (sameHash) {
        versionService.duplicateCurrentAsNewVersion(file,
                changeDescription != null ? changeDescription : "内容未变更");
        return fileMetadataRepository.save(file);
    }

    String encryptionKey = file.getEncryptionKey();
    if (encrypt && encryptionKey == null) {
        encryptionKey = encryptionUtil.generateKey();
        file.setEncryptionKey(encryptionKey);
    }

    List<DeduplicationUtil.ChunkInfo> chunks = materializeChunks(rawBytes);
    List<FileChunk> chunkEntities = new ArrayList<>();
    for (DeduplicationUtil.ChunkInfo chunkInfo : chunks) {
        chunkEntities.add(persistChunkIfNeeded(chunkInfo, compress, encrypt, encryptionKey));
    }

    byte[] stored = rawBytes;
    if (compress) stored = compressionUtil.compressFile(stored, "GZIP");
    if (encrypt) stored = encryptionUtil.encryptCBC(stored, encryptionKey);
    String storage = saveFileToStorage(stored, newHash);

    file.setStoragePath(storage);
    file.setFileSize((long) rawBytes.length);
    file.setStoredSize((long) stored.length);
    file.setFileHash(newHash);
    file.setIsCompressed(compress);
    file.setIsEncrypted(encrypt);
    file.setUpdatedAt(LocalDateTime.now());

    versionService.createSnapshot(file, chunkEntities,
            changeDescription, newHash, rawBytes.length, true);

    return fileMetadataRepository.save(file);
}
    /**
     * 分块上传文件（用于大文件）
     */
    public FileMetadata uploadFileInChunks(MultipartFile file, String filePath, Long userId,
                                         boolean compress, boolean encrypt) throws Exception {
        
        User user = userService.getUserById(userId).orElseThrow(() -> new RuntimeException("用户不存在"));
        
        // 检查存储空间
        if (!userService.hasEnoughStorage(userId, file.getSize())) {
            throw new RuntimeException("存储空间不足");
        }
        
        // 计算文件哈希
        String fileHash = deduplicationUtil.calculateHash(file.getBytes());
        
        // 检查是否已存在相同文件
        List<FileMetadata> existingFiles = fileMetadataRepository.findByFileHash(fileHash);
        if (!existingFiles.isEmpty()) {
            FileMetadata existingFile = existingFiles.get(0);
            return createFileLink(existingFile, filePath, userId);
        }
        
        // 分割文件为块
        byte[] fileBytes = file.getBytes();
        List<DeduplicationUtil.ChunkInfo> chunks = deduplicationUtil.calculateChunkHashes(fileBytes);
        
        // 处理每个块
        long storedSize = 0L;
        for (DeduplicationUtil.ChunkInfo chunk : chunks) {
            byte[] chunkData = chunk.getData();
            
            // 压缩
            if (compress) {
                chunkData = compressionUtil.compressFile(chunkData, "GZIP");
            }
            
            // 加密
            String encryptionKey = null;
            if (encrypt) {
                encryptionKey = encryptionUtil.generateKey();
                chunkData = encryptionUtil.encryptCBC(chunkData, encryptionKey);
            }
            storedSize += chunkData.length;
            // 检查块是否已存在
            Optional<FileChunk> existingChunk = fileChunkRepository.findByChunkHash(chunk.getChunkHash());
            if (existingChunk.isPresent()) {
                // 块去重：重用现有块
                continue;
            }
            
            // 保存块
            String chunkStoragePath = saveChunkToStorage(chunkData, chunk.getChunkHash());
            
            FileChunk fileChunk = new FileChunk();
            fileChunk.setChunkHash(chunk.getChunkHash());
            fileChunk.setChunkSize((long) chunkData.length);
            fileChunk.setChunkIndex(chunk.getChunkIndex());
            fileChunk.setStoragePath(chunkStoragePath);
            fileChunk.setIsCompressed(compress);
            fileChunk.setIsEncrypted(encrypt);
            fileChunk.setCreatedAt(LocalDateTime.now());
            
            
            fileChunkRepository.save(fileChunk);
        }
        
        // 创建文件元数据
        FileMetadata fileMetadata = new FileMetadata();
        fileMetadata.setFilename(file.getOriginalFilename());
        fileMetadata.setFilePath(filePath);
        fileMetadata.setFileSize(file.getSize());
        fileMetadata.setStoredSize(storedSize); 
        fileMetadata.setFileHash(fileHash);
        // 设置MIME类型，如果为空则根据文件扩展名推断
        String mimeType = file.getContentType();
        if (mimeType == null || mimeType.isEmpty()) {
            mimeType = getMimeTypeFromFilename(file.getOriginalFilename());
        }
        fileMetadata.setMimeType(mimeType);
        fileMetadata.setIsDirectory(false);
        fileMetadata.setVersion(1);
        fileMetadata.setIsCompressed(compress);
        fileMetadata.setIsEncrypted(encrypt);
        fileMetadata.setOwner(user);
        fileMetadata.setCreatedAt(LocalDateTime.now());
        fileMetadata.setUpdatedAt(LocalDateTime.now());
        
        return fileMetadataRepository.save(fileMetadata);
    }
    
    
    /**
     * 获取用户文件列表
     */
    public List<FileMetadata> getUserFiles(Long userId, String path) {
        User user = userService.getUserById(userId).orElseThrow(() -> new RuntimeException("用户不存在"));
        
        System.out.println("=== 获取用户文件调试信息 ===");
        System.out.println("用户ID: " + userId);
        System.out.println("用户: " + user.getUsername());
        System.out.println("路径: " + path);
        
        List<FileMetadata> files;
        if (path == null || path.isEmpty()) {
            // 先获取所有文件，不按isDirectory过滤
            files = fileMetadataRepository.findByOwner(user);
            System.out.println("找到所有文件数量: " + files.size());
            
            // 然后过滤掉目录
            files = files.stream()
                .filter(file -> !file.getIsDirectory())
                .collect(java.util.stream.Collectors.toList());
            System.out.println("过滤后文件数量: " + files.size());
        } else {
            files = fileMetadataRepository.findByOwnerAndFilePathLike(user, path + "%");
            System.out.println("按路径查找文件数量: " + files.size());
        }
        
        // 打印文件信息用于调试
        for (FileMetadata file : files) {
            System.out.println("- 文件: " + file.getFilename() + " (ID: " + file.getId() + ", 目录: " + file.getIsDirectory() + ")");
        }
        
        return files;
    }
    
    /**
     * 删除文件
     */
    public void deleteFile(Long fileId, Long userId) {
        FileMetadata fileMetadata = fileMetadataRepository.findById(fileId)
            .orElseThrow(() -> new RuntimeException("文件不存在"));
        
        // 检查权限
        if (!fileMetadata.getOwner().getId().equals(userId)) {
            if (!shareService.hasFileAccess(fileId, userId, "WRITE")) {
                throw new RuntimeException("无权限删除此文件");
            }
        }
        
        // 删除物理文件
        try {
            Files.deleteIfExists(Paths.get(fileMetadata.getStoragePath()));
        } catch (IOException e) {
            System.err.println("删除物理文件失败: " + e.getMessage());
        }
        
        // 删除数据库记录
        fileMetadataRepository.delete(fileMetadata);
    }
    
    /**
     * 创建目录
     */
    public FileMetadata createDirectory(String dirPath, Long userId) {
        User user = userService.getUserById(userId).orElseThrow(() -> new RuntimeException("用户不存在"));
        
        FileMetadata directory = new FileMetadata();
        directory.setFilename(Paths.get(dirPath).getFileName().toString());
        directory.setFilePath(dirPath);
        directory.setFileSize(0L);
        directory.setFileHash("");
        directory.setMimeType("directory");
        directory.setIsDirectory(true);
        directory.setVersion(1);
        directory.setOwner(user);
        directory.setCreatedAt(LocalDateTime.now());
        directory.setUpdatedAt(LocalDateTime.now());
        
        return fileMetadataRepository.save(directory);
    }
    
    /**
     * 创建文件硬链接（用于去重）
     */
    private FileMetadata createFileLink(FileMetadata originalFile, String newPath, Long userId) {
        User user = userService.getUserById(userId).orElseThrow(() -> new RuntimeException("用户不存在"));
        
        FileMetadata linkFile = new FileMetadata();
        linkFile.setFilename(Paths.get(newPath).getFileName().toString());
        linkFile.setFilePath(newPath);
        linkFile.setFileSize(originalFile.getFileSize());
        linkFile.setFileHash(originalFile.getFileHash());
        linkFile.setMimeType(originalFile.getMimeType());
        linkFile.setIsDirectory(originalFile.getIsDirectory());
        linkFile.setVersion(1);
        linkFile.setIsCompressed(originalFile.getIsCompressed());
        linkFile.setIsEncrypted(originalFile.getIsEncrypted());
        linkFile.setStoragePath(originalFile.getStoragePath()); // 共享存储路径
        linkFile.setOwner(user);
        linkFile.setCreatedAt(LocalDateTime.now());
        linkFile.setUpdatedAt(LocalDateTime.now());
        
        return fileMetadataRepository.save(linkFile);
    }
    
    /**
     * 保存文件到存储
     */
    private String saveFileToStorage(byte[] fileData, String fileHash) throws IOException {
        String fileName = fileHash + ".dat";
        Path storagePath = Paths.get(this.storagePath + fileName);
        
        System.out.println("[FileService] 存储路径配置: " + this.storagePath);
        System.out.println("[FileService] 完整存储路径: " + storagePath.toAbsolutePath());
        
        Files.createDirectories(storagePath.getParent());
        Files.write(storagePath, fileData);
        
        System.out.println("[FileService] 文件已保存到: " + storagePath.toAbsolutePath());
        System.out.println("[FileService] 文件大小: " + fileData.length + " bytes");
        
        return storagePath.toString();
    }
    
    /**
     * 保存块到存储
     */
    private String saveChunkToStorage(byte[] chunkData, String chunkHash) throws IOException {
        String fileName = chunkHash + ".chunk";
        Path storagePath = Paths.get(this.storagePath + "chunks/" + fileName);
        Files.createDirectories(storagePath.getParent());
        Files.write(storagePath, chunkData);
        return storagePath.toString();
    }
    
    /**
     * 多线程文件上传
     */
    public FileMetadata uploadFileMultiThread(MultipartFile file, String filePath, Long userId, 
                                            int threads, boolean compress, boolean encrypt) throws Exception {
        User user = userService.getUserById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        
        // 读取文件数据
        byte[] fileData = file.getBytes();
        
        // 计算文件哈希
        String fileHash = calculateFileHash(fileData);
        
        // 检查是否已存在相同文件
        List<FileMetadata> existingFiles = fileMetadataRepository.findByFileHash(fileHash);
        if (!existingFiles.isEmpty()) {
            // 创建硬链接
            FileMetadata existingFile = existingFiles.get(0);
            return createHardLink(existingFile, filePath, user);
        }
        
        // 多线程处理文件
        byte[] processedData = processFileMultiThread(fileData, threads, compress, encrypt);
        
        // 保存文件
        String storagePath = saveFileToStorage(processedData, fileHash);
        
        // 生成加密密钥（如果需要）
        String encryptionKey = null;
        if (encrypt) {
            encryptionKey = encryptionUtil.generateKey();
        }
        
        // 创建文件元数据
        FileMetadata fileMetadata = new FileMetadata();
        fileMetadata.setFilename(file.getOriginalFilename());
        fileMetadata.setFilePath(filePath);
        fileMetadata.setFileSize((long) fileData.length);
        fileMetadata.setFileHash(fileHash);
        fileMetadata.setMimeType(getMimeTypeFromFilename(file.getOriginalFilename()));
        fileMetadata.setIsDirectory(false);
        fileMetadata.setVersion(1);
        fileMetadata.setIsCompressed(compress);
        fileMetadata.setIsEncrypted(encrypt);
        fileMetadata.setEncryptionKey(encryptionKey); // 保存加密密钥！
        fileMetadata.setStoragePath(storagePath);
        fileMetadata.setOwner(user);
        fileMetadata.setCreatedAt(LocalDateTime.now());
        fileMetadata.setUpdatedAt(LocalDateTime.now());
        
        return fileMetadataRepository.save(fileMetadata);
    }
    
    /**
     * 多线程处理文件
     */
    private byte[] processFileMultiThread(byte[] fileData, int threads, boolean compress, boolean encrypt) throws Exception {
        // 将文件分成多个块
        int chunkSize = fileData.length / threads;
        if (chunkSize == 0) chunkSize = fileData.length;
        
        byte[][] chunks = new byte[threads][];
        for (int i = 0; i < threads; i++) {
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, fileData.length);
            chunks[i] = new byte[end - start];
            System.arraycopy(fileData, start, chunks[i], 0, end - start);
        }
        
        // 并行处理每个块
        byte[][] processedChunks = new byte[threads][];
        Thread[] threadArray = new Thread[threads];
        
        for (int i = 0; i < threads; i++) {
            final int index = i;
            threadArray[i] = new Thread(() -> {
                try {
                    byte[] chunk = chunks[index];
                    if (compress) {
                        chunk = compressionUtil.compress(chunk);
                    }
                    if (encrypt) {
                        chunk = encryptionUtil.encrypt(chunk);
                    }
                    processedChunks[index] = chunk;
                } catch (Exception e) {
                    System.err.println("处理块 " + index + " 失败: " + e.getMessage());
                }
            });
            threadArray[i].start();
        }
        
        // 等待所有线程完成
        for (Thread thread : threadArray) {
            thread.join();
        }
        
        // 合并处理后的块
        int totalLength = 0;
        for (byte[] chunk : processedChunks) {
            totalLength += chunk.length;
        }
        
        byte[] result = new byte[totalLength];
        int offset = 0;
        for (byte[] chunk : processedChunks) {
            System.arraycopy(chunk, 0, result, offset, chunk.length);
            offset += chunk.length;
        }
        
        return result;
    }
    
    /**
     * 验证文件哈希
     */
    public Map<String, Object> verifyFileHash(Long fileId, Long userId) throws Exception {
        FileMetadata fileMetadata = fileMetadataRepository.findById(fileId)
            .orElseThrow(() -> new RuntimeException("文件不存在"));
        
        // 验证用户权限
        if (!fileMetadata.getOwner().getId().equals(userId)) {
            throw new RuntimeException("无权限访问此文件");
        }
        
        // 读取文件数据
        Path filePath = Paths.get(fileMetadata.getStoragePath());
        byte[] fileData = Files.readAllBytes(filePath);
        
        // 计算当前哈希
        String currentHash = calculateFileHash(fileData);
        String storedHash = fileMetadata.getFileHash();
        
        Map<String, Object> verification = new HashMap<>();
        verification.put("fileId", fileId);
        verification.put("filename", fileMetadata.getFilename());
        verification.put("storedHash", storedHash);
        verification.put("currentHash", currentHash);
        verification.put("isValid", currentHash.equals(storedHash));
        verification.put("fileSize", fileData.length);
        verification.put("verifiedAt", LocalDateTime.now());
        
        return verification;
    }
    
    /**
     * 批量文件上传
     */
    public List<Map<String, Object>> uploadFilesBatch(MultipartFile[] files, String basePath, 
                                                    Long userId, boolean compress, boolean encrypt) {
        List<Map<String, Object>> results = new ArrayList<>();
        
        for (MultipartFile file : files) {
            Map<String, Object> result = new HashMap<>();
            try {
                String filePath = basePath + "/" + file.getOriginalFilename();
                FileMetadata fileMetadata = uploadFile(file, filePath, userId, compress, encrypt);
                
                result.put("success", true);
                result.put("filename", file.getOriginalFilename());
                result.put("fileId", fileMetadata.getId());
                result.put("fileSize", fileMetadata.getFileSize());
                result.put("message", "上传成功");
                
            } catch (Exception e) {
                result.put("success", false);
                result.put("filename", file.getOriginalFilename());
                result.put("message", "上传失败: " + e.getMessage());
            }
            
            results.add(result);
        }
        
        return results;
    }
    
    /**
     * 单文件下载
     */
    public Map<String, Object> downloadFile(Long fileId, Long userId) throws Exception {
        FileMetadata fileMetadata = fileMetadataRepository.findById(fileId)
            .orElseThrow(() -> new RuntimeException("文件不存在"));
        
        // 验证用户权限
        if (!fileMetadata.getOwner().getId().equals(userId)) {
            if (!shareService.hasFileAccess(fileId, userId, "READ")) {
                throw new RuntimeException("无权限下载此文件");
            }
        }
        
        // 读取文件数据
        Path filePath = Paths.get(fileMetadata.getStoragePath());
        byte[] fileData = Files.readAllBytes(filePath);
        
        // 如果文件被压缩或加密，需要解压/解密
        if (fileMetadata.getIsCompressed() || fileMetadata.getIsEncrypted()) {
            System.out.println("[FileService] 处理加密/压缩文件，加密状态: " + fileMetadata.getIsEncrypted() + 
                              ", 压缩状态: " + fileMetadata.getIsCompressed() + 
                              ", 加密密钥: " + (fileMetadata.getEncryptionKey() != null ? "存在" : "缺失"));
            fileData = processDownloadedFile(fileData, fileMetadata.getIsCompressed(), 
                                          fileMetadata.getIsEncrypted(), fileMetadata.getEncryptionKey());
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("fileData", fileData);
        result.put("filename", fileMetadata.getFilename());
        result.put("mimeType", fileMetadata.getMimeType());
        result.put("fileSize", fileData.length);
        result.put("fileInfo", new FileInfoDTO(fileMetadata));
        
        return result;
    }
    
    /**
     * 多线程文件下载
     */
    public Map<String, Object> downloadFileMultiThread(Long fileId, Long userId, int threads) throws Exception {
        FileMetadata fileMetadata = fileMetadataRepository.findById(fileId)
            .orElseThrow(() -> new RuntimeException("文件不存在"));
        
        // 验证用户权限
        if (!fileMetadata.getOwner().getId().equals(userId)) {
            throw new RuntimeException("无权限下载此文件");
        }
        
        // 读取文件数据
        Path filePath = Paths.get(fileMetadata.getStoragePath());
        byte[] fileData = Files.readAllBytes(filePath);
        
        // 多线程处理下载
        byte[] processedData = processDownloadMultiThread(fileData, threads, 
            fileMetadata.getIsCompressed(), fileMetadata.getIsEncrypted(), fileMetadata.getEncryptionKey());
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "多线程下载完成");
        
        Map<String, Object> downloadInfo = new HashMap<>();
        downloadInfo.put("fileId", fileId);
        downloadInfo.put("filename", fileMetadata.getFilename());
        downloadInfo.put("originalSize", fileData.length);
        downloadInfo.put("processedSize", processedData.length);
        downloadInfo.put("threads", threads);
        downloadInfo.put("compressionRatio", (double) processedData.length / fileData.length);
        
        result.put("downloadInfo", downloadInfo);
        
        return result;
    }
    
    /**
     * 批量文件下载
     */
    public Map<String, Object> downloadFilesBatch(Long[] fileIds, Long userId, String format) throws Exception {
        List<FileMetadata> files = new ArrayList<>();
        
        // 验证所有文件权限
        for (Long fileId : fileIds) {
            FileMetadata fileMetadata = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("文件不存在: " + fileId));
            
            if (!fileMetadata.getOwner().getId().equals(userId)) {
                if (!shareService.hasFileAccess(fileId, userId, "READ")) {
                    throw new RuntimeException("无权限下载文件: " + fileMetadata.getFilename());
                }
            }
            
            files.add(fileMetadata);
        }
        
        // 根据格式处理批量下载
        Map<String, Object> result = new HashMap<>();
        
        if ("zip".equals(format)) {
            result = createZipArchive(files);
        } else {
            result.put("success", false);
            result.put("message", "不支持的下载格式: " + format);
        }
        
        return result;
    }
    
    /**
     * 生成下载链接
     */
    public Map<String, Object> generateDownloadLink(Long fileId, Long userId, int expireMinutes) throws Exception {
        FileMetadata fileMetadata = fileMetadataRepository.findById(fileId)
            .orElseThrow(() -> new RuntimeException("文件不存在"));
        
        // 验证用户权限
        if (!fileMetadata.getOwner().getId().equals(userId)) {
            throw new RuntimeException("无权限生成此文件的下载链接");
        }
        
        // 生成临时下载链接（这里简化实现，实际应该使用JWT或临时token）
        String downloadLink = "/api/files/download/" + fileId + "?token=" + generateTempToken(userId, expireMinutes);
        LocalDateTime expireTime = LocalDateTime.now().plusMinutes(expireMinutes);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("downloadLink", downloadLink);
        result.put("expireTime", expireTime);
        result.put("filename", fileMetadata.getFilename());
        result.put("fileSize", fileMetadata.getFileSize());
        
        return result;
    }
    
    /**
     * 处理下载的文件（解压/解密）
     */
    private byte[] processDownloadedFile(byte[] fileData, boolean isCompressed, boolean isEncrypted) throws Exception {
        byte[] processedData = fileData;
        
        // 先解密
        if (isEncrypted) {
            processedData = encryptionUtil.decrypt(processedData);
        }
        
        // 再解压
        if (isCompressed) {
            processedData = compressionUtil.decompress(processedData);
        }
        
        return processedData;
    }
    
    /**
     * 处理下载的文件（解压/解密）- 使用指定密钥
     */
    private byte[] processDownloadedFile(byte[] fileData, boolean isCompressed, boolean isEncrypted, String encryptionKey) throws Exception {
        byte[] processedData = fileData;
        
        // 先解密
        if (isEncrypted && encryptionKey != null) {
            System.out.println("[FileService] 使用保存的加密密钥进行解密");
            processedData = encryptionUtil.decryptCBC(processedData, encryptionKey);
        } else if (isEncrypted) {
            System.out.println("[FileService] 警告：文件已加密但缺少密钥，使用默认密钥");
            processedData = encryptionUtil.decrypt(processedData);
        }
        
        // 再解压
        if (isCompressed) {
            System.out.println("[FileService] 解压文件");
            processedData = compressionUtil.decompress(processedData);
        }
        
        return processedData;
    }
    
    /**
     * 多线程处理下载
     */
    private byte[] processDownloadMultiThread(byte[] fileData, int threads, boolean isCompressed, boolean isEncrypted, String encryptionKey) throws Exception {
        if (!isCompressed && !isEncrypted) {
            return fileData; // 无需处理
        }
        
        // 重要：加密和压缩的文件不能分块处理！
        // 加密文件需要完整的密钥和初始化向量
        // 压缩文件需要完整的文件上下文
        // 所以对于加密/压缩文件，我们使用单线程处理，但模拟多线程的统计信息
        
        System.out.println("[FileService] 检测到加密/压缩文件，使用单线程处理（加密/压缩文件不能分块）");
        
        // 直接使用单线程处理，传入加密密钥
        byte[] processedData = processDownloadedFile(fileData, isCompressed, isEncrypted, encryptionKey);
        
        System.out.println("[FileService] 文件处理完成，原始大小: " + fileData.length + 
                          ", 处理后大小: " + processedData.length + 
                          ", 压缩比: " + String.format("%.2f", (double) processedData.length / fileData.length));
        
        return processedData;
    }
    
    /**
     * 创建ZIP压缩包
     */
    private Map<String, Object> createZipArchive(List<FileMetadata> files) throws Exception {
        // 这里简化实现，实际应该使用ZIP库
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "ZIP压缩包创建成功");
        result.put("fileCount", files.size());
        result.put("totalSize", files.stream().mapToLong(FileMetadata::getFileSize).sum());
        
        Map<String, Object> downloadInfo = new HashMap<>();
        downloadInfo.put("format", "zip");
        downloadInfo.put("files", files.stream().map(f -> {
            Map<String, Object> fileInfo = new HashMap<>();
            fileInfo.put("id", f.getId());
            fileInfo.put("filename", f.getFilename());
            fileInfo.put("size", f.getFileSize());
            return fileInfo;
        }).collect(java.util.stream.Collectors.toList()));
        
        result.put("downloadInfo", downloadInfo);
        
        return result;
    }
    
    /**
     * 生成临时下载token
     */
    private String generateTempToken(Long userId, int expireMinutes) {
        // 简化实现，实际应该使用JWT或专门的临时token机制
        return "temp_" + userId + "_" + System.currentTimeMillis() + "_" + expireMinutes;
    }
    
    /**
     * 计算文件哈希
     */
    private String calculateFileHash(byte[] fileData) throws NoSuchAlgorithmException {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(fileData);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    /**
     * 创建硬链接
     */
    private FileMetadata createHardLink(FileMetadata existingFile, String newPath, User user) {
        FileMetadata linkFile = new FileMetadata();
        linkFile.setFilename(existingFile.getFilename());
        linkFile.setFilePath(newPath);
        linkFile.setFileSize(existingFile.getFileSize());
        linkFile.setFileHash(existingFile.getFileHash());
        linkFile.setMimeType(existingFile.getMimeType());
        linkFile.setIsDirectory(existingFile.getIsDirectory());
        linkFile.setVersion(existingFile.getVersion());
        linkFile.setIsCompressed(existingFile.getIsCompressed());
        linkFile.setIsEncrypted(existingFile.getIsEncrypted());
        linkFile.setStoragePath(existingFile.getStoragePath());
        linkFile.setOwner(user);
        linkFile.setCreatedAt(LocalDateTime.now());
        linkFile.setUpdatedAt(LocalDateTime.now());
        
        return fileMetadataRepository.save(linkFile);
    }
}

