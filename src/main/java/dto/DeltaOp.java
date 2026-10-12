package dto;

public class DeltaOp {
    public enum Type { COPY, DATA }

    private Type type;
    private long srcOffset; // for COPY: offset in base file
    private int length;     // for COPY/DATA length
    private String dataBase64; // for DATA

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public long getSrcOffset() { return srcOffset; }
    public void setSrcOffset(long srcOffset) { this.srcOffset = srcOffset; }

    public int getLength() { return length; }
    public void setLength(int length) { this.length = length; }

    public String getDataBase64() { return dataBase64; }
    public void setDataBase64(String dataBase64) { this.dataBase64 = dataBase64; }
}
