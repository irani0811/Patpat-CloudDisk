package controller;

import service.FileService;
import service.UserService;
import model.FileMetadata;
import model.FileInfoDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import model.ConflictResolveRequest;
import java.util.Base64;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 文件控制器
 */
@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "*")
public class FileController {
    
    @Autowired
    private FileService fileService;
    
    @Autowired
    private UserService userService;
    
    /**
     * 上传文件
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("path") String filePath,
            @RequestHeader("Authorization") String token,
            @RequestParam(value = "compress", defaultValue = "false") boolean compress,
            @RequestParam(value = "encrypt", defaultValue = "false") boolean encrypt) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 验证token
            String cleanToken = cleanToken(token);
            String username = extractUsernameFromToken(token);
            Long userId = userService.getUserIdFromToken(cleanToken);
            
            long fileSize = file.getSize();
            
            // 自动判断是否需要断点续传
            if (fileSize > 10 * 1024 * 1024) { // 大于10MB，建议使用断点续传
                response.put("success", false);
                response.put("message", "文件过大，建议使用智能上传功能");
                response.put("suggestion", "大文件将自动使用断点续传");
                response.put("fileSize", fileSize);
                response.put("threshold", 10 * 1024 * 1024);
                return ResponseEntity.badRequest().body(response);
            } else {
                // 小文件直接上传
                FileMetadata fileMetadata = fileService.uploadFile(file, filePath, userId, compress, encrypt);
                
                response.put("success", true);
                response.put("message", "文件上传成功");
                response.put("fileId", fileMetadata.getId());
                response.put("fileName", fileMetadata.getFilename());
                response.put("fileSize", fileMetadata.getFileSize());
                response.put("uploadType", "direct");
                
                return ResponseEntity.ok(response);
            }
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/conflict-check")
    public ResponseEntity<Map<String, Object>> checkConflict(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Object> payload) {

        Map<String, Object> response = new HashMap<>();
        try {
            String cleanToken = cleanToken(token);
            Long userId = userService.getUserIdFromToken(cleanToken);

            Long fileId = payload.get("fileId") != null
                    ? Long.parseLong(payload.get("fileId").toString())
                    : null;
            Integer clientVersion = payload.get("clientVersion") != null
                    ? Integer.parseInt(payload.get("clientVersion").toString())
                    : null;
            String clientHash = payload.get("clientHash") != null
                    ? payload.get("clientHash").toString()
                    : null;

            if (fileId == null) {
                response.put("success", false);
                response.put("message", "缺少 fileId");
                return ResponseEntity.badRequest().body(response);
            }

            Map<String, Object> result = fileService.checkUploadConflict(
                    fileId, userId, clientVersion, clientHash);

            response.put("success", true);
            response.putAll(result);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/resolve-conflict")
public ResponseEntity<Map<String, Object>> resolveConflict(
        @RequestHeader("Authorization") String token,
        @RequestBody ConflictResolveRequest request) {

    Map<String, Object> response = new HashMap<>();
    try {
        String cleanToken = cleanToken(token);
        Long userId = userService.getUserIdFromToken(cleanToken);

        if (request.getFileId() == null || request.getBase64Content() == null) {
            response.put("success", false);
            response.put("message", "缺少 fileId 或 base64Content");
            return ResponseEntity.badRequest().body(response);
        }

        byte[] rawBytes = Base64.getDecoder().decode(request.getBase64Content());
        Map<String, Object> result = fileService.resolveConflictUpload(
                request.getFileId(),
                userId,
                rawBytes,
                Boolean.TRUE.equals(request.getCompress()),
                Boolean.TRUE.equals(request.getEncrypt()),
                request.getStrategy(),
                request.getNote()
        );

        response.putAll(result);
        return ResponseEntity.ok(response);

    } catch (Exception e) {
        response.put("success", false);
        response.put("message", e.getMessage());
        return ResponseEntity.badRequest().body(response);
    }
}
    
    /**
     * 智能文件上传 - 自动判断上传方式
     */
    @PostMapping("/upload-smart")
    public ResponseEntity<Map<String, Object>> uploadFileSmart(
            @RequestParam("file") MultipartFile file,
            @RequestParam("path") String filePath,
            @RequestHeader("Authorization") String token,
            @RequestParam(value = "compress", defaultValue = "false") boolean compress,
            @RequestParam(value = "encrypt", defaultValue = "false") boolean encrypt) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            String cleanToken = cleanToken(token);
            Long userId = userService.getUserIdFromToken(cleanToken);
            
            long fileSize = file.getSize();
            
            if (fileSize > 10 * 1024 * 1024) { // 大于10MB，使用断点续传
                response.put("success", false);
                response.put("message", "大文件需要断点续传");
                response.put("uploadType", "resume");
                response.put("fileSize", fileSize);
                response.put("suggestion", "请使用智能上传功能");
                return ResponseEntity.badRequest().body(response);
            } else {
                // 小文件直接上传
                FileMetadata fileMetadata = fileService.uploadFile(file, filePath, userId, compress, encrypt);
                
                response.put("success", true);
                response.put("message", "文件上传成功");
                response.put("fileId", fileMetadata.getId());
                response.put("uploadType", "direct");
                
                return ResponseEntity.ok(response);
            }
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * 下载文件
     */
    @Autowired
    private ObjectMapper objectMapper;

    @GetMapping("/download/{fileId}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable Long fileId, @RequestHeader("Authorization") String token) {
        try {
            String cleanToken = cleanToken(token);
            Long userId = userService.getUserIdFromToken(cleanToken);
            Map<String, Object> downloadResult = fileService.downloadFile(fileId, userId);
            byte[] fileData = (byte[]) downloadResult.get("fileData");

            FileInfoDTO infoDTO = (FileInfoDTO) downloadResult.get("fileInfo");
            if (infoDTO == null) {
                return ResponseEntity.badRequest().build();
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", infoDTO.getFilename());
            headers.add("X-File-Metadata", objectMapper.writeValueAsString(infoDTO));

            return ResponseEntity.ok()
                .headers(headers)
                .body(fileData);

        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 获取文件列表
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getFileList(
            @RequestHeader("Authorization") String token,
            @RequestParam(value = "path", required = false) String path) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            String cleanToken = cleanToken(token);
            Long userId = userService.getUserIdFromToken(cleanToken);
            
            System.out.println("=== 文件列表API调试信息 ===");
            System.out.println("用户ID: " + userId);
            System.out.println("路径: " + path);
            
            List<FileMetadata> files = fileService.getUserFiles(userId, path);
            
            System.out.println("从数据库获取的文件数量: " + files.size());
            
            // 转换为DTO避免循环引用
            List<FileInfoDTO> fileDTOs = files.stream()
                .map(FileInfoDTO::new)
                .collect(java.util.stream.Collectors.toList());
            
            System.out.println("转换后的DTO数量: " + fileDTOs.size());
            
            response.put("success", true);
            response.put("data", fileDTOs);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * 删除文件
     */
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Map<String, Object>> deleteFile(
            @PathVariable Long fileId,
            @RequestHeader("Authorization") String token) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            String cleanToken = cleanToken(token);
            Long userId = userService.getUserIdFromToken(cleanToken);
            fileService.deleteFile(fileId, userId);
            
            response.put("success", true);
            response.put("message", "文件删除成功");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * 创建目录
     */
    @PostMapping("/mkdir")
    public ResponseEntity<Map<String, Object>> createDirectory(
            @RequestParam("path") String dirPath,
            @RequestHeader("Authorization") String token) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 移除 "Bearer " 前缀
            String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            Long userId = userService.getUserIdFromToken(cleanToken);
            FileMetadata directory = fileService.createDirectory(dirPath, userId);
            
            response.put("success", true);
            response.put("message", "目录创建成功");
            response.put("directoryId", directory.getId());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * 清理token，移除Bearer前缀
     */
    private String cleanToken(String token) {
        return token.startsWith("Bearer ") ? token.substring(7) : token;
    }
    
    /**
     * 从token中提取用户名
     */
    private String extractUsernameFromToken(String token) {
        try {
            String cleanToken = cleanToken(token);
            Long userId = userService.getUserIdFromToken(cleanToken);
            return userService.getUserById(userId)
                .map(user -> user.getUsername())
                .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 多线程文件上传
     */
    @PostMapping("/upload-multithread")
    public ResponseEntity<Map<String, Object>> uploadFileMultiThread(
            @RequestParam("file") MultipartFile file,
            @RequestParam("path") String filePath,
            @RequestHeader("Authorization") String token,
            @RequestParam(value = "threads", defaultValue = "4") int threads,
            @RequestParam(value = "compress", defaultValue = "false") boolean compress,
            @RequestParam(value = "encrypt", defaultValue = "false") boolean encrypt) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            String cleanToken = cleanToken(token);
            String username = extractUsernameFromToken(token);
            Long userId = userService.getUserIdFromToken(cleanToken);
            
            // 使用多线程上传
            FileMetadata fileMetadata = fileService.uploadFileMultiThread(file, filePath, userId, threads, compress, encrypt);
            
            response.put("success", true);
            response.put("message", "多线程上传成功");
            response.put("fileId", fileMetadata.getId());
            response.put("filename", fileMetadata.getFilename());
            response.put("fileSize", fileMetadata.getFileSize());
            response.put("fileHash", fileMetadata.getFileHash());
            response.put("threads", threads);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "多线程上传失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * 获取文件MD5校验
     */
    @GetMapping("/verify/{fileId}")
    public ResponseEntity<Map<String, Object>> verifyFileHash(
            @PathVariable Long fileId,
            @RequestHeader("Authorization") String token) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            String cleanToken = cleanToken(token);
            Long userId = userService.getUserIdFromToken(cleanToken);
            
            Map<String, Object> verification = fileService.verifyFileHash(fileId, userId);
            
            response.put("success", true);
            response.put("verification", verification);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "文件校验失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * 批量文件上传
     */
    @PostMapping("/upload-batch")
    public ResponseEntity<Map<String, Object>> uploadFilesBatch(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("path") String filePath,
            @RequestHeader("Authorization") String token,
            @RequestParam(value = "compress", defaultValue = "false") boolean compress,
            @RequestParam(value = "encrypt", defaultValue = "false") boolean encrypt) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            String cleanToken = cleanToken(token);
            Long userId = userService.getUserIdFromToken(cleanToken);
            
            List<Map<String, Object>> results = fileService.uploadFilesBatch(files, filePath, userId, compress, encrypt);
            
            response.put("success", true);
            response.put("message", "批量上传完成");
            response.put("results", results);
            response.put("totalFiles", files.length);
            response.put("successCount", results.stream().mapToInt(r -> (Boolean) r.get("success") ? 1 : 0).sum());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "批量上传失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    
    /**
     * 多线程文件下载
     */
    @GetMapping("/download-multithread/{fileId}")
    public ResponseEntity<Map<String, Object>> downloadFileMultiThread(
            @PathVariable Long fileId,
            @RequestHeader("Authorization") String token,
            @RequestParam(value = "threads", defaultValue = "4") int threads) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            String cleanToken = cleanToken(token);
            Long userId = userService.getUserIdFromToken(cleanToken);
            
            Map<String, Object> downloadResult = fileService.downloadFileMultiThread(fileId, userId, threads);
            
            response.put("success", downloadResult.get("success"));
            response.put("message", downloadResult.get("message"));
            response.put("downloadInfo", downloadResult.get("downloadInfo"));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "多线程下载失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * 批量文件下载
     */
    @PostMapping("/download-batch")
    public ResponseEntity<Map<String, Object>> downloadFilesBatch(
            @RequestParam("fileIds") String fileIds,
            @RequestHeader("Authorization") String token,
            @RequestParam(value = "format", defaultValue = "zip") String format) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            String cleanToken = cleanToken(token);
            Long userId = userService.getUserIdFromToken(cleanToken);
            
            // 解析文件ID列表
            String[] fileIdArray = fileIds.split(",");
            Long[] fileIdLongs = new Long[fileIdArray.length];
            for (int i = 0; i < fileIdArray.length; i++) {
                fileIdLongs[i] = Long.parseLong(fileIdArray[i].trim());
            }
            
            Map<String, Object> downloadResult = fileService.downloadFilesBatch(fileIdLongs, userId, format);
            
            response.put("success", downloadResult.get("success"));
            response.put("message", downloadResult.get("message"));
            response.put("downloadInfo", downloadResult.get("downloadInfo"));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "批量下载失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * 获取下载链接
     */
    @GetMapping("/download-link/{fileId}")
    public ResponseEntity<Map<String, Object>> getDownloadLink(
            @PathVariable Long fileId,
            @RequestHeader("Authorization") String token,
            @RequestParam(value = "expireMinutes", defaultValue = "60") int expireMinutes) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            String cleanToken = cleanToken(token);
            Long userId = userService.getUserIdFromToken(cleanToken);
            
            Map<String, Object> linkResult = fileService.generateDownloadLink(fileId, userId, expireMinutes);
            
            response.put("success", linkResult.get("success"));
            response.put("message", linkResult.get("message"));
            response.put("downloadLink", linkResult.get("downloadLink"));
            response.put("expireTime", linkResult.get("expireTime"));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "生成下载链接失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}

