package dto;

import java.util.List;

public class ChunkDedupInitRequest {
    private String filename;
    private String path;
    private long fileSize;
    private int chunkSize;
    private String fileHash;
    private List<String> chunkHashes;

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }
    public List<String> getChunkHashes() { return chunkHashes; }
    public void setChunkHashes(List<String> chunkHashes) { this.chunkHashes = chunkHashes; }
}
