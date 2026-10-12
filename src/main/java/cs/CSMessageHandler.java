package cs;

import service.*;
import model.*;
import utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CS消息处理器
 * 处理来自CS客户端的消息
 */
@Component
public class CSMessageHandler {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private FileService fileService;
    
    @Autowired
    private SyncService syncService;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    /**
     * 处理CS消息
     */
    public String handleMessage(SocketChannel channel, String message) {
        try {
            CSProtocol.CSMessage csMessage = CSProtocol.parseMessage(message);
            
            // 验证用户身份（除了登录消息）
            if (!CSProtocol.LOGIN.equals(csMessage.getType())) {
                if (!validateToken(csMessage.getToken())) {
                    return CSProtocol.buildResponse(CSProtocol.AUTH_REQUIRED, "Invalid token");
                }
            }
            
            // 根据消息类型处理
            switch (csMessage.getType()) {
                case CSProtocol.LOGIN:
                    return handleLogin(csMessage);
                case CSProtocol.LOGOUT:
                    return handleLogout(csMessage);
                case CSProtocol.UPLOAD:
                    return handleUpload(csMessage);
                case CSProtocol.DOWNLOAD:
                    return handleDownload(csMessage);
                case CSProtocol.LIST:
                    return handleList(csMessage);
                case CSProtocol.DELETE:
                    return handleDelete(csMessage);
                case CSProtocol.SYNC_START:
                    return handleSyncStart(csMessage);
                case CSProtocol.SYNC_STOP:
                    return handleSyncStop(csMessage);
                case CSProtocol.SYNC_EVENTS:
                    return handleSyncEvents(csMessage);
                default:
                    return CSProtocol.buildResponse(CSProtocol.ERROR, "Unknown message type: " + csMessage.getType());
            }
            
        } catch (Exception e) {
            return CSProtocol.buildResponse(CSProtocol.ERROR, "Message processing error: " + e.getMessage());
        }
    }
    
    /**
     * 处理登录
     */
    private String handleLogin(CSProtocol.CSMessage message) {
        try {
            // 解析登录参数
            String[] loginData = message.getData().split(":");
            String username = loginData[0];
            String password = loginData[1];
            
            // 验证用户
            try {
                String token = userService.loginUser(username, password);
                return CSProtocol.buildResponse(CSProtocol.SUCCESS, "Login successful::" + token);
            } catch (RuntimeException e) {
                return CSProtocol.buildResponse(CSProtocol.ERROR, "Invalid credentials: " + e.getMessage());
            }
        } catch (Exception e) {
            return CSProtocol.buildResponse(CSProtocol.ERROR, "Login failed: " + e.getMessage());
        }
    }
    
    /**
     * 处理登出
     */
    private String handleLogout(CSProtocol.CSMessage message) {
        return CSProtocol.buildResponse(CSProtocol.SUCCESS, "Logout successful");
    }
    
    /**
     * 处理文件上传
     */
    private String handleUpload(CSProtocol.CSMessage message) {
        try {
            Long userId = getUserIdFromToken(message.getToken());
            
            // 解析上传参数
            String[] uploadData = message.getData().split(":");
            String filename = uploadData[0];
            String filePath = uploadData[1];
            String fileContent = uploadData[2]; // Base64编码的文件内容
            boolean compress = Boolean.parseBoolean(uploadData[3]);
            boolean encrypt = Boolean.parseBoolean(uploadData[4]);
            
            // 将Base64内容转换为字节数组
            byte[] fileBytes = java.util.Base64.getDecoder().decode(fileContent);
            System.out.println("[CS服务器] 文件大小: " + fileBytes.length + " bytes");
            
            // 创建临时文件
            java.io.File tempFile = java.io.File.createTempFile("upload_", "_" + filename);
            try {
                // 写入文件内容
                java.nio.file.Files.write(tempFile.toPath(), fileBytes);
                System.out.println("[CS服务器] 临时文件创建: " + tempFile.getAbsolutePath());
                
                // 创建MultipartFile对象
                org.springframework.web.multipart.MultipartFile multipartFile = new org.springframework.mock.web.MockMultipartFile(
                    "file", filename, "application/octet-stream", fileBytes
                );
                
                System.out.println("[CS服务器] 开始调用文件服务上传...");
                // 调用文件服务上传
                model.FileMetadata fileMetadata = fileService.uploadFile(multipartFile, filePath, userId, compress, encrypt);
                
                if (fileMetadata != null) {
                    System.out.println("[CS服务器] 文件上传成功，ID: " + fileMetadata.getId() + ", 存储路径: " + fileMetadata.getStoragePath());
                    try {
                        syncService.recordClientOperation(
                                userId,
                                null,
                                "UPLOAD",
                                filePath,
                                (long) fileBytes.length,
                                fileMetadata.getFileHash()
                        );
                    } catch (Exception notifyEx) {
                        System.err.println("[CS服务器] 记录云端通知失败: " + notifyEx.getMessage());
                    }
                    return CSProtocol.buildResponse(CSProtocol.SUCCESS, "File uploaded successfully, ID: " + fileMetadata.getId());
                } else {
                    System.out.println("[CS服务器] 文件上传失败: 返回null");
                    return CSProtocol.buildResponse(CSProtocol.ERROR, "Upload failed: Unknown error");
                }
            } catch (Exception uploadException) {
                System.out.println("[CS服务器] 文件上传异常: " + uploadException.getMessage());
                uploadException.printStackTrace();
                return CSProtocol.buildResponse(CSProtocol.ERROR, "Upload failed: " + uploadException.getMessage());
            } finally {
                // 删除临时文件
                if (tempFile.exists()) {
                    tempFile.delete();
                    System.out.println("[CS服务器] 临时文件已删除");
                }
            }
        } catch (Exception e) {
            return CSProtocol.buildResponse(CSProtocol.ERROR, "Upload failed: " + e.getMessage());
        }
    }
    
    /**
     * 处理文件下载
     */
    private String handleDownload(CSProtocol.CSMessage message) {
        try {
            Long userId = getUserIdFromToken(message.getToken());
            Long fileId = Long.parseLong(message.getData());
            
            System.out.println("[CS服务器] 开始下载文件ID: " + fileId + ", 用户ID: " + userId);
            
            // 调用文件服务下载
            Map<String, Object> downloadResult = fileService.downloadFile(fileId, userId);
            
            if ((Boolean) downloadResult.get("success")) {
                byte[] fileData = (byte[]) downloadResult.get("fileData");
                String filename = (String) downloadResult.get("filename");
                
                System.out.println("[CS服务器] 文件数据大小: " + fileData.length + " 字节");
                System.out.println("[CS服务器] 文件名: " + filename);
                
                // 对文件数据进行Base64编码
                String fileContent = java.util.Base64.getEncoder().encodeToString(fileData);
                System.out.println("[CS服务器] Base64编码后长度: " + fileContent.length());
                
                return CSProtocol.buildResponse(CSProtocol.SUCCESS, 
                    filename + "::" + fileContent);
            } else {
                System.out.println("[CS服务器] 文件下载失败");
                return CSProtocol.buildResponse(CSProtocol.ERROR, "Download failed");
            }
        } catch (Exception e) {
            System.out.println("[CS服务器] 下载异常: " + e.getMessage());
            e.printStackTrace();
            return CSProtocol.buildResponse(CSProtocol.ERROR, "Download failed: " + e.getMessage());
        }
    }
    
    /**
     * 处理文件列表
     */
    private String handleList(CSProtocol.CSMessage message) {
        try {
            Long userId = getUserIdFromToken(message.getToken());
            String path = message.getData();
            
            // 获取用户文件列表
            List<FileMetadata> files = fileService.getUserFiles(userId, path);
            
            StringBuilder fileList = new StringBuilder();
for (FileMetadata file : files) {
    Long storedSize = file.getStoredSize();

    fileList.append(file.getId()).append(":")
            .append(file.getFilename()).append(":")
            .append(file.getFileSize() != null ? file.getFileSize() : 0L).append(":")
            .append(storedSize != null ? storedSize : file.getFileSize()).append(":")
            .append(file.getCreatedAt()).append(":")
            .append(file.getFilePath() != null ? file.getFilePath() : "/")
            .append(";");
}

            
            return CSProtocol.buildResponse(CSProtocol.SUCCESS, fileList.toString());
        } catch (Exception e) {
            return CSProtocol.buildResponse(CSProtocol.ERROR, "List failed: " + e.getMessage());
        }
    }
    
    /**
     * 处理文件删除
     */
    private String handleDelete(CSProtocol.CSMessage message) {
        try {
            Long userId = getUserIdFromToken(message.getToken());
            Long fileId = Long.parseLong(message.getData());
            
            // 删除文件
            fileService.deleteFile(fileId, userId);
            
            return CSProtocol.buildResponse(CSProtocol.SUCCESS, "File deleted successfully");
        } catch (Exception e) {
            return CSProtocol.buildResponse(CSProtocol.ERROR, "Delete failed: " + e.getMessage());
        }
    }
    
    /**
     * 处理开始同步
     */
    private String handleSyncStart(CSProtocol.CSMessage message) {
        try {
            Long userId = getUserIdFromToken(message.getToken());
            
            // 使用最后一个冒号来分割路径和客户端ID
            String data = message.getData();
            int lastColonIndex = data.lastIndexOf(':');
            if (lastColonIndex == -1) {
                throw new IllegalArgumentException("Invalid sync data format");
            }
            
            // 正确提取路径和客户端ID
            String syncPath = data.substring(0, lastColonIndex).trim();
            String clientId = data.substring(lastColonIndex + 1).trim();
            
            System.out.println("=== 开始同步调试信息 ===");
            System.out.println("完整同步数据: " + data);
            System.out.println("原始同步路径: " + syncPath);
            
            // 标准化路径格式
            syncPath = syncPath.replace('\\', '/');
            if (syncPath.endsWith("/")) {
                syncPath = syncPath.substring(0, syncPath.length() - 1);
            }
            
            // 确保使用完整路径而不是取第一个部分
            if (syncPath.contains("/")) {
                System.out.println("处理后的同步路径: " + syncPath);
            } else {
                throw new IllegalArgumentException("Invalid sync path: " + syncPath + ". Please provide a complete folder path.");
            }
            System.out.println("最终客户端ID: " + clientId);
            
            // 开始同步
            syncService.startSync(syncPath, userId, clientId);
            
            return CSProtocol.buildResponse(CSProtocol.SUCCESS, "Sync started successfully");
        } catch (Exception e) {
            return CSProtocol.buildResponse(CSProtocol.ERROR, "Sync start failed: " + e.getMessage());
        }
    }
    
    /**
     * 处理停止同步
     */
    private String handleSyncStop(CSProtocol.CSMessage message) {
        try {
            syncService.stopSync();
            return CSProtocol.buildResponse(CSProtocol.SUCCESS, "Sync stopped successfully");
        } catch (Exception e) {
            return CSProtocol.buildResponse(CSProtocol.ERROR, "Sync stop failed: " + e.getMessage());
        }
    }
    
    /**
     * 处理获取同步事件
     */
    private String handleSyncEvents(CSProtocol.CSMessage message) {
        try {
            Long userId = getUserIdFromToken(message.getToken());
            
            // 获取同步事件
            List<SyncEvent> events = syncService.getUnprocessedEvents(userId);
            
            StringBuilder eventList = new StringBuilder();
            for (SyncEvent event : events) {
                eventList.append(event.getId()).append(":")
                       .append(event.getEventType()).append(":")
                       .append(event.getFilePath()).append(":")
                       .append(event.getEventTime()).append(";");
            }
            
            return CSProtocol.buildResponse(CSProtocol.SUCCESS, eventList.toString());
        } catch (Exception e) {
            return CSProtocol.buildResponse(CSProtocol.ERROR, "Get sync events failed: " + e.getMessage());
        }
    }
    
    /**
     * 验证token
     */
    private boolean validateToken(String token) {
        try {
            System.out.println("[CS服务器] 验证token: " + token.substring(0, Math.min(20, token.length())) + "...");
            String username = jwtUtil.getUsernameFromToken(token);
            System.out.println("[CS服务器] 从token获取用户名: " + username);
            boolean isValid = jwtUtil.validateToken(token, username);
            System.out.println("[CS服务器] Token验证结果: " + isValid);
            return isValid;
        } catch (Exception e) {
            System.out.println("[CS服务器] Token验证异常: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 从token获取用户ID
     */
    private Long getUserIdFromToken(String token) {
        try {
            return jwtUtil.getUserIdFromToken(token);
        } catch (Exception e) {
            throw new RuntimeException("Invalid token");
        }
    }
}
