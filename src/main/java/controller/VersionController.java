package controller;

import service.VersionService;
import service.UserService;
import model.FileVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 版本控制控制器
 */
@RestController
@RequestMapping("/api/version")
@CrossOrigin(origins = "*")
public class VersionController {
    
    @Autowired
    private VersionService versionService;
    
    @Autowired
    private UserService userService;
    
    
    /**
     * 创建文件版本
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createVersion(
            @RequestParam("fileId") Long fileId,
            @RequestParam(value = "changeDescription", required = false) String changeDescription,
            @RequestHeader("Authorization") String token) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 移除 "Bearer " 前缀
            String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            Long userId = userService.getUserIdFromToken(cleanToken);
            FileVersion version = versionService.createVersion(fileId, userId, changeDescription);
            
            response.put("success", true);
            response.put("message", "版本创建成功");
            response.put("version", version);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * 回滚到指定版本
     */
    @PostMapping("/rollback")
    public ResponseEntity<Map<String, Object>> rollbackToVersion(
            @RequestParam("fileId") Long fileId,
            @RequestParam("versionNumber") Integer versionNumber,
            @RequestHeader("Authorization") String token) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 移除 "Bearer " 前缀
            String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            Long userId = userService.getUserIdFromToken(cleanToken);
            boolean success = versionService.rollbackToVersion(fileId, userId, versionNumber);
            
            if (success) {
                response.put("success", true);
                response.put("message", "回滚成功");
            } else {
                response.put("success", false);
                response.put("message", "回滚失败");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * 获取文件版本列表
     */
    @GetMapping("/list/{fileId}")
    public ResponseEntity<Map<String, Object>> getFileVersions(
            @PathVariable Long fileId,
            @RequestHeader("Authorization") String token) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 移除 "Bearer " 前缀
            String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            Long userId = userService.getUserIdFromToken(cleanToken);
            List<FileVersion> versions = versionService.getFileVersions(fileId, userId);
            
            response.put("success", true);
            response.put("versions", versions);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * 比较两个版本
     */
    @GetMapping("/compare/{fileId}")
    public ResponseEntity<Map<String, Object>> compareVersions(
            @PathVariable Long fileId,
            @RequestParam("version1") Integer version1,
            @RequestParam("version2") Integer version2,
            @RequestHeader("Authorization") String token) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 移除 "Bearer " 前缀
            String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            Long userId = userService.getUserIdFromToken(cleanToken);
            Map<String, Object> comparison = versionService.compareVersions(fileId, userId, version1, version2);
            
            response.put("success", true);
            response.put("comparison", comparison);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/download/{fileId}/{versionNumber}")
    public ResponseEntity<byte[]> downloadVersion(@PathVariable Long fileId,
                                                  @PathVariable Integer versionNumber,
                                                  @RequestHeader("Authorization") String token) {
        try {
            String clean = token.startsWith("Bearer ") ? token.substring(7) : token;
            Long userId = userService.getUserIdFromToken(clean);

            Map<String, Object> download = versionService.downloadVersion(fileId, versionNumber, userId);
            byte[] data = (byte[]) download.get("data");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", download.get("filename").toString());
            return ResponseEntity.ok().headers(headers).body(data);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }
    
    /**
     * 删除指定版本
     */
    @DeleteMapping("/delete/{fileId}/{versionNumber}")
    public ResponseEntity<Map<String, Object>> deleteVersion(
            @PathVariable Long fileId,
            @PathVariable Integer versionNumber,
            @RequestHeader("Authorization") String token) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 移除 "Bearer " 前缀
            String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            Long userId = userService.getUserIdFromToken(cleanToken);
            boolean success = versionService.deleteVersion(fileId, userId, versionNumber);
            
            if (success) {
                response.put("success", true);
                response.put("message", "版本删除成功");
            } else {
                response.put("success", false);
                response.put("message", "版本删除失败");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}

