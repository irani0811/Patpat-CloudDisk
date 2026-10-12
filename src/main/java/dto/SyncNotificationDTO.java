package dto;

import model.SyncEvent;

/**
 * 精简的同步通知数据传输对象。
 */
public class SyncNotificationDTO {
    private final Long id;
    private final String eventType;
    private final String filePath;
    private final String oldPath;
    private final Long fileSize;
    private final String fileHash;
    private final String clientId;
    private final java.time.LocalDateTime eventTime;

    public SyncNotificationDTO(Long id,
                               String eventType,
                               String filePath,
                               String oldPath,
                               Long fileSize,
                               String fileHash,
                               String clientId,
                               java.time.LocalDateTime eventTime) {
        this.id = id;
        this.eventType = eventType;
        this.filePath = filePath;
        this.oldPath = oldPath;
        this.fileSize = fileSize;
        this.fileHash = fileHash;
        this.clientId = clientId;
        this.eventTime = eventTime;
    }

    public static SyncNotificationDTO from(SyncEvent event) {
        return new SyncNotificationDTO(
                event.getId(),
                event.getEventType(),
                event.getFilePath(),
                event.getOldPath(),
                event.getFileSize(),
                event.getFileHash(),
                event.getClientId(),
                event.getEventTime()
        );
    }

    public Long getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getOldPath() {
        return oldPath;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public String getFileHash() {
        return fileHash;
    }

    public String getClientId() {
        return clientId;
    }

    public java.time.LocalDateTime getEventTime() {
        return eventTime;
    }
}
