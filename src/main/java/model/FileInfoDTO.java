package model;

import java.time.LocalDateTime;

/**
 * 文件信息DTO，用于API响应，避免循环引用
 */
public class FileInfoDTO {
    
    private Long id;
    private String filename;
    private String filePath;
    private Long fileSize;
    private Long storedSize;
    private String fileHash;
    private String mimeType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isDirectory;
    private Long parentId;
    private Integer version;
    private Boolean isCompressed;
    private Boolean isEncrypted;
    private String storagePath;
    private Long ownerId; // 只返回用户ID，不返回整个User对象
    
    // 构造函数
    public FileInfoDTO() {}
    
    public FileInfoDTO(FileMetadata fileMetadata) {
        this.id = fileMetadata.getId();
        this.filename = fileMetadata.getFilename();
        this.filePath = fileMetadata.getFilePath();
        this.fileSize = fileMetadata.getFileSize();
        this.storedSize = fileMetadata.getStoredSize(); 
        this.fileHash = fileMetadata.getFileHash();
        this.mimeType = fileMetadata.getMimeType();
        this.createdAt = fileMetadata.getCreatedAt();
        this.updatedAt = fileMetadata.getUpdatedAt();
        this.isDirectory = fileMetadata.getIsDirectory();
        this.parentId = fileMetadata.getParentId();
        this.version = fileMetadata.getVersion();
        this.isCompressed = fileMetadata.getIsCompressed();
        this.isEncrypted = fileMetadata.getIsEncrypted();
        this.storagePath = fileMetadata.getStoragePath();
        this.ownerId = fileMetadata.getOwner() != null ? fileMetadata.getOwner().getId() : null;
    }
    
    // Getter和Setter方法
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    
    public Long getStoredSize() { return storedSize; }
    public void setStoredSize(Long storedSize) { this.storedSize = storedSize; }

    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }
    
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public Boolean getIsDirectory() { return isDirectory; }
    public void setIsDirectory(Boolean isDirectory) { this.isDirectory = isDirectory; }
    
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    
    public Boolean getIsCompressed() { return isCompressed; }
    public void setIsCompressed(Boolean isCompressed) { this.isCompressed = isCompressed; }
    
    public Boolean getIsEncrypted() { return isEncrypted; }
    public void setIsEncrypted(Boolean isEncrypted) { this.isEncrypted = isEncrypted; }
    
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
}
