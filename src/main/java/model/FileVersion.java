package model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 文件版本实体类 - 用于版本控制
 */
@Entity
@Table(name = "file_versions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileVersion {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Integer versionNumber;
    
    @Column(nullable = false)
    private String fileHash;
    
    @Column(nullable = false)
    private Long fileSize;

    @Column(name = "version_storage_path")
    private String versionStoragePath;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "change_description")
    private String changeDescription;
    
    @Column(name = "is_current")
    private Boolean isCurrent = false;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id")
    @JsonIgnore
    private FileMetadata file;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<FileVersionChunk> versionChunks = new ArrayList<>();

    // 手动添加getter/setter方法
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Integer getVersionNumber() { return versionNumber; }
    public void setVersionNumber(Integer versionNumber) { this.versionNumber = versionNumber; }
    
    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }
    
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getVersionStoragePath() { return versionStoragePath; }
    public void setVersionStoragePath(String versionStoragePath) { this.versionStoragePath = versionStoragePath; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public String getChangeDescription() { return changeDescription; }
    public void setChangeDescription(String changeDescription) { this.changeDescription = changeDescription; }
    
    public Boolean getIsCurrent() { return isCurrent; }
    public void setIsCurrent(Boolean isCurrent) { this.isCurrent = isCurrent; }
    
    public FileMetadata getFile() { return file; }
    public void setFile(FileMetadata file) { this.file = file; }
}

