package controller;

import service.ShareService;
import service.UserService;
import model.FileShare;
import model.FileMetadata;
import model.FileShareDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文件共享控制器
 */
@RestController
@RequestMapping("/api/share")
@CrossOrigin(origins = "*")
public class ShareController {
    
    @Autowired
    private ShareService shareService;
    
    @Autowired
    private UserService userService;
    
    /**
     * 创建文件共享
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createShare(
            @RequestParam("fileId") Long fileId,
            @RequestParam("shareWith") String shareWith,
            @RequestParam("permission") String permission,
            @RequestParam(value = "expireDays", required = false) Integer expireDays,
            @RequestHeader("Authorization") String token) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 移除 "Bearer " 前缀
            String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            Long userId = userService.getUserIdFromToken(cleanToken);
            FileShare share = shareService.createShare(fileId, userId, shareWith, permission, expireDays);
            
            response.put("success", true);
            response.put("message", "文件共享创建成功");
            response.put("shareToken", share.getShareToken());
            response.put("shareId", share.getId());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @GetMapping("/shared-with-me")
    public ResponseEntity<Map<String, Object>> getSharedWithMe(@RequestHeader("Authorization") String token) {
        Map<String, Object> response = new HashMap<>();
        try {
            String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            Long userId = userService.getUserIdFromToken(cleanToken);
            List<FileShareDTO> shares = shareService.getSharedWithMe(userId).stream()
                .map(FileShareDTO::recipientView)
                .collect(Collectors.toList());
            response.put("success", true);
            response.put("shares", shares);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * 通过共享token访问文件
     */
    @GetMapping("/access/{shareToken}")
    public ResponseEntity<Map<String, Object>> accessSharedFile(@PathVariable String shareToken) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            FileMetadata file = shareService.accessSharedFile(shareToken);
            
            response.put("success", true);
            response.put("file", file);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * 获取用户的共享文件列表
     */
        @GetMapping("/my-shares")
    public ResponseEntity<Map<String, Object>> getMyShares(@RequestHeader("Authorization") String token) {
        Map<String, Object> response = new HashMap<>();

        try {
            String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            Long userId = userService.getUserIdFromToken(cleanToken);
            List<FileShareDTO> shares = shareService.getUserSharedFiles(userId).stream()
                .map(FileShareDTO::ownerView)
                .collect(Collectors.toList());

            response.put("success", true);
            response.put("shares", shares);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    
    /**
     * 获取文件的共享信息
     */
       @GetMapping("/file/{fileId}")
    public ResponseEntity<Map<String, Object>> getFileShares(
            @PathVariable Long fileId,
            @RequestHeader("Authorization") String token) {

        Map<String, Object> response = new HashMap<>();

        try {
            String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            Long userId = userService.getUserIdFromToken(cleanToken);
            List<FileShareDTO> shares = shareService.getFileShares(fileId).stream()
                .map(FileShareDTO::ownerView)
                .collect(Collectors.toList());

            response.put("success", true);
            response.put("shares", shares);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * 更新共享权限
     */
    @PutMapping("/update/{shareId}")
    public ResponseEntity<Map<String, Object>> updateSharePermission(
            @PathVariable Long shareId,
            @RequestParam("permission") String permission,
            @RequestParam(value = "expireDays", required = false) Integer expireDays,
            @RequestHeader("Authorization") String token) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            FileShare share = shareService.updateSharePermission(shareId, permission, expireDays);
            
            response.put("success", true);
            response.put("message", "共享权限更新成功");
            response.put("share", share);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * 取消共享
     */
    @DeleteMapping("/cancel/{shareId}")
    public ResponseEntity<Map<String, Object>> cancelShare(
            @PathVariable Long shareId,
            @RequestHeader("Authorization") String token) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 移除 "Bearer " 前缀
            String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            Long userId = userService.getUserIdFromToken(cleanToken);
            boolean success = shareService.cancelShare(shareId, userId);
            
            if (success) {
                response.put("success", true);
                response.put("message", "共享已取消");
            } else {
                response.put("success", false);
                response.put("message", "取消共享失败");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * 获取共享统计信息
     */
    @GetMapping("/stats/{shareId}")
    public ResponseEntity<Map<String, Object>> getShareStatistics(
            @PathVariable Long shareId,
            @RequestHeader("Authorization") String token) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            Map<String, Object> stats = shareService.getShareStatistics(shareId);
            
            if (stats != null) {
                response.put("success", true);
                response.put("statistics", stats);
            } else {
                response.put("success", false);
                response.put("message", "共享记录不存在");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}

