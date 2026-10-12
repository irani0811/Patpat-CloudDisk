package model;

import java.time.LocalDateTime;

public class FileShareDTO {
    private Long shareId;
    private Long fileId;
    private String filename;
    private String shareFrom;
    private String shareTo;
    private String permission;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private Boolean active;
    

    public static FileShareDTO ownerView(FileShare share) {
        return baseFrom(share);
    }

    public static FileShareDTO recipientView(FileShare share) {
        return baseFrom(share);
    }

    private static FileShareDTO baseFrom(FileShare share) {
        FileShareDTO dto = new FileShareDTO();
        dto.shareId = share.getId();
        dto.fileId = share.getFile() != null ? share.getFile().getId() : null;
        dto.filename = share.getFile() != null ? share.getFile().getFilename() : null;
        dto.shareFrom = share.getUser() != null ? share.getUser().getUsername() : null;
        dto.shareTo = share.getSharedWithUser() != null ? share.getSharedWithUser().getUsername() : null;
        dto.permission = share.getPermission();
        dto.createdAt = share.getCreatedAt();
        dto.expiresAt = share.getExpiresAt();
        dto.active = share.getIsActive();
        return dto;
    }

    public Long getShareId() { return shareId; }
    public Long getFileId() { return fileId; }
    public String getFilename() { return filename; }
    public String getShareFrom() { return shareFrom; }
    public String getShareTo() { return shareTo; }
    public String getPermission() { return permission; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public Boolean getActive() { return active; }
}