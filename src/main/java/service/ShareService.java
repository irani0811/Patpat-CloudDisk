package service;

import model.FileMetadata;
import model.FileShare;
import model.User;
import repository.FileMetadataRepository;
import repository.FileShareRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

/**
 * 文件共享服务类
 */
@Service
public class ShareService {
    
    @Autowired
    private FileShareRepository fileShareRepository;
    
    @Autowired
    private FileMetadataRepository fileMetadataRepository;
    
    @Autowired
    private UserService userService;
    
    /**
     * 创建文件共享
     */
    public FileShare createShare(Long fileId, Long userId, String shareWithUsername, String permission, Integer expireDays) {
        // 检查文件是否存在且用户有权限
        FileMetadata file = fileMetadataRepository.findById(fileId)
            .orElseThrow(() -> new RuntimeException("文件不存在"));
        
        if (!file.getOwner().getId().equals(userId)) {
            throw new RuntimeException("无权限共享此文件");
        }
        
        // 获取被共享用户
        if (shareWithUsername == null || shareWithUsername.isBlank()) {
            throw new RuntimeException("共享对象不能为空");
        }
        if (file.getOwner().getUsername().equals(shareWithUsername)) {
            throw new RuntimeException("无法将文件共享给自己");
        }
        User sharedWith = userService.getUserByUsername(shareWithUsername)
            .orElseThrow(() -> new RuntimeException("被共享用户不存在"));
        
        // 生成共享token
        String shareToken = generateShareToken();
        
        // 计算过期时间
        LocalDateTime expiresAt = null;
        if (expireDays != null && expireDays > 0) {
            expiresAt = LocalDateTime.now().plusDays(expireDays);
        }
        
        // 创建共享记录
        FileShare fileShare = new FileShare();
        fileShare.setShareToken(shareToken);
        fileShare.setPermission(permission);
        fileShare.setExpiresAt(expiresAt);
        fileShare.setIsActive(true);
        fileShare.setCreatedAt(LocalDateTime.now());
        fileShare.setAccessCount(0);
        fileShare.setFile(file);
        fileShare.setUser(userService.getUserById(userId).orElse(null));
        fileShare.setSharedWithUser(sharedWith);
        
        return fileShareRepository.save(fileShare);
    }
    
    /**
     * 通过共享token访问文件
     */
    public FileMetadata accessSharedFile(String shareToken) {
        Optional<FileShare> shareOpt = fileShareRepository.findActiveShareByToken(shareToken);
        
        if (shareOpt.isEmpty()) {
            throw new RuntimeException("共享链接无效或已过期");
        }
        
        FileShare share = shareOpt.get();
        
        // 增加访问次数
        share.setAccessCount(share.getAccessCount() + 1);
        fileShareRepository.save(share);
        
        return share.getFile();
    }
    
    /**
     * 获取用户的共享文件列表
     */
    public List<FileShare> getUserSharedFiles(Long userId) {
        return fileShareRepository.findByUserId(userId);
    }
    
    public List<FileShare> getSharedWithMe(Long userId) {
        return fileShareRepository.findBySharedWithUserId(userId);
    }
    
    /**
     * 获取文件的共享信息
     */
    public List<FileShare> getFileShares(Long fileId) {
        return fileShareRepository.findByFileId(fileId);
    }
    
    /**
     * 更新共享权限
     */
    public FileShare updateSharePermission(Long shareId, String permission, Integer expireDays) {
        FileShare share = fileShareRepository.findById(shareId)
            .orElseThrow(() -> new RuntimeException("共享记录不存在"));
        
        share.setPermission(permission);
        
        if (expireDays != null && expireDays > 0) {
            share.setExpiresAt(LocalDateTime.now().plusDays(expireDays));
        } else {
            share.setExpiresAt(null);
        }
        
        return fileShareRepository.save(share);
    }
    
    /**
     * 取消共享
     */
    public boolean cancelShare(Long shareId, Long userId) {
        Optional<FileShare> shareOpt = fileShareRepository.findById(shareId);
        
        if (shareOpt.isEmpty()) {
            return false;
        }
        
        FileShare share = shareOpt.get();
        
        // 检查权限
        if (!share.getUser().getId().equals(userId)) {
            throw new RuntimeException("无权限取消此共享");
        }
        
        share.setIsActive(false);
        fileShareRepository.save(share);
        
        return true;
    }
    
    /**
     * 检查用户对文件的访问权限
     */
    public boolean hasFileAccess(Long fileId, Long userId, String requiredPermission) {
        FileMetadata file = fileMetadataRepository.findById(fileId)
            .orElseThrow(() -> new RuntimeException("文件不存在"));
        
        // 文件所有者有所有权限
        if (file.getOwner().getId().equals(userId)) {
            return true;
        }
        
        // 检查共享权限
        List<FileShare> shares = fileShareRepository.findByFileId(fileId);
        for (FileShare share : shares) {
            if (!share.getIsActive()) {
                continue;
            }
            if (share.getExpiresAt() != null && share.getExpiresAt().isBefore(LocalDateTime.now())) {
                continue;
            }
            if (share.getSharedWithUser() == null || !share.getSharedWithUser().getId().equals(userId)) {
                continue;
            }
            String level = share.getPermission();
            if (requiredPermission.equals("READ")) {
                if ("READ".equals(level) || "WRITE".equals(level) || "ADMIN".equals(level)) {
                    return true;
                }
            } else if (requiredPermission.equals("WRITE")) {
                if ("WRITE".equals(level) || "ADMIN".equals(level)) {
                    return true;
                }
            } else if (requiredPermission.equals("ADMIN")) {
                if ("ADMIN".equals(level)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 生成共享token
     */
    private String generateShareToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * 获取共享统计信息
     */
    public Map<String, Object> getShareStatistics(Long shareId) {
        Optional<FileShare> shareOpt = fileShareRepository.findById(shareId);
        
        if (shareOpt.isEmpty()) {
            return null;
        }
        
        FileShare share = shareOpt.get();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("shareId", share.getId());
        stats.put("fileName", share.getFile().getFilename());
        stats.put("accessCount", share.getAccessCount());
        stats.put("createdAt", share.getCreatedAt());
        stats.put("expiresAt", share.getExpiresAt());
        stats.put("isActive", share.getIsActive());
        
        return stats;
    }
}

