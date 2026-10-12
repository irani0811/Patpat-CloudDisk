package dto;

import java.util.List;

public class ChunkDedupInitResponse {
    private boolean fileAlreadyExists;
    private Long existingFileId;
    private int chunkSize;
    private int totalChunks;
    private List<Integer> needUploadIndices;
    private List<String> needUploadHashes;

    public boolean isFileAlreadyExists() { return fileAlreadyExists; }
    public void setFileAlreadyExists(boolean fileAlreadyExists) { this.fileAlreadyExists = fileAlreadyExists; }
    public Long getExistingFileId() { return existingFileId; }
    public void setExistingFileId(Long existingFileId) { this.existingFileId = existingFileId; }
    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
    public int getTotalChunks() { return totalChunks; }
    public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }
    public List<Integer> getNeedUploadIndices() { return needUploadIndices; }
    public void setNeedUploadIndices(List<Integer> needUploadIndices) { this.needUploadIndices = needUploadIndices; }
    public List<String> getNeedUploadHashes() { return needUploadHashes; }
    public void setNeedUploadHashes(List<String> needUploadHashes) { this.needUploadHashes = needUploadHashes; }
}
