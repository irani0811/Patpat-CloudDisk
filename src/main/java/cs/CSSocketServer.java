package cs;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CS Socket服务器
 * 处理CS客户端的Socket连接
 */
@Component
public class CSSocketServer {
    
    @Autowired
    private CSMessageHandler messageHandler;
    
    private ServerSocketChannel serverSocketChannel;
    private Selector selector;
    private ExecutorService executorService;
    private boolean isRunning = false;
    private int port = 8889; // CS服务端口，与BS的8080端口分开
    
    // 客户端连接管理
    private Map<SocketChannel, String> clientTokens = new HashMap<>();
    private Map<SocketChannel, String> clientInfo = new HashMap<>();
    
    /**
     * 启动CS Socket服务器
     */
    public void start() {
        try {
            // 创建服务器Socket通道
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.bind(new InetSocketAddress(port));
            
            // 创建选择器
            selector = Selector.open();
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            
            // 创建线程池
            executorService = Executors.newFixedThreadPool(10);
            
            isRunning = true;
            
            System.out.println("[CS服务器] CS Socket服务器已启动，端口: " + port);
            
            // 启动服务器循环
            startServerLoop();
            
        } catch (IOException e) {
            System.err.println("[CS服务器] 启动失败: " + e.getMessage());
            throw new RuntimeException("CS服务器启动失败", e);
        }
    }
    
    /**
     * 停止CS Socket服务器
     */
    public void stop() {
        isRunning = false;
        
        try {
            if (selector != null) {
                selector.close();
            }
            if (serverSocketChannel != null) {
                serverSocketChannel.close();
            }
            if (executorService != null) {
                executorService.shutdown();
            }
            
            System.out.println("[CS服务器] CS Socket服务器已停止");
        } catch (IOException e) {
            System.err.println("[CS服务器] 停止失败: " + e.getMessage());
        }
    }
    
    /**
     * 服务器主循环
     */
    private void startServerLoop() {
        new Thread(() -> {
            while (isRunning) {
                try {
                    int readyChannels = selector.select();
                    if (readyChannels == 0) continue;
                    
                    var selectedKeys = selector.selectedKeys();
                    var keyIterator = selectedKeys.iterator();
                    
                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();
                        keyIterator.remove();
                        
                        if (!key.isValid()) continue;
                        
                        if (key.isAcceptable()) {
                            handleAccept(key);
                        } else if (key.isReadable()) {
                            handleRead(key);
                        }
                    }
                } catch (IOException e) {
                    System.err.println("[CS服务器] 服务器循环错误: " + e.getMessage());
                }
            }
        }).start();
    }
    
    /**
     * 处理客户端连接
     */
    private void handleAccept(SelectionKey key) {
        try {
            ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
            SocketChannel clientChannel = serverChannel.accept();
            clientChannel.configureBlocking(false);
            clientChannel.register(selector, SelectionKey.OP_READ);
            
            String clientInfo = clientChannel.getRemoteAddress().toString();
            this.clientInfo.put(clientChannel, clientInfo);
            
            System.out.println("[CS服务器] 新客户端连接: " + clientInfo);
            
        } catch (IOException e) {
            System.err.println("[CS服务器] 处理客户端连接失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理客户端消息
     */
    private void handleRead(SelectionKey key) {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        
        try {
            // 读取消息长度
            ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
            int bytesRead = clientChannel.read(lengthBuffer);
            
            if (bytesRead == -1) {
                // 客户端断开连接
                handleClientDisconnect(clientChannel);
                return;
            }
            
            if (bytesRead < 4) {
                return; // 数据不完整，等待下次读取
            }
            
            lengthBuffer.flip();
            int messageLength = lengthBuffer.getInt();
            
            // 读取消息内容
            ByteBuffer messageBuffer = ByteBuffer.allocate(messageLength);
            int totalRead = 0;
            
            while (totalRead < messageLength) {
                int read = clientChannel.read(messageBuffer);
                if (read == -1) {
                    handleClientDisconnect(clientChannel);
                    return;
                }
                totalRead += read;
            }
            
            // 处理消息
            messageBuffer.flip();
            String message = new String(messageBuffer.array());
            
            System.out.println("[CS服务器] 收到消息: " + message);
            
            // 异步处理消息
            executorService.submit(() -> {
                try {
                    String response = messageHandler.handleMessage(clientChannel, message);
                    sendResponse(clientChannel, response);
                } catch (Exception e) {
                    System.err.println("[CS服务器] 处理消息失败: " + e.getMessage());
                    sendResponse(clientChannel, "ERROR::Message processing failed");
                }
            });
            
        } catch (IOException e) {
            System.err.println("[CS服务器] 读取客户端消息失败: " + e.getMessage());
            handleClientDisconnect(clientChannel);
        }
    }
    
    /**
     * 发送响应给客户端
     */
    private void sendResponse(SocketChannel clientChannel, String response) {
        try {
            byte[] responseBytes = response.getBytes();
            ByteBuffer buffer = ByteBuffer.allocate(4 + responseBytes.length);
            buffer.putInt(responseBytes.length);
            buffer.put(responseBytes);
            buffer.flip();
            
            while (buffer.hasRemaining()) {
                clientChannel.write(buffer);
            }
            
            System.out.println("[CS服务器] 发送响应: " + response);
            
        } catch (IOException e) {
            System.err.println("[CS服务器] 发送响应失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理客户端断开连接
     */
    private void handleClientDisconnect(SocketChannel clientChannel) {
        try {
            String clientInfo = this.clientInfo.get(clientChannel);
            System.out.println("[CS服务器] 客户端断开连接: " + clientInfo);
            
            this.clientInfo.remove(clientChannel);
            this.clientTokens.remove(clientChannel);
            
            clientChannel.close();
        } catch (IOException e) {
            System.err.println("[CS服务器] 处理客户端断开连接失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取连接状态
     */
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * 获取连接客户端数量
     */
    public int getConnectedClientCount() {
        return clientInfo.size();
    }
    
    /**
     * 获取客户端信息
     */
    public Map<SocketChannel, String> getClientInfo() {
        return new HashMap<>(clientInfo);
    }
}
