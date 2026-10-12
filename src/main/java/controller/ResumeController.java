package controller;

import service.ResumeService;
import service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 断点续传控制器
 */
@RestController
@RequestMapping("/api/resume")
@CrossOrigin(origins = "*")
public class ResumeController {
    
    @Autowired
    private ResumeService resumeService;
    
    @Autowired
    private UserService userService;
    
    /**
     * 开始断点续传上传
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startResumeUpload(
            @RequestParam("fileName") String fileName,
            @RequestParam("fileSize") Long fileSize,
            @RequestParam("filePath") String filePath,
            @RequestHeader("Authorization") String token) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 移除 "Bearer " 前缀
            String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            Long userId = userService.getUserIdFromToken(cleanToken);
            String uploadId = resumeService.startResumeUpload(fileName, fileSize, userId, filePath);
            
            response.put("success", true);
            response.put("uploadId", uploadId);
            response.put("message", "断点续传上传已开始");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * 上传文件块
     */
    @PostMapping("/chunk")
    public ResponseEntity<Map<String, Object>> uploadChunk(
            @RequestParam("uploadId") String uploadId,
            @RequestParam("chunkIndex") int chunkIndex,
            @RequestParam("chunk") MultipartFile chunk,
            @RequestHeader("Authorization") String token) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            byte[] chunkData = chunk.getBytes();
            boolean success = resumeService.uploadChunk(uploadId, chunkIndex, chunkData);
            
            if (success) {
                response.put("success", true);
                response.put("message", "文件块上传成功");
            } else {
                response.put("success", false);
                response.put("message", "文件块上传失败");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * 完成上传
     */
    @PostMapping("/complete")
    public ResponseEntity<Map<String, Object>> completeUpload(
            @RequestParam("uploadId") String uploadId,
            @RequestHeader("Authorization") String token) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean success = resumeService.completeUpload(uploadId);
            
            if (success) {
                response.put("success", true);
                response.put("message", "文件上传完成");
            } else {
                response.put("success", false);
                response.put("message", "文件上传完成失败");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * 获取上传进度
     */
    @GetMapping("/progress/{uploadId}")
    public ResponseEntity<Map<String, Object>> getUploadProgress(
            @PathVariable String uploadId,
            @RequestHeader("Authorization") String token) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            Map<String, Object> progress = resumeService.getUploadProgress(uploadId);
            
            if (progress != null) {
                response.put("success", true);
                response.put("progress", progress);
            } else {
                response.put("success", false);
                response.put("message", "上传进度不存在");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * 取消上传
     */
    @PostMapping("/cancel")
    public ResponseEntity<Map<String, Object>> cancelUpload(
            @RequestParam("uploadId") String uploadId,
            @RequestHeader("Authorization") String token) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean success = resumeService.cancelUpload(uploadId);
            
            if (success) {
                response.put("success", true);
                response.put("message", "上传已取消");
            } else {
                response.put("success", false);
                response.put("message", "取消上传失败");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * 恢复上传
     */
    @PostMapping("/resume")
    public ResponseEntity<Map<String, Object>> resumeUpload(
            @RequestParam("uploadId") String uploadId,
            @RequestHeader("Authorization") String token) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean success = resumeService.resumeUpload(uploadId);
            
            if (success) {
                response.put("success", true);
                response.put("message", "上传已恢复");
            } else {
                response.put("success", false);
                response.put("message", "恢复上传失败");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}

