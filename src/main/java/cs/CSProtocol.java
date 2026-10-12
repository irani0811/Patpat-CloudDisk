package cs;

import java.util.HashMap;
import java.util.Map;

/**
 * CS协议定义
 * 定义客户端和服务端之间的通信协议
 */
public class CSProtocol {
    
    // 消息类型常量
    public static final String LOGIN = "LOGIN";
    public static final String LOGOUT = "LOGOUT";
    public static final String UPLOAD = "UPLOAD";
    public static final String DOWNLOAD = "DOWNLOAD";
    public static final String LIST = "LIST";
    public static final String DELETE = "DELETE";
    public static final String SYNC_START = "SYNC_START";
    public static final String SYNC_STOP = "SYNC_STOP";
    public static final String SYNC_EVENTS = "SYNC_EVENTS";
    
    // 响应状态
    public static final String SUCCESS = "SUCCESS";
    public static final String ERROR = "ERROR";
    public static final String AUTH_REQUIRED = "AUTH_REQUIRED";
    
    /**
     * 解析CS协议消息
     */
    public static CSMessage parseMessage(String message) {
        String[] parts = message.split("::", 3);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid message format");
        }
        
        CSMessage csMessage = new CSMessage();
        csMessage.setType(parts[0]);
        csMessage.setToken(parts[1]);
        if (parts.length > 2) {
            csMessage.setData(parts[2]);
        }
        
        return csMessage;
    }
    
    /**
     * 构建CS协议消息
     */
    public static String buildMessage(String type, String token, String data) {
        if (data == null || data.isEmpty()) {
            return type + "::" + token;
        }
        return type + "::" + token + "::" + data;
    }
    
    /**
     * 构建响应消息
     */
    public static String buildResponse(String status, String data) {
        if (data == null || data.isEmpty()) {
            return status;
        }
        return status + "::" + data;
    }
    
    /**
     * CS消息类
     */
    public static class CSMessage {
        private String type;
        private String token;
        private String data;
        private Map<String, String> params = new HashMap<>();
        
        // Getters and Setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        
        public String getData() { return data; }
        public void setData(String data) { this.data = data; }
        
        public Map<String, String> getParams() { return params; }
        public void setParams(Map<String, String> params) { this.params = params; }
        
        public void addParam(String key, String value) {
            this.params.put(key, value);
        }
        
        public String getParam(String key) {
            return this.params.get(key);
        }
    }
}
