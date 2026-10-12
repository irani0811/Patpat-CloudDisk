package dto;

public class DeltaBlock {
    private int index;
    private long offset;
    private int size;
    private String sha256;
    private String dataBase64;

    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }
    public long getOffset() { return offset; }
    public void setOffset(long offset) { this.offset = offset; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public String getDataBase64() { return dataBase64; }
    public void setDataBase64(String dataBase64) { this.dataBase64 = dataBase64; }
}
