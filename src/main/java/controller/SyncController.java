package controller;

import dto.SyncNotificationDTO;
import service.SyncService;
import service.UserService;
import model.SyncEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 同步控制器
 */
@RestController
@RequestMapping("/api/sync")
@CrossOrigin(origins = "*")
public class SyncController {
    
    @Autowired
    private SyncService syncService;
    
    @Autowired
    private UserService userService;
    
    /**
     * 开始同步
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startSync(
            @RequestParam("syncPath") String syncPath,
            @RequestParam("clientId") String clientId,
            @RequestHeader("Authorization") String token) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 移除 "Bearer " 前缀
            String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            Long userId = userService.getUserIdFromToken(cleanToken);
            syncService.startSync(syncPath, userId, clientId);
            
            response.put("success", true);
            response.put("message", "同步已开始");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * 停止同步
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopSync(@RequestHeader("Authorization") String token) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            syncService.stopSync();
            
            response.put("success", true);
            response.put("message", "同步已停止");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * 获取同步事件
     */
    @GetMapping("/events")
    public ResponseEntity<Map<String, Object>> getSyncEvents(@RequestHeader("Authorization") String token) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 移除 "Bearer " 前缀
            String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            Long userId = userService.getUserIdFromToken(cleanToken);
            List<SyncEvent> events = syncService.getUnprocessedEvents(userId);
            
            response.put("success", true);
            response.put("events", events);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * 获取云端通知
     */
    @GetMapping("/notifications")
    public ResponseEntity<Map<String, Object>> getNotifications(
            @RequestHeader("Authorization") String token,
            @RequestParam(value = "sinceId", required = false) Long sinceId,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "clientId", required = false) String clientId) {
        
        Map<String, Object> response = new HashMap<>();
        try {
            String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            Long userId = userService.getUserIdFromToken(cleanToken);
            List<SyncNotificationDTO> events = syncService.getNotifications(userId, sinceId, limit, clientId);
            
            response.put("success", true);
            response.put("events", events);
            if (!events.isEmpty()) {
                response.put("lastEventId", events.get(events.size() - 1).getId());
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * 执行差分同步
     */
    @PostMapping("/delta-sync")
    public ResponseEntity<Map<String, Object>> performDeltaSync(
            @RequestParam("filePath") String filePath,
            @RequestHeader("Authorization") String token) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 移除 "Bearer " 前缀
            String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            Long userId = userService.getUserIdFromToken(cleanToken);
            syncService.performDeltaSync(filePath, userId);
            
            response.put("success", true);
            response.put("message", "差分同步完成");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}

