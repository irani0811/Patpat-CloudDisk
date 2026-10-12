package cs.client;

import cs.CSProtocol;
import model.FileMetadata;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Scanner;
import java.util.Map;
import repository.FileMetadataRepository;
import service.ShareService;
/**
 * CS客户端核心类
 * 提供与CS服务器的通信功能
 */
public class CSClient {
    
    private SocketChannel socketChannel;
    private String serverHost = "localhost";
    private int serverPort = 8889;
    private String token = null;
    private boolean isConnected = false;
    private FileMetadataRepository fileMetadataRepository;
    private ShareService shareService;
    
    /**
     * 连接到CS服务器
     */
    public boolean connect() {
        try {
            socketChannel = SocketChannel.open();
            socketChannel.connect(new InetSocketAddress(serverHost, serverPort));
            isConnected = true;
            System.out.println("[CS客户端] 已连接到服务器: " + serverHost + ":" + serverPort);
            return true;
        } catch (IOException e) {
            System.err.println("[CS客户端] 连接失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 断开连接
     */
    public void disconnect() {
        try {
            if (socketChannel != null && socketChannel.isConnected()) {
                socketChannel.close();
            }
            isConnected = false;
            token = null;
            System.out.println("[CS客户端] 已断开连接");
        } catch (IOException e) {
            System.err.println("[CS客户端] 断开连接失败: " + e.getMessage());
        }
    }
    
    /**
     * 发送消息到服务器
     */
    public String sendMessage(String message) {
        if (!isConnected) {
            return "ERROR::Not connected to server";
        }
        
        try {
            // 发送消息
            byte[] messageBytes = message.getBytes();
            ByteBuffer buffer = ByteBuffer.allocate(4 + messageBytes.length);
            buffer.putInt(messageBytes.length);
            buffer.put(messageBytes);
            buffer.flip();
            
            while (buffer.hasRemaining()) {
                socketChannel.write(buffer);
            }
            
            // 接收响应
            return receiveResponse();
            
        } catch (IOException e) {
            System.err.println("[CS客户端] 发送消息失败: " + e.getMessage());
            return "ERROR::Send message failed: " + e.getMessage();
        }
    }
    
    /**
     * 接收服务器响应
     */
    private String receiveResponse() throws IOException {
        // 读取响应长度
        ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
        int bytesRead = socketChannel.read(lengthBuffer);
        
        if (bytesRead == -1) {
            throw new IOException("Connection closed by server");
        }
        
        lengthBuffer.flip();
        int responseLength = lengthBuffer.getInt();
        
        // 读取响应内容
        ByteBuffer responseBuffer = ByteBuffer.allocate(responseLength);
        int totalRead = 0;
        
        while (totalRead < responseLength) {
            int read = socketChannel.read(responseBuffer);
            if (read == -1) {
                throw new IOException("Connection closed by server");
            }
            totalRead += read;
        }
        
        responseBuffer.flip();
        return new String(responseBuffer.array());
    }
    
    /**
     * 用户登录
     */
    public boolean login(String username, String password) {
        String message = CSProtocol.buildMessage(CSProtocol.LOGIN, "", username + ":" + password);
        String response = sendMessage(message);
        
        if (response.startsWith(CSProtocol.SUCCESS)) {
            // 解析token - 格式: SUCCESS::Login successful::token
            String[] parts = response.split("::");
            if (parts.length > 2) {
                token = parts[2]; // token在第三个位置
                System.out.println("[CS客户端] 登录成功，Token: " + token.substring(0, Math.min(20, token.length())) + "...");
                return true;
            }
        }
        
        System.out.println("[CS客户端] 登录失败: " + response);
        return false;
    }
    
    /**
     * 用户登出
     */
    public boolean logout() {
        if (token == null) {
            System.out.println("[CS客户端] 未登录");
            return false;
        }
        
        String message = CSProtocol.buildMessage(CSProtocol.LOGOUT, token, "");
        String response = sendMessage(message);
        
        if (response.startsWith(CSProtocol.SUCCESS)) {
            token = null;
            System.out.println("[CS客户端] 登出成功");
            return true;
        }
        
        System.out.println("[CS客户端] 登出失败: " + response);
        return false;
    }
    
    /**
     * 上传文件
     */
    public boolean uploadFile(String filename, String filePath, String content, boolean compress, boolean encrypt) {
        if (token == null) {
            System.out.println("[CS客户端] 请先登录");
            return false;
        }

        String message = CSProtocol.buildMessage(CSProtocol.UPLOAD, token,
            filename + ":" + filePath + ":" + content + ":" + compress + ":" + encrypt);
        String response = sendMessage(message);

        if (response.startsWith(CSProtocol.SUCCESS)) {
            System.out.println("[CS客户端] 文件上传成功: " + filename);
            return true;
        }

        System.out.println("[CS客户端] 文件上传失败: " + response);
        return false;
    }

    
    /**
     * 检查文件上传冲突
     */
    public Map<String, Object> checkUploadConflict(Long fileId, Long userId,
                                                Integer clientVersion, String clientHash) {
        FileMetadata file = fileMetadataRepository.findById(fileId)
            .orElseThrow(() -> new RuntimeException("文件不存在"));

        boolean hasPermission = file.getOwner().getId().equals(userId)
            || shareService.hasFileAccess(fileId, userId, "WRITE");
        if (!hasPermission) {
            throw new RuntimeException("无权限上传此文件");
        }

        Map<String, Object> result = new HashMap<>();
        Integer latestVersion = file.getVersion();
        String latestHash = file.getFileHash();

        boolean hasServerSnapshot = (latestVersion != null)
            || (latestHash != null && !latestHash.isEmpty());

        boolean conflict;
        if (!hasServerSnapshot) {
            conflict = false;
        } else if (clientVersion != null && latestVersion != null
                && latestVersion.equals(clientVersion)) {
            conflict = false;
        } else if (clientHash != null && latestHash != null
                && latestHash.equals(clientHash)) {
            conflict = false;
        } else {
            conflict = true;
        }

        result.put("success", true);
        result.put("conflict", conflict);
        result.put("latestVersion", latestVersion);
        result.put("latestHash", latestHash);
        result.put("updatedAt", file.getUpdatedAt());
        result.put("owner", file.getOwner() != null ? file.getOwner().getUsername() : null);

        return result;
    }
    
    /**
     * 下载文件
     */
    public String downloadFile(Long fileId) {
        if (token == null) {
            return "ERROR::Please login first";
        }
        
        String message = CSProtocol.buildMessage(CSProtocol.DOWNLOAD, token, fileId.toString());
        String response = sendMessage(message);
        
        if (response.startsWith(CSProtocol.SUCCESS)) {
            System.out.println("[CS客户端] 文件下载成功");
            return response;
        }
        
        System.out.println("[CS客户端] 文件下载失败: " + response);
        return response;
    }
    
    /**
     * 获取文件列表
     */
    public String getFileList(String path) {
        if (token == null) {
            return "ERROR::Please login first";
        }
        
        String message = CSProtocol.buildMessage(CSProtocol.LIST, token, path);
        String response = sendMessage(message);
        
        if (response.startsWith(CSProtocol.SUCCESS)) {
            System.out.println("[CS客户端] 获取文件列表成功");
            return response;
        }
        
        System.out.println("[CS客户端] 获取文件列表失败: " + response);
        return response;
    }
    
    /**
     * 删除文件
     */
    public boolean deleteFile(Long fileId) {
        if (token == null) {
            System.out.println("[CS客户端] 请先登录");
            return false;
        }
        
        String message = CSProtocol.buildMessage(CSProtocol.DELETE, token, fileId.toString());
        String response = sendMessage(message);
        
        if (response.startsWith(CSProtocol.SUCCESS)) {
            System.out.println("[CS客户端] 文件删除成功");
            return true;
        }
        
        System.out.println("[CS客户端] 文件删除失败: " + response);
        return false;
    }
    
    /**
     * 开始同步
     */
    public boolean startSync(String syncPath, String clientId) {
        if (token == null) {
            System.out.println("[CS客户端] 请先登录");
            return false;
        }
        
        String message = CSProtocol.buildMessage(CSProtocol.SYNC_START, token, syncPath + ":" + clientId);
        String response = sendMessage(message);
        
        if (response.startsWith(CSProtocol.SUCCESS)) {
            System.out.println("[CS客户端] 同步开始成功");
            return true;
        }
        
        System.out.println("[CS客户端] 同步开始失败: " + response);
        return false;
    }
    
    /**
     * 停止同步
     */
    public boolean stopSync() {
        if (token == null) {
            System.out.println("[CS客户端] 请先登录");
            return false;
        }
        
        String message = CSProtocol.buildMessage(CSProtocol.SYNC_STOP, token, "");
        String response = sendMessage(message);
        
        if (response.startsWith(CSProtocol.SUCCESS)) {
            System.out.println("[CS客户端] 同步停止成功");
            return true;
        }
        
        System.out.println("[CS客户端] 同步停止失败: " + response);
        return false;
    }
    
    /**
     * 获取同步事件
     */
    public String getSyncEvents() {
        if (token == null) {
            return "ERROR::Please login first";
        }
        
        String message = CSProtocol.buildMessage(CSProtocol.SYNC_EVENTS, token, "");
        String response = sendMessage(message);
        
        if (response.startsWith(CSProtocol.SUCCESS)) {
            System.out.println("[CS客户端] 获取同步事件成功");
            return response;
        }
        
        System.out.println("[CS客户端] 获取同步事件失败: " + response);
        return response;
    }
    
    /**
     * 检查连接状态
     */
    public boolean isConnected() {
        return isConnected;
    }
    
    /**
     * 检查登录状态
     */
    public boolean isLoggedIn() {
        return token != null;
    }
    
    /**
     * 获取当前token
     */
    public String getToken() {
        return token;
    }
    
}
