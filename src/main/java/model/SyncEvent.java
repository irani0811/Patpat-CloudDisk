package model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 同步事件实体类 - 用于记录文件同步事件
 */
@Entity
@Table(name = "sync_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String eventType; // CREATE, UPDATE, DELETE, MOVE
    
    @Column(nullable = false)
    private String filePath;
    
    @Column(name = "old_path")
    private String oldPath; // For MOVE events
    
    @Column(name = "file_hash")
    private String fileHash;
    
    @Column(name = "file_size")
    private Long fileSize;
    
    @Column(name = "event_time")
    private LocalDateTime eventTime;
    
    @Column(name = "is_processed")
    private Boolean isProcessed = false;
    
    @Column(name = "client_id")
    private String clientId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    
    @PrePersist
    protected void onCreate() {
        eventTime = LocalDateTime.now();
    }

    // 手动添加getter/setter方法
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    
    public String getOldPath() { return oldPath; }
    public void setOldPath(String oldPath) { this.oldPath = oldPath; }
    
    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }
    
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    
    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }
    
    public Boolean getIsProcessed() { return isProcessed; }
    public void setIsProcessed(Boolean isProcessed) { this.isProcessed = isProcessed; }
    
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}

