package dto;

public class DeltaApplyResponse {
    private Long fileId;
    private Integer newVersion;
    private long fileSize;
    private String fileHashMd5;
    private String fileHashSha256;
    private String storagePath;

    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }
    public Integer getNewVersion() { return newVersion; }
    public void setNewVersion(Integer newVersion) { this.newVersion = newVersion; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public String getFileHashMd5() { return fileHashMd5; }
    public void setFileHashMd5(String fileHashMd5) { this.fileHashMd5 = fileHashMd5; }
    public String getFileHashSha256() { return fileHashSha256; }
    public void setFileHashSha256(String fileHashSha256) { this.fileHashSha256 = fileHashSha256; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
}
