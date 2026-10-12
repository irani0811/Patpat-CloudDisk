package dto;

public class ChunkDescriptor {
    private int index;
    private long offset;
    private int size;
    private String sha256;
    private int weak32; // rsync-style weak checksum (rolling-compatible)

    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }
    public long getOffset() { return offset; }
    public void setOffset(long offset) { this.offset = offset; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public int getWeak32() { return weak32; }
    public void setWeak32(int weak32) { this.weak32 = weak32; }
}
