package dto;

import java.util.List;

public class DeltaApplyRequest {
    private Integer baseVersion;
    private int chunkSize;
    private long targetFileSize;
    private String targetFileHashSha256;
    private List<DeltaBlock> blocks;
    private List<DeltaOp> ops;

    public Integer getBaseVersion() { return baseVersion; }
    public void setBaseVersion(Integer baseVersion) { this.baseVersion = baseVersion; }
    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
    public long getTargetFileSize() { return targetFileSize; }
    public void setTargetFileSize(long targetFileSize) { this.targetFileSize = targetFileSize; }
    public String getTargetFileHashSha256() { return targetFileHashSha256; }
    public void setTargetFileHashSha256(String targetFileHashSha256) { this.targetFileHashSha256 = targetFileHashSha256; }
    public List<DeltaBlock> getBlocks() { return blocks; }
    public void setBlocks(List<DeltaBlock> blocks) { this.blocks = blocks; }
    public List<DeltaOp> getOps() { return ops; }
    public void setOps(List<DeltaOp> ops) { this.ops = ops; }
}
