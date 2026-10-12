package dto;

import java.util.List;

public class ChunkIndexResponse {
    private Long fileId;
    private Integer version;
    private long fileSize;
    private String fileHashSha256;
    private int chunkSize;
    private List<ChunkDescriptor> chunks;

    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public String getFileHashSha256() { return fileHashSha256; }
    public void setFileHashSha256(String fileHashSha256) { this.fileHashSha256 = fileHashSha256; }
    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
    public List<ChunkDescriptor> getChunks() { return chunks; }
    public void setChunks(List<ChunkDescriptor> chunks) { this.chunks = chunks; }
}
