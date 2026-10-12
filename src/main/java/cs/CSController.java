package cs;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * CS控制器
 * 提供CS架构的REST API接口（用于管理CS服务）
 */
@RestController
@RequestMapping("/api/cs")
@CrossOrigin(origins = "*")
public class CSController {
    
    @Autowired
    private CSSocketServer csSocketServer;
    
    /**
     * 启动CS服务器
     */
    @PostMapping("/start")
    public Map<String, Object> startCSServer() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!csSocketServer.isRunning()) {
                csSocketServer.start();
                response.put("success", true);
                response.put("message", "CS服务器启动成功");
                response.put("port", 8889);
            } else {
                response.put("success", false);
                response.put("message", "CS服务器已在运行");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "CS服务器启动失败: " + e.getMessage());
        }
        
        return response;
    }
    
    /**
     * 停止CS服务器
     */
    @PostMapping("/stop")
    public Map<String, Object> stopCSServer() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (csSocketServer.isRunning()) {
                csSocketServer.stop();
                response.put("success", true);
                response.put("message", "CS服务器停止成功");
            } else {
                response.put("success", false);
                response.put("message", "CS服务器未运行");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "CS服务器停止失败: " + e.getMessage());
        }
        
        return response;
    }
    
    /**
     * 获取CS服务器状态
     */
    @GetMapping("/status")
    public Map<String, Object> getCSServerStatus() {
        Map<String, Object> response = new HashMap<>();
        
        response.put("success", true);
        response.put("isRunning", csSocketServer.isRunning());
        response.put("connectedClients", csSocketServer.getConnectedClientCount());
        response.put("port", 8889);
        
        if (csSocketServer.isRunning()) {
            response.put("message", "CS服务器运行正常");
            response.put("clientInfo", csSocketServer.getClientInfo());
        } else {
            response.put("message", "CS服务器未运行");
        }
        
        return response;
    }
    
    /**
     * 重启CS服务器
     */
    @PostMapping("/restart")
    public Map<String, Object> restartCSServer() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (csSocketServer.isRunning()) {
                csSocketServer.stop();
                Thread.sleep(1000); // 等待1秒
            }
            
            csSocketServer.start();
            response.put("success", true);
            response.put("message", "CS服务器重启成功");
            response.put("port", 8889);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "CS服务器重启失败: " + e.getMessage());
        }
        
        return response;
    }
}
