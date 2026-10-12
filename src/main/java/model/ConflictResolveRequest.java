package model;

public class ConflictResolveRequest {
    private Long fileId;
    private String base64Content;
    private String strategy;   // duplicate / overwrite
    private String note;
    private Boolean compress;
    private Boolean encrypt;

    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }

    public String getBase64Content() { return base64Content; }
    public void setBase64Content(String base64Content) { this.base64Content = base64Content; }

    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Boolean getCompress() { return compress; }
    public void setCompress(Boolean compress) { this.compress = compress; }

    public Boolean getEncrypt() { return encrypt; }
    public void setEncrypt(Boolean encrypt) { this.encrypt = encrypt; }
}