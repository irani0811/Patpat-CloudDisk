package dto;

import java.util.List;

public class FinalizeAssembleRequest {
    private String filename;
    private String path;
    private String fileHash;
    private List<String> chunkHashes;

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }
    public List<String> getChunkHashes() { return chunkHashes; }
    public void setChunkHashes(List<String> chunkHashes) { this.chunkHashes = chunkHashes; }
}
