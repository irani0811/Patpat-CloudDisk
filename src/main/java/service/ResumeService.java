package service;

import model.User;
import model.FileMetadata;
import repository.FileMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 断点续传服务类 - 真正的断点续传实现
 */
@Service
public class ResumeService {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private FileMetadataRepository fileMetadataRepository;
    
    // 存储上传进度信息
    private final Map<String, UploadProgress> uploadProgressMap = new ConcurrentHashMap<>();
    
    @Value("${file.storage.path:./storage/}")
    private String storagePath;
    
    private static final int CHUNK_SIZE = 1024 * 1024; // 1MB per chunk
    
    /**
     * 开始断点续传上传
     */
    public String startResumeUpload(String fileName, Long fileSize, Long userId, String filePath) {
        String uploadId = generateUploadId(fileName, userId);
        
        UploadProgress progress = new UploadProgress();
        progress.setUploadId(uploadId);
        progress.setFileName(fileName);
        progress.setFileSize(fileSize);
        progress.setUserId(userId);
        progress.setFilePath(filePath);
        progress.setUploadedBytes(0L);
        progress.setChunks(new HashMap<>());
        progress.setStartTime(System.currentTimeMillis());
        
        // 计算需要的块数
        int totalChunks = (int) Math.ceil((double) fileSize / CHUNK_SIZE);
        progress.setTotalChunks(totalChunks);
        
        uploadProgressMap.put(uploadId, progress);
        
        return uploadId;
    }
    
    /**
     * 上传文件块 - 真正的分块存储
     */
    public boolean uploadChunk(String uploadId, int chunkIndex, byte[] chunkData) {
        UploadProgress progress = uploadProgressMap.get(uploadId);
        if (progress == null) {
            return false;
        }
        
        try {
            // 保存块到磁盘
            String chunkPath = saveChunkToDisk(uploadId, chunkIndex, chunkData);
            
            // 更新进度
            progress.getChunks().put(chunkIndex, chunkPath);
            progress.setUploadedBytes(progress.getUploadedBytes() + chunkData.length);
            
            return true;
            
        } catch (Exception e) {
            System.err.println("保存文件块失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 完成上传 - 合并所有块并创建文件元数据
     */
    public boolean completeUpload(String uploadId) {
        UploadProgress progress = uploadProgressMap.get(uploadId);
        if (progress == null) {
            return false;
        }
        
        try {
            // 合并所有块（内部会计算哈希并重命名文件）
            String completeFilePath = mergeChunksFromDisk(progress);
            
            // 从文件路径中提取哈希值
            String fileHash = extractHashFromPath(completeFilePath);
            
            // 创建文件元数据
            FileMetadata fileMetadata = new FileMetadata();
            fileMetadata.setFilename(progress.getFileName());
            fileMetadata.setFilePath(progress.getFilePath());
            fileMetadata.setFileSize(progress.getFileSize());
            fileMetadata.setFileHash(fileHash);
            fileMetadata.setMimeType(getMimeTypeFromFilename(progress.getFileName()));
            fileMetadata.setIsDirectory(false);
            fileMetadata.setVersion(1);
            fileMetadata.setIsCompressed(false);
            fileMetadata.setIsEncrypted(false);
            fileMetadata.setStoragePath(completeFilePath);
            fileMetadata.setOwner(userService.getUserById(progress.getUserId()).orElse(null));
            
            // 保存到数据库
            fileMetadataRepository.save(fileMetadata);
            
            // 清理临时文件块
            cleanupChunks(progress);
            
            // 清理进度信息
            uploadProgressMap.remove(uploadId);
            
            return true;
            
        } catch (Exception e) {
            System.err.println("完成上传失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取上传进度
     */
    public Map<String, Object> getUploadProgress(String uploadId) {
        UploadProgress progress = uploadProgressMap.get(uploadId);
        if (progress == null) {
            return null;
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("uploadId", uploadId);
        result.put("fileName", progress.getFileName());
        result.put("fileSize", progress.getFileSize());
        result.put("uploadedBytes", progress.getUploadedBytes());
        result.put("totalChunks", progress.getTotalChunks());
        result.put("progress", (double) progress.getUploadedBytes() / progress.getFileSize());
        
        return result;
    }
    
    /**
     * 取消上传
     */
    public boolean cancelUpload(String uploadId) {
        UploadProgress progress = uploadProgressMap.get(uploadId);
        if (progress == null) {
            return false;
        }
        
        try {
            // 清理临时文件块
            cleanupChunks(progress);
            
            // 清理进度信息
            uploadProgressMap.remove(uploadId);
            
            return true;
            
        } catch (Exception e) {
            System.err.println("取消上传失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 恢复上传 - 检查已上传的块
     */
    public boolean resumeUpload(String uploadId) {
        UploadProgress progress = uploadProgressMap.get(uploadId);
        if (progress == null) {
            return false;
        }
        
        try {
            // 检查哪些块已经上传
            Map<Integer, String> uploadedChunks = new HashMap<>();
            for (int i = 0; i < progress.getTotalChunks(); i++) {
                String chunkPath = getChunkPath(uploadId, i);
                if (Files.exists(Paths.get(chunkPath))) {
                    uploadedChunks.put(i, chunkPath);
                }
            }
            
            // 更新进度信息
            progress.setChunks(uploadedChunks);
            progress.setUploadedBytes((long) uploadedChunks.size() * CHUNK_SIZE);
            
            return true;
            
        } catch (Exception e) {
            System.err.println("恢复上传失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 保存块到磁盘
     */
    private String saveChunkToDisk(String uploadId, int chunkIndex, byte[] chunkData) throws IOException {
        String chunkPath = getChunkPath(uploadId, chunkIndex);
        Path path = Paths.get(chunkPath);
        Files.createDirectories(path.getParent());
        Files.write(path, chunkData);
        return chunkPath;
    }
    
    /**
     * 获取块文件路径
     */
    private String getChunkPath(String uploadId, int chunkIndex) {
        return storagePath + "chunks/" + uploadId + "_chunk_" + chunkIndex + ".dat";
    }
    
    /**
     * 从磁盘合并所有块
     */
    private String mergeChunksFromDisk(UploadProgress progress) throws IOException {
        // 先合并到临时文件
        String tempFilePath = "src/main/java/cloud/temp_" + progress.getFileName();
        Path tempPath = Paths.get(tempFilePath);
        Files.createDirectories(tempPath.getParent());
        
        try (FileOutputStream fos = new FileOutputStream(tempPath.toFile())) {
            for (int i = 0; i < progress.getTotalChunks(); i++) {
                String chunkPath = progress.getChunks().get(i);
                if (chunkPath != null && Files.exists(Paths.get(chunkPath))) {
                    Files.copy(Paths.get(chunkPath), fos);
                }
            }
        }
        
        // 计算文件哈希
        String fileHash;
        try {
            fileHash = calculateFileHash(tempFilePath);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("计算文件哈希失败", e);
        }
        
        // 使用哈希文件名存储最终文件
        String finalFilePath = storagePath + fileHash + ".dat";
        Path finalPath = Paths.get(finalFilePath);
        Files.move(tempPath, finalPath);
        
        // 清理临时文件
        Files.deleteIfExists(tempPath);
        
        return finalFilePath;
    }
    
    /**
     * 从文件路径中提取哈希值
     */
    private String extractHashFromPath(String filePath) {
        String fileName = Paths.get(filePath).getFileName().toString();
        return fileName.replace(".dat", "");
    }
    
    /**
     * 计算文件哈希
     */
    private String calculateFileHash(String filePath) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (FileInputStream fis = new FileInputStream(filePath)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
        }
        
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    /**
     * 根据文件名推断MIME类型
     */
    private String getMimeTypeFromFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "application/octet-stream";
        }
        
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        switch (extension) {
            case "txt": return "text/plain";
            case "pdf": return "application/pdf";
            case "jpg":
            case "jpeg": return "image/jpeg";
            case "png": return "image/png";
            case "gif": return "image/gif";
            case "mp4": return "video/mp4";
            case "mp3": return "audio/mpeg";
            case "zip": return "application/zip";
            default: return "application/octet-stream";
        }
    }
    
    /**
     * 清理临时文件块
     */
    private void cleanupChunks(UploadProgress progress) {
        for (String chunkPath : progress.getChunks().values()) {
            try {
                Files.deleteIfExists(Paths.get(chunkPath));
            } catch (IOException e) {
                System.err.println("清理文件块失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 生成上传ID
     */
    private String generateUploadId(String fileName, Long userId) {
        return userId + "_" + fileName + "_" + System.currentTimeMillis();
    }
    
    /**
     * 上传进度内部类
     */
    public static class UploadProgress {
        private String uploadId;
        private String fileName;
        private Long fileSize;
        private Long userId;
        private String filePath;
        private Long uploadedBytes;
        private Map<Integer, String> chunks; // 存储块文件路径
        private Long startTime;
        private int totalChunks;
        
        // Getters and Setters
        public String getUploadId() { return uploadId; }
        public void setUploadId(String uploadId) { this.uploadId = uploadId; }
        
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        
        public Long getFileSize() { return fileSize; }
        public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
        
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        
        public Long getUploadedBytes() { return uploadedBytes; }
        public void setUploadedBytes(Long uploadedBytes) { this.uploadedBytes = uploadedBytes; }
        
        public Map<Integer, String> getChunks() { return chunks; }
        public void setChunks(Map<Integer, String> chunks) { this.chunks = chunks; }
        
        public Long getStartTime() { return startTime; }
        public void setStartTime(Long startTime) { this.startTime = startTime; }
        
        public int getTotalChunks() { return totalChunks; }
        public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }
    }
}