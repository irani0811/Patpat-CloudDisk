package utils;

import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryWatcher;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 文件监控工具类
 */
@Component
public class FileWatcherUtil {
    
    private DirectoryWatcher watcher;
    private CompletableFuture<Void> watchFuture;
    
    /**
     * 开始监控目录
     */
    public void startWatching(String directoryPath, Consumer<DirectoryChangeEvent> eventHandler) throws IOException {
        Path path = Paths.get(directoryPath);
        
        watcher = DirectoryWatcher.builder()
                .path(path)
                .listener(event -> {
                    try {
                        eventHandler.accept(event);
                    } catch (Exception e) {
                        System.err.println("Error handling file event: " + e.getMessage());
                    }
                })
                .build();
        
        watchFuture = watcher.watchAsync();
    }
    
    /**
     * 停止监控
     */
    public void stopWatching() throws IOException {
        if (watcher != null) {
            watcher.close();
        }
        if (watchFuture != null) {
            watchFuture.cancel(true);
        }
    }
    
    /**
     * 检查是否正在监控
     */
    public boolean isWatching() {
        return watcher != null && !watchFuture.isDone();
    }
    
    /**
     * 获取事件类型描述
     */
    public String getEventTypeDescription(DirectoryChangeEvent event) {
        switch (event.eventType()) {
            case CREATE:
                return "文件创建";
            case MODIFY:
                return "文件修改";
            case DELETE:
                return "文件删除";
            case OVERFLOW:
                return "事件溢出";
            default:
                return "未知事件";
        }
    }
    
    /**
     * 检查是否为目录
     */
    public boolean isDirectory(DirectoryChangeEvent event) {
        return event.path().toFile().isDirectory();
    }
    
    /**
     * 检查是否为文件
     */
    public boolean isFile(DirectoryChangeEvent event) {
        return event.path().toFile().isFile();
    }
    
    /**
     * 获取相对路径
     */
    public String getRelativePath(Path watchPath, Path eventPath) {
        return watchPath.relativize(eventPath).toString();
    }
}

