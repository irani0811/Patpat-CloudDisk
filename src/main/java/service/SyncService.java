package service;

import dto.SyncNotificationDTO;
import model.SyncEvent;
import model.User;
import model.FileMetadata;
import repository.SyncEventRepository;
import repository.FileMetadataRepository;
import utils.FileWatcherUtil;
import utils.DeduplicationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 同步服务类
 */
@Service
public class SyncService {
    
    @Autowired
    private SyncEventRepository syncEventRepository;
    
    @Autowired
    private FileMetadataRepository fileMetadataRepository;
    
    @Autowired
    private FileWatcherUtil fileWatcherUtil;
    
    @Autowired
    private FileService fileService;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private DeduplicationUtil deduplicationUtil;
    
    private CompletableFuture<Void> syncFuture;
    private java.util.concurrent.ScheduledExecutorService scheduler;
    private java.util.concurrent.ScheduledFuture<?> syncTask;
    private java.util.concurrent.ScheduledExecutorService renameExecutor;
    private final java.util.concurrent.ConcurrentLinkedQueue<PendingDeleteEvent> pendingDeleteEvents =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    private volatile java.nio.file.Path syncRootPath;
    private static final long RENAME_DETECTION_WINDOW_MS = 2000L;
    // 保护机制：抑制由我们本地写入（下载）引发的文件事件，避免回传到云端
    private final java.util.concurrent.ConcurrentHashMap<String, Long> suppressedUntil = new java.util.concurrent.ConcurrentHashMap<>();
    
    private static class PendingDeleteEvent {
        final String absolutePath;
        final Long userId;
        final String clientId;
        final long timestamp;
        final boolean directory;
        final String cloudPath;
        final String fileHash;
        final Long fileSize;
        final java.util.concurrent.atomic.AtomicBoolean consumed = new java.util.concurrent.atomic.AtomicBoolean(false);
        
        PendingDeleteEvent(String absolutePath,
                           Long userId,
                           String clientId,
                           boolean directory,
                           String cloudPath,
                           String fileHash,
                           Long fileSize) {
            this.absolutePath = absolutePath;
            this.userId = userId;
            this.clientId = clientId;
            this.directory = directory;
            this.cloudPath = cloudPath;
            this.fileHash = fileHash;
            this.fileSize = fileSize;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    /**
     * 开始同步文件夹
     */
    public void startSync(String syncFolderPath, Long userId, String clientId) {
        try {
            System.out.println("=== 开始同步调试信息 ===");
            System.out.println("同步路径: " + syncFolderPath);
            System.out.println("用户ID: " + userId);
            System.out.println("客户端ID: " + clientId);
            
            // 检查路径是否存在
            java.io.File syncDir = new java.io.File(syncFolderPath);
            if (!syncDir.exists()) {
                System.out.println("警告: 同步路径不存在，尝试创建: " + syncFolderPath);
                syncDir.mkdirs();
            }
            
            if (!syncDir.isDirectory()) {
                throw new RuntimeException("同步路径不是文件夹: " + syncFolderPath);
            }
            
            System.out.println("同步路径验证成功，开始监控...");
            this.syncRootPath = syncDir.toPath().toAbsolutePath().normalize();
            pendingDeleteEvents.clear();
            ensureRenameExecutor();
            
            // 启动文件监控
            fileWatcherUtil.startWatching(syncFolderPath, event -> {
                System.out.println("检测到文件变化: " + event.path() + " 类型: " + event.eventType());
                handleFileEvent(event, userId, clientId);
            });
            
            // 启动定时双向同步
            startScheduledSync(syncFolderPath, userId);
            
            System.out.println("文件监控和定时同步已启动");
            
        } catch (IOException e) {
            System.err.println("启动文件监控失败: " + e.getMessage());
            throw new RuntimeException("启动文件监控失败: " + e.getMessage());
        }
    }
    
    /**
     * 启动定时双向同步
     */
    private void startScheduledSync(String syncFolderPath, Long userId) {
        try {
            System.out.println("启动定时双向同步检查");
            
            // 创建定时任务调度器
            scheduler = java.util.concurrent.Executors.newScheduledThreadPool(1);
            
            // 每30秒执行一次双向同步检查
            syncTask = scheduler.scheduleAtFixedRate(() -> {
                try {
                    System.out.println("执行定时双向同步检查");
                    performDeltaSync(syncFolderPath, userId);
                } catch (Exception e) {
                    System.err.println("定时同步检查失败: " + e.getMessage());
                }
            }, 30, 30, java.util.concurrent.TimeUnit.SECONDS);
            
            System.out.println("定时双向同步已启动，间隔30秒");
            
        } catch (Exception e) {
            System.err.println("启动定时同步失败: " + e.getMessage());
        }
    }
    
    /**
     * 停止同步
     */
    public void stopSync() {
        try {
            // 停止文件监控
            if (fileWatcherUtil != null) {
                fileWatcherUtil.stopWatching();
            }
            
            // 停止定时同步
            if (syncTask != null) {
                syncTask.cancel(false);
                System.out.println("定时同步已停止");
            }
            
            // 关闭调度器
            if (scheduler != null) {
                scheduler.shutdown();
                System.out.println("同步调度器已关闭");
            }
            if (renameExecutor != null) {
                renameExecutor.shutdownNow();
                renameExecutor = null;
            }
            for (PendingDeleteEvent pending : pendingDeleteEvents) {
                finalizePendingDelete(pending);
            }
            pendingDeleteEvents.clear();
            syncRootPath = null;
            
            System.out.println("所有同步功能已停止");
            
        } catch (IOException e) {
            System.err.println("停止文件监控失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理文件事件
     */
    private void handleFileEvent(io.methvin.watcher.DirectoryChangeEvent event, Long userId, String clientId) {
        try {
            System.out.println("=== 处理文件事件 ===");
            System.out.println("文件路径: " + event.path());
            System.out.println("事件类型: " + event.eventType());
            System.out.println("用户ID: " + userId);
            System.out.println("客户端ID: " + clientId);
            User user = userService.getUserById(userId).orElseThrow(() -> new RuntimeException("用户不存在"));
            // 若该路径在抑制窗口内，则忽略本次事件（避免下载->监听->回传循环）
            String normalizedLocal = normalizeLocalPath(event.path().toString());
            long now = System.currentTimeMillis();
            Long until = suppressedUntil.get(normalizedLocal);
            if (until != null) {
                if (until > now) {
                    System.out.println("忽略本次事件（下载写入导致）: " + normalizedLocal);
                    return;
                } else {
                    suppressedUntil.remove(normalizedLocal);
                }
            }
            
            if (event.eventType() == io.methvin.watcher.DirectoryChangeEvent.EventType.DELETE) {
                queueDeleteEvent(event, user, clientId);
                return;
            }
            
            if (event.eventType() == io.methvin.watcher.DirectoryChangeEvent.EventType.CREATE) {
                PendingDeleteEvent renameCandidate = findRenameCandidate(event, user);
                if (renameCandidate != null) {
                    submitRenameEvent(renameCandidate, event, user, clientId);
                    return;
                }
            }
            
            SyncEvent syncEvent = new SyncEvent();
            syncEvent.setEventType(getEventType(event));
            syncEvent.setFilePath(event.path().toString());
            syncEvent.setEventTime(LocalDateTime.now());
            syncEvent.setIsProcessed(false);
            syncEvent.setClientId(clientId);
            syncEvent.setUser(user);
            
            // 如果是文件，计算哈希和大小
            if (fileWatcherUtil.isFile(event)) {
                File file = event.path().toFile();
                syncEvent.setFileSize(file.length());
                try {
                    syncEvent.setFileHash(deduplicationUtil.calculateFileHash(file));
                } catch (Exception e) {
                    System.err.println("计算文件哈希失败: " + e.getMessage());
                }
            }
            
            syncEventRepository.save(syncEvent);
            System.out.println("同步事件已保存到数据库: " + syncEvent.getId());
            
            // 异步处理同步事件
            CompletableFuture.runAsync(() -> processSyncEvent(syncEvent));
            
        } catch (Exception e) {
            System.err.println("处理文件事件失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理同步事件
     */
    private void processSyncEvent(SyncEvent syncEvent) {
        try {
            System.out.println("=== 开始处理同步事件 ===");
            System.out.println("事件类型: " + syncEvent.getEventType());
            System.out.println("文件路径: " + syncEvent.getFilePath());
            
            switch (syncEvent.getEventType()) {
                case "CREATE":
                    handleFileCreate(syncEvent);
                    break;
                case "MODIFY":
                    handleFileModify(syncEvent);
                    break;
                case "DELETE":
                    handleFileDelete(syncEvent);
                    break;
                case "MOVE":
                    handleFileMove(syncEvent);
                    break;
            }
            
            syncEvent.setIsProcessed(true);
            syncEventRepository.save(syncEvent);
            System.out.println("同步事件处理完成，已标记为已处理");
            
        } catch (Exception e) {
            System.err.println("处理同步事件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 处理文件创建 - 上传到云端
     */
    private void handleFileCreate(SyncEvent syncEvent) {
        try {
            System.out.println("=== 处理文件创建 ===");
            System.out.println("文件路径: " + syncEvent.getFilePath());
            
            // 去抖：等待文件写入稳定
            try { Thread.sleep(500); } catch (InterruptedException ie) { /* ignore */ }

            File localFile = new File(syncEvent.getFilePath());
            if (!localFile.exists()) {
                System.out.println("本地文件不存在，跳过上传: " + syncEvent.getFilePath());
                return;
            }
            
            if (localFile.isFile()) {
                // 读取本地文件
                byte[] fileData = java.nio.file.Files.readAllBytes(localFile.toPath());
                System.out.println("文件大小: " + fileData.length + " bytes");
                // 空文件先跳过，等待后续修改事件
                if (fileData.length == 0) {
                    System.out.println("空文件，跳过当前CREATE事件处理，等待后续修改");
                    return;
                }
                
                // 计算文件哈希
                String fileHash = deduplicationUtil.calculateFileHash(localFile);
                System.out.println("文件哈希: " + fileHash);
                
                // 检查云端是否已存在相同文件
                List<FileMetadata> existingFiles = fileMetadataRepository.findByFileHash(fileHash);
                if (!existingFiles.isEmpty()) {
                    System.out.println("云端已存在相同文件，创建硬链接");
                    FileMetadata existingFile = existingFiles.get(0);
                    createHardLink(existingFile, syncEvent);
                } else {
                    System.out.println("上传新文件到云端");
                    uploadNewFileToCloud(fileData, localFile.getName(), fileHash, syncEvent);
                }
            } else if (localFile.isDirectory()) {
                // 创建目录
                System.out.println("创建云端目录: " + localFile.getName());
                fileService.createDirectory(localFile.getName(), syncEvent.getUser().getId());
            }
            
            System.out.println("文件创建处理完成");
            
        } catch (Exception e) {
            System.err.println("处理文件创建失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 处理文件修改 - 更新云端文件
     */
    private void handleFileModify(SyncEvent syncEvent) {
        try {
            System.out.println("=== 处理文件修改 ===");
            System.out.println("文件路径: " + syncEvent.getFilePath());
            
            // 去抖：等待文件写入稳定
            try { Thread.sleep(500); } catch (InterruptedException ie) { /* ignore */ }

            File localFile = new File(syncEvent.getFilePath());
            if (!localFile.exists()) {
                System.out.println("本地文件不存在，跳过更新: " + syncEvent.getFilePath());
                return;
            }
            
            // 读取修改后的文件内容
            byte[] fileData = java.nio.file.Files.readAllBytes(localFile.toPath());
            System.out.println("文件大小: " + fileData.length + " bytes");
            
            // 计算新的文件哈希
            String newFileHash = deduplicationUtil.calculateFileHash(localFile);
            System.out.println("新文件哈希: " + newFileHash);
            
            // 查找云端对应的文件记录
            String fileName = localFile.getName();
            List<FileMetadata> existingFiles = fileMetadataRepository.findByFilenameAndOwner(fileName, syncEvent.getUser());
            
            if (!existingFiles.isEmpty()) {
                FileMetadata existingFile = existingFiles.get(0);
                System.out.println("找到云端文件记录: " + existingFile.getId());
                
                // 检查文件是否真的发生了变化
                if (!newFileHash.equals(existingFile.getFileHash())) {
                    System.out.println("文件内容已变化，尝试通过API执行增量同步");
                    boolean deltaOk = attemptDeltaUpload(localFile, existingFile, newFileHash);
                    if (!deltaOk) {
                        System.out.println("API增量同步不可用或失败，回退为本地重建/整文件更新");
                        updateFileInCloud(fileData, newFileHash, existingFile, syncEvent);
                    }
                } else {
                    System.out.println("文件内容未变化，跳过更新");
                }
            } else {
                System.out.println("云端未找到对应文件，作为新文件上传");
                uploadNewFileToCloud(fileData, fileName, newFileHash, syncEvent);
            }
            
            System.out.println("文件修改处理完成");
            
        } catch (Exception e) {
            System.err.println("处理文件修改失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 通过REST API执行增量同步（优先 rsync 风格滚动匹配；兼容固定分块）。成功返回true，否则false。
    private boolean attemptDeltaUpload(File localFile, FileMetadata existingFile, String newFileHash) {
        try {
            String baseUrl = System.getProperty("delta.api.base", System.getenv().getOrDefault("DELTA_API_BASE", "http://localhost:8080"));
            org.springframework.web.client.RestTemplate rt = new org.springframework.web.client.RestTemplate();
            int chunkSize = utils.ChunkingUtil.DEFAULT_CHUNK_SIZE;

            String indexUrl = baseUrl + "/api/delta/files/" + existingFile.getId() + "/chunks/index?chunkSize=" + chunkSize;
            dto.ChunkIndexResponse index = rt.getForObject(indexUrl, dto.ChunkIndexResponse.class);
            if (index == null || index.getChunks() == null) {
                System.out.println("无法获取服务器块索引，放弃API增量");
                return false;
            }

            long fileSize = localFile.length();

            // 构建服务器弱校验 -> 候选块列表 映射
            java.util.Map<Integer, java.util.List<dto.ChunkDescriptor>> weakMap = new java.util.HashMap<>();
            for (dto.ChunkDescriptor cd : index.getChunks()) {
                weakMap.computeIfAbsent(cd.getWeak32(), k -> new java.util.ArrayList<>()).add(cd);
            }

            byte[] data = java.nio.file.Files.readAllBytes(localFile.toPath());
            java.util.List<dto.DeltaOp> ops = new java.util.ArrayList<>();

            int i = 0;
            int pendingStart = 0; // 未发出的字面量起点
            if (data.length >= chunkSize) {
                utils.RollingHashUtil.Rolling roll = new utils.RollingHashUtil.Rolling(data, 0, chunkSize);
                while (i + chunkSize <= data.length) {
                    int weak = roll.value();
                    java.util.List<dto.ChunkDescriptor> cands = weakMap.get(weak);
                    boolean matched = false;
                    if (cands != null) {
                        for (dto.ChunkDescriptor cd : cands) {
                            int len = cd.getSize();
                            if (i + len <= data.length) {
                                byte[] windowHash = utils.ChunkingUtil.sha256(java.util.Arrays.copyOfRange(data, i, i + len));
                                String hex = bytesToHex(windowHash);
                                if (hex.equalsIgnoreCase(cd.getSha256())) {
                                    // 先把 [pendingStart, i) 作为 DATA 发出（若有）
                                    if (i > pendingStart) {
                                        dto.DeltaOp d = new dto.DeltaOp();
                                        d.setType(dto.DeltaOp.Type.DATA);
                                        d.setLength(i - pendingStart);
                                        d.setDataBase64(java.util.Base64.getEncoder().encodeToString(java.util.Arrays.copyOfRange(data, pendingStart, i)));
                                        ops.add(d);
                                    }
                                    // 发出 COPY
                                    dto.DeltaOp c = new dto.DeltaOp();
                                    c.setType(dto.DeltaOp.Type.COPY);
                                    c.setSrcOffset(cd.getOffset());
                                    c.setLength(len);
                                    ops.add(c);

                                    i += len;
                                    pendingStart = i;
                                    // 重置 rolling 窗口
                                    if (i + chunkSize <= data.length) {
                                        roll = new utils.RollingHashUtil.Rolling(data, i, chunkSize);
                                    }
                                    matched = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (!matched) {
                        // 滑动一个字节
                        if (i + chunkSize < data.length) {
                            roll.roll(data[i], data[i + chunkSize]);
                        }
                        i += 1;
                    }
                }
            }
            // 末尾剩余字节作为 DATA
            if (pendingStart < data.length) {
                dto.DeltaOp d = new dto.DeltaOp();
                d.setType(dto.DeltaOp.Type.DATA);
                d.setLength(data.length - pendingStart);
                d.setDataBase64(java.util.Base64.getEncoder().encodeToString(java.util.Arrays.copyOfRange(data, pendingStart, data.length)));
                ops.add(d);
            }

            long dataBytes = 0;
            for (dto.DeltaOp op : ops) if (op.getType() == dto.DeltaOp.Type.DATA) dataBytes += op.getLength();
            if (fileSize > 0 && (dataBytes * 1.0 / fileSize) > 0.6) {
                System.out.println("差异字节比例过高，放弃API增量，选择整文件");
                return false;
            }

            dto.DeltaApplyRequest req = new dto.DeltaApplyRequest();
            req.setBaseVersion(index.getVersion());
            req.setChunkSize(chunkSize);
            req.setTargetFileSize(fileSize);
            req.setTargetFileHashSha256(utils.ChunkingUtil.computeFileSHA256(localFile.toPath()));
            req.setOps(ops);

            String applyUrl = baseUrl + "/api/delta/files/" + existingFile.getId() + "/chunks/apply";
            dto.DeltaApplyResponse resp = rt.postForObject(applyUrl, req, dto.DeltaApplyResponse.class);
            if (resp == null) {
                System.out.println("应用差异失败，服务器无响应");
                return false;
            }
            System.out.println("API增量同步成功: newVersion=" + resp.getNewVersion() + ", storage=" + resp.getStoragePath());
            return true;
        } catch (Exception ex) {
            System.err.println("API增量同步异常: " + ex.getMessage());
            return false;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
    
    /**
     * 处理文件删除 - 从云端删除
     */
    private void handleFileDelete(SyncEvent syncEvent) {
        try {
            System.out.println("=== 处理文件删除 ===");
            System.out.println("文件路径: " + syncEvent.getFilePath());
            
            String fileName = new File(syncEvent.getFilePath()).getName();
            System.out.println("删除文件名: " + fileName);
            
            // 查找云端对应的文件记录
            List<FileMetadata> existingFiles = fileMetadataRepository.findByFilenameAndOwner(fileName, syncEvent.getUser());
            
            if (!existingFiles.isEmpty()) {
                FileMetadata fileToDelete = existingFiles.get(0);
                System.out.println("找到云端文件记录: " + fileToDelete.getId());
                
                // 检查是否有其他文件引用同一个存储文件
                List<FileMetadata> filesWithSameHash = fileMetadataRepository.findByFileHash(fileToDelete.getFileHash());
                
                if (filesWithSameHash.size() <= 1) {
                    // 没有其他文件引用，删除存储文件
                    System.out.println("删除存储文件: " + fileToDelete.getStoragePath());
                    String deletePath = fileToDelete.getStoragePath();
                    System.out.println("删除文件原始路径: " + deletePath);
                    String normalizedDeletePath = normalizeStoragePath(deletePath);
                    System.out.println("最终删除路径: " + normalizedDeletePath);
                    java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(normalizedDeletePath));
                } else {
                    System.out.println("有其他文件引用，只删除元数据记录");
                }
                
                // 删除数据库记录
                fileMetadataRepository.delete(fileToDelete);
                System.out.println("云端文件删除完成: " + fileName);
                
            } else {
                System.out.println("云端未找到对应文件，无需删除");
            }
            
            System.out.println("文件删除处理完成");
            
        } catch (Exception e) {
            System.err.println("处理文件删除失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 处理文件移动
     */
    private void handleFileMove(SyncEvent syncEvent) {
        try {
            System.out.println("=== 处理文件移动/重命名 ===");
            System.out.println("旧路径: " + syncEvent.getOldPath());
            System.out.println("新路径: " + syncEvent.getFilePath());
            
            String oldCloudPath = toCloudPathFromAbsolute(syncEvent.getOldPath());
            String newCloudPath = toCloudPathFromAbsolute(syncEvent.getFilePath());
            if (oldCloudPath == null || newCloudPath == null) {
                System.out.println("无法解析重命名前后的路径，跳过");
                return;
            }
            
            File newFile = new File(syncEvent.getFilePath());
            boolean isDirectory = newFile.exists() && newFile.isDirectory();
            String queryPattern = isDirectory ? oldCloudPath + "%" : oldCloudPath;
            
            List<FileMetadata> files = fileMetadataRepository.findByOwnerAndFilePathLike(
                syncEvent.getUser(), queryPattern);
            
            for (FileMetadata file : files) {
                String suffix = file.getFilePath().substring(oldCloudPath.length());
                file.setFilePath(newCloudPath + suffix);
                file.setUpdatedAt(java.time.LocalDateTime.now());
                fileMetadataRepository.save(file);
            }
            System.out.println("文件移动/重命名处理完成, 影响记录: " + files.size());
        } catch (Exception e) {
            System.err.println("处理文件移动失败: " + e.getMessage());
        }
    }

    private void queueDeleteEvent(io.methvin.watcher.DirectoryChangeEvent event, User user, String clientId) {
        ensureRenameExecutor();
        String absolutePath = event.path().toString();
        boolean isDirectory = event.path().toFile().isDirectory();
        String cloudPath = toCloudPathFromAbsolute(absolutePath);
        String fileHash = null;
        Long fileSize = null;
        if (!isDirectory && cloudPath != null) {
            java.util.Optional<FileMetadata> metadata = fileMetadataRepository.findByOwnerAndFilePath(user, cloudPath);
            if (metadata.isPresent()) {
                fileHash = metadata.get().getFileHash();
                fileSize = metadata.get().getFileSize();
            }
        }
        PendingDeleteEvent pending = new PendingDeleteEvent(
                absolutePath, user.getId(), clientId, isDirectory, cloudPath, fileHash, fileSize);
        pendingDeleteEvents.add(pending);
        renameExecutor.schedule(() -> finalizePendingDelete(pending),
                RENAME_DETECTION_WINDOW_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private PendingDeleteEvent findRenameCandidate(io.methvin.watcher.DirectoryChangeEvent event, User user) {
        File newFile = event.path().toFile();
        boolean isDirectory = newFile.isDirectory();
        long now = System.currentTimeMillis();
        for (PendingDeleteEvent pending : pendingDeleteEvents) {
            if (pending.consumed.get()) continue;
            if (!pending.userId.equals(user.getId())) continue;
            if (pending.directory != isDirectory) continue;
            if (now - pending.timestamp > RENAME_DETECTION_WINDOW_MS) continue;
            if (!pending.consumed.compareAndSet(false, true)) {
                continue;
            }
            boolean match = true;
            if (!isDirectory && newFile.exists()) {
                try {
                    if (pending.fileHash != null) {
                        String newHash = deduplicationUtil.calculateFileHash(newFile);
                        match = pending.fileHash.equals(newHash);
                    } else if (pending.fileSize != null) {
                        match = newFile.length() == pending.fileSize;
                    }
                } catch (Exception e) {
                    System.err.println("检测重命名时计算哈希失败: " + e.getMessage());
                    match = false;
                }
            }
            if (!match) {
                pending.consumed.set(false);
                continue;
            }
            pendingDeleteEvents.remove(pending);
            return pending;
        }
        return null;
    }

    private void submitRenameEvent(PendingDeleteEvent pending,
                                   io.methvin.watcher.DirectoryChangeEvent event,
                                   User user,
                                   String clientId) {
        System.out.println("检测到重命名/移动: " + pending.absolutePath + " -> " + event.path());
        SyncEvent syncEvent = new SyncEvent();
        syncEvent.setEventType("MOVE");
        syncEvent.setFilePath(event.path().toString());
        syncEvent.setOldPath(pending.absolutePath);
        syncEvent.setEventTime(LocalDateTime.now());
        syncEvent.setIsProcessed(false);
        syncEvent.setClientId(clientId);
        syncEvent.setUser(user);
        syncEventRepository.save(syncEvent);
        CompletableFuture.runAsync(() -> processSyncEvent(syncEvent));
    }

    private void finalizePendingDelete(PendingDeleteEvent pending) {
        if (!pending.consumed.compareAndSet(false, true)) {
            return;
        }
        pendingDeleteEvents.remove(pending);
        User user = userService.getUserById(pending.userId).orElse(null);
        if (user == null) {
            return;
        }
        SyncEvent syncEvent = new SyncEvent();
        syncEvent.setEventType("DELETE");
        syncEvent.setFilePath(pending.absolutePath);
        syncEvent.setEventTime(LocalDateTime.now());
        syncEvent.setIsProcessed(false);
        syncEvent.setClientId(pending.clientId);
        syncEvent.setUser(user);
        syncEventRepository.save(syncEvent);
        CompletableFuture.runAsync(() -> processSyncEvent(syncEvent));
    }

    private synchronized void ensureRenameExecutor() {
        if (renameExecutor == null || renameExecutor.isShutdown()) {
            renameExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        }
    }

    private String toCloudPathFromAbsolute(String absolutePath) {
        if (absolutePath == null) {
            return null;
        }
        String relative = getRelativePathWithinSync(absolutePath);
        if (relative == null || relative.isEmpty()) {
            return null;
        }
        String normalized = relative.replace('\\', '/');
        if (!normalized.startsWith("sync/")) {
            normalized = "sync/" + normalized;
        }
        return normalized;
    }

    private String getRelativePathWithinSync(String absolutePath) {
        if (absolutePath == null || syncRootPath == null) {
            return null;
        }
        try {
            java.nio.file.Path absolute = java.nio.file.Paths.get(absolutePath).toAbsolutePath().normalize();
            if (!absolute.startsWith(syncRootPath)) {
                return null;
            }
            java.nio.file.Path relative = syncRootPath.relativize(absolute);
            return relative.toString().replace('\\', '/');
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 获取同步事件（包括已处理的）
     */
    public List<SyncEvent> getUnprocessedEvents(Long userId) {
        System.out.println("=== 获取同步事件调试信息 ===");
        System.out.println("用户ID: " + userId);
        
        User user = userService.getUserById(userId).orElseThrow(() -> new RuntimeException("用户不存在"));
        
        // 获取所有事件（包括已处理的）
        List<SyncEvent> allEvents = syncEventRepository.findByUserOrderByEventTime(user);
        List<SyncEvent> unprocessedEvents = syncEventRepository.findUnprocessedEventsByUser(user);
        
        System.out.println("找到 " + allEvents.size() + " 个所有同步事件");
        System.out.println("找到 " + unprocessedEvents.size() + " 个未处理的同步事件");
        
        for (SyncEvent event : allEvents) {
            System.out.println("- 事件ID: " + event.getId() + ", 类型: " + event.getEventType() + ", 路径: " + event.getFilePath() + ", 已处理: " + event.getIsProcessed());
        }
        
        // 返回所有事件，让前端能看到
        return allEvents;
    }

    /**
     * 获取通知事件，可增量查询
     */
    public List<SyncNotificationDTO> getNotifications(Long userId, Long sinceId, int limit, String excludeClientId) {
        User user = userService.getUserById(userId).orElseThrow(() -> new RuntimeException("用户不存在"));
        List<SyncEvent> events;
        if (sinceId != null && sinceId > 0) {
            events = syncEventRepository.findByUserAndIdGreaterThanOrderByEventTime(user, sinceId);
        } else {
            Pageable pageable = PageRequest.of(0, Math.max(limit, 1));
            events = syncEventRepository.findRecentEvents(user, pageable);
            Collections.reverse(events);
        }
        return events.stream()
                .filter(event -> excludeClientId == null
                        || event.getClientId() == null
                        || !excludeClientId.equals(event.getClientId()))
                .map(SyncNotificationDTO::from)
                .collect(Collectors.toList());
    }
    
    /**
     * 上传新文件到云端
     */
    private void uploadNewFileToCloud(byte[] fileData, String fileName, String fileHash, SyncEvent syncEvent) {
        try {
            System.out.println("开始上传新文件到云端: " + fileName);
            
            // 创建文件元数据
            FileMetadata fileMetadata = new FileMetadata();
            fileMetadata.setFilename(fileName);
            fileMetadata.setFileSize((long) fileData.length);
            fileMetadata.setFileHash(fileHash);
            fileMetadata.setOwner(syncEvent.getUser());
            // 设置文件路径（使用事件中的路径，存为虚拟 sync/ 下的相对路径）
            String relativePath = syncEvent.getFilePath().replace('\\', '/');
            if (relativePath.contains("/sync/")) {
                relativePath = relativePath.substring(relativePath.indexOf("/sync/") + 6);
            }
            if (!relativePath.startsWith("sync/")) {
                relativePath = "sync/" + relativePath;
            }
            fileMetadata.setFilePath(relativePath);
            // 保存文件到storage目录
            String storagePath = "storage/" + fileHash + ".dat";
            fileMetadata.setStoragePath(storagePath);
            fileMetadata.setMimeType(getMimeTypeFromFilename(fileName));
            fileMetadata.setCreatedAt(java.time.LocalDateTime.now());
            fileMetadata.setUpdatedAt(java.time.LocalDateTime.now());
            
            System.out.println("保存文件到: " + storagePath);
            java.nio.file.Files.write(java.nio.file.Paths.get(storagePath), fileData);
            
            // 保存元数据到数据库
            fileMetadataRepository.save(fileMetadata);
            
            System.out.println("文件上传完成: " + fileName + " -> " + storagePath);
            
        } catch (Exception e) {
            System.err.println("上传文件到云端失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 创建硬链接
     */
    private void createHardLink(FileMetadata existingFile, SyncEvent syncEvent) {
        try {
            System.out.println("创建硬链接: " + existingFile.getFilename());
            
            // 创建新的文件元数据，指向相同的存储文件
            FileMetadata newFileMetadata = new FileMetadata();
            newFileMetadata.setFilename(new java.io.File(syncEvent.getFilePath()).getName());
            newFileMetadata.setFileSize(existingFile.getFileSize());
            newFileMetadata.setFileHash(existingFile.getFileHash());
            newFileMetadata.setOwner(syncEvent.getUser());
            newFileMetadata.setStoragePath(existingFile.getStoragePath());
            newFileMetadata.setMimeType(existingFile.getMimeType());
            newFileMetadata.setCreatedAt(java.time.LocalDateTime.now());
            newFileMetadata.setUpdatedAt(java.time.LocalDateTime.now());
            // 同步设置 filePath 到虚拟 sync/ 路径
            String rel = syncEvent.getFilePath().replace('\\', '/');
            if (rel.contains("/sync/")) {
                rel = rel.substring(rel.indexOf("/sync/") + 6);
            }
            if (!rel.startsWith("sync/")) {
                rel = "sync/" + rel;
            }
            newFileMetadata.setFilePath(rel);
            
            // 保存到数据库
            fileMetadataRepository.save(newFileMetadata);
            
            System.out.println("硬链接创建完成: " + newFileMetadata.getFilename());
            
        } catch (Exception e) {
            System.err.println("创建硬链接失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 更新云端文件
     */
    private void updateFileInCloud(byte[] fileData, String newFileHash, FileMetadata existingFile, SyncEvent syncEvent) {
        try {
            System.out.println("开始更新云端文件: " + existingFile.getFilename());
            
            // 冲突检测
            if (hasFileConflict(existingFile, syncEvent)) {
                System.out.println("检测到文件冲突，创建冲突版本");
                handleFileConflict(existingFile, fileData, newFileHash, syncEvent);
                return;
            }

            // 目标存储路径
            String newStoragePath = newFileHash + ".dat";
            String fullNewStoragePath = "storage/" + newStoragePath;

            // 基础文件（旧版本）路径
            String basePath = normalizeStoragePath(existingFile.getStoragePath());
            java.nio.file.Path base = java.nio.file.Paths.get(basePath);
            java.nio.file.Path local = java.nio.file.Paths.get(syncEvent.getFilePath());
            java.nio.file.Path target = java.nio.file.Paths.get(fullNewStoragePath);

            // 确保存储目录存在
            java.nio.file.Files.createDirectories(target.getParent());

            boolean usedDelta = false;
            try {
                if (java.nio.file.Files.exists(base) && java.nio.file.Files.exists(local)) {
                    System.out.println("使用32KB分块进行增量重建...");
                    java.nio.file.Path storageDir = java.nio.file.Paths.get("storage");
                    java.nio.file.Files.createDirectories(storageDir);
                    java.nio.file.Path tmp = java.nio.file.Files.createTempFile(storageDir, "delta_rebuild_", ".dat");
                    utils.DeltaSyncUtil.DiffResult dr = utils.DeltaSyncUtil.rebuildWithDelta(base, local, tmp, utils.ChunkingUtil.DEFAULT_CHUNK_SIZE);
                    System.out.println("增量重建完成: 总块=" + dr.totalChunks + ", 修改块=" + dr.changedChunks);
                    // 移动到目标路径（优先原子移动，失败降级）
                    try {
                        java.nio.file.Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                    } catch (java.nio.file.AtomicMoveNotSupportedException e1) {
                        java.nio.file.Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (java.nio.file.AccessDeniedException e2) {
                        java.nio.file.Files.copy(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        try { java.nio.file.Files.deleteIfExists(tmp); } catch (Exception ignore) {}
                    }
                    usedDelta = true;
                }
            } catch (Exception dx) {
                System.err.println("增量重建失败，回退整文件写入: " + dx.getMessage());
            }

            if (!usedDelta) {
                // 回退整文件写入
                System.out.println("整文件写入到: " + fullNewStoragePath);
                java.nio.file.Files.write(target, fileData);
            }

            // 记录旧hash与旧路径用于后续清理
            String oldHash = existingFile.getFileHash();
            String oldPath = basePath;

            // 更新文件元数据（以新文件大小为准）
            long newSize = java.nio.file.Files.size(target);
            existingFile.setFileSize(newSize);
            existingFile.setFileHash(newFileHash);
            existingFile.setStoragePath(fullNewStoragePath);
            existingFile.setUpdatedAt(java.time.LocalDateTime.now());

            // 保存数据库
            fileMetadataRepository.save(existingFile);

            // 旧存储文件清理（仅当无其他文件仍引用旧hash）
            try {
                if (oldHash != null && oldPath != null && !oldPath.equals(fullNewStoragePath)) {
                    List<FileMetadata> refs = fileMetadataRepository.findByFileHash(oldHash);
                    if (refs == null || refs.isEmpty()) {
                        java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(oldPath));
                    }
                }
            } catch (Exception ignore) {}

            System.out.println("云端文件更新完成: " + existingFile.getFilename() + " -> " + newStoragePath + (usedDelta ? " (delta)" : " (full)"));

        } catch (Exception e) {
            System.err.println("更新云端文件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 检查文件冲突
     */
    private boolean hasFileConflict(FileMetadata existingFile, SyncEvent syncEvent) {
        // 仅当云端文件更新时间晚于当前事件时间，才认为发生冲突
        return existingFile.getUpdatedAt() != null && syncEvent.getEventTime() != null
            && existingFile.getUpdatedAt().isAfter(syncEvent.getEventTime());
    }
    
    /**
     * 处理文件冲突
     */
    private void handleFileConflict(FileMetadata existingFile, byte[] newFileData, String newFileHash, SyncEvent syncEvent) {
        try {
            System.out.println("=== 处理文件冲突 ===");
            System.out.println("冲突文件: " + existingFile.getFilename());
            
            // 创建冲突版本的文件名
            String conflictFileName = generateConflictFileName(existingFile.getFilename());
            System.out.println("冲突文件名: " + conflictFileName);
            
            // 保存冲突版本到云端
            String conflictStoragePath = newFileHash + "_conflict.dat";
            java.nio.file.Files.write(java.nio.file.Paths.get("storage/" + conflictStoragePath), newFileData);
            
            // 创建冲突文件的元数据
            FileMetadata conflictFile = new FileMetadata();
            conflictFile.setFilename(conflictFileName);
            conflictFile.setFileSize((long) newFileData.length);
            conflictFile.setFileHash(newFileHash);
            conflictFile.setOwner(syncEvent.getUser());
            conflictFile.setStoragePath(conflictStoragePath);
            conflictFile.setMimeType(existingFile.getMimeType());
            conflictFile.setCreatedAt(java.time.LocalDateTime.now());
            conflictFile.setUpdatedAt(java.time.LocalDateTime.now());
            // 冲突文件沿用原文件的虚拟路径（file_path）
            conflictFile.setFilePath(existingFile.getFilePath());
            
            // 保存冲突文件到数据库
            fileMetadataRepository.save(conflictFile);
            
            System.out.println("文件冲突处理完成: " + conflictFileName);
            
        } catch (Exception e) {
            System.err.println("处理文件冲突失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 生成冲突文件名
     */
    private String generateConflictFileName(String originalFileName) {
        String nameWithoutExt = originalFileName.substring(0, originalFileName.lastIndexOf('.'));
        String extension = originalFileName.substring(originalFileName.lastIndexOf('.'));
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return nameWithoutExt + "_conflict_" + timestamp + extension;
    }
    
    /**
     * 根据文件名获取MIME类型
     */
    private String getMimeTypeFromFilename(String filename) {
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        switch (extension) {
            case "txt": return "text/plain";
            case "html": return "text/html";
            case "css": return "text/css";
            case "js": return "application/javascript";
            case "json": return "application/json";
            case "xml": return "application/xml";
            case "pdf": return "application/pdf";
            case "doc": return "application/msword";
            case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls": return "application/vnd.ms-excel";
            case "xlsx": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt": return "application/vnd.ms-powerpoint";
            case "pptx": return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "jpg": case "jpeg": return "image/jpeg";
            case "png": return "image/png";
            case "gif": return "image/gif";
            case "bmp": return "image/bmp";
            case "svg": return "image/svg+xml";
            case "mp3": return "audio/mpeg";
            case "mp4": return "video/mp4";
            case "avi": return "video/x-msvideo";
            case "zip": return "application/zip";
            case "rar": return "application/x-rar-compressed";
            case "7z": return "application/x-7z-compressed";
            default: return "application/octet-stream";
        }
    }
    
    /**
     * 规范化存储路径，兼容以下情况：
     * - Windows 路径分隔符 "\\" -> "/"
     * - 去掉开头的"./"
     * - 如果包含重复或缺失的 storage 前缀，保证最终以 "storage/" 开头
     * - 返回当前操作系统规范化后的路径字符串
     */
    private String normalizeStoragePath(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return rawPath;
        }
        String s = rawPath.trim().replace('\\', '/');
        if (s.startsWith("./")) {
            s = s.substring(2);
        }
        if (!s.startsWith("storage/")) {
            int idx = s.indexOf("storage/");
            if (idx >= 0) {
                s = s.substring(idx);
            } else {
                s = "storage/" + s;
            }
        }
        return java.nio.file.Paths.get(s).normalize().toString();
    }
    
    // 规范化本地文件路径（用于事件抑制）
    private String normalizeLocalPath(String raw) {
        if (raw == null || raw.isEmpty()) return raw;
        return java.nio.file.Paths.get(raw).toAbsolutePath().normalize().toString();
    }
    
    /**
     * 获取事件类型
     */
    private String getEventType(io.methvin.watcher.DirectoryChangeEvent event) {
        switch (event.eventType()) {
            case CREATE:
                return "CREATE";
            case MODIFY:
                return "MODIFY";
            case DELETE:
                return "DELETE";
            case OVERFLOW:
                return "OVERFLOW";
            default:
                return "UNKNOWN";
        }
    }
    
    /**
     * 实现差分同步
     */
    public void performDeltaSync(String filePath, Long userId) {
        try {
            System.out.println("=== 执行双向差分同步 ===");
            System.out.println("文件路径: " + filePath);
            
            User user = userService.getUserById(userId).orElseThrow(() -> new RuntimeException("用户不存在"));
            
            // 获取云端所有文件并限定在虚拟 sync/ 目录下
            List<FileMetadata> allFiles = fileMetadataRepository.findByOwner(user);
            java.util.List<FileMetadata> cloudFiles = new java.util.ArrayList<>();
            for (FileMetadata fm : allFiles) {
                if (fm.getFilePath() != null && fm.getIsDirectory() != Boolean.TRUE && fm.getFilePath().startsWith("sync/")) {
                    cloudFiles.add(fm);
                }
            }
            System.out.println("云端文件数量: " + cloudFiles.size());
            
            // 检查本地文件
            File localFile = new File(filePath);
            if (!localFile.exists()) {
                System.out.println("本地文件不存在，从云端下载");
                downloadFileFromCloud(cloudFiles, filePath, user);
                return;
            }
            
            // 比较本地和云端文件
            for (FileMetadata cloudFile : cloudFiles) {
                // 从 file_path 提取 sync/ 下的相对路径
                String virtualPath = cloudFile.getFilePath() == null ? cloudFile.getFilename() : cloudFile.getFilePath();
                String relUnderSync = virtualPath.startsWith("sync/") ? virtualPath.substring(5) : virtualPath;
                String localFilePath = java.nio.file.Paths.get(filePath, relUnderSync).toString();
                File localFileToCheck = new File(localFilePath);
                
                if (!localFileToCheck.exists()) {
                    // 本地不存在，从云端下载
                    System.out.println("下载云端文件到本地: " + cloudFile.getFilename());
                    downloadSingleFileFromCloud(cloudFile, localFilePath);
                } else {
                    // 检查文件是否一致
                    if (!isFileConsistent(localFileToCheck, cloudFile)) {
                        System.out.println("文件不一致，更新本地文件: " + cloudFile.getFilename());
                        downloadSingleFileFromCloud(cloudFile, localFilePath);
                    }
                }
            }
            
            System.out.println("双向差分同步完成");
            
        } catch (Exception e) {
            System.err.println("差分同步失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 从云端下载文件到本地（使用指定根目录）
     */
    private void downloadFileFromCloud(List<FileMetadata> cloudFiles, String rootPath, User user) {
        try {
            for (FileMetadata cloudFile : cloudFiles) {
                String virtualPath = cloudFile.getFilePath() == null ? cloudFile.getFilename() : cloudFile.getFilePath();
                String relUnderSync = virtualPath.startsWith("sync/") ? virtualPath.substring(5) : virtualPath;
                String localFilePath = java.nio.file.Paths.get(rootPath, relUnderSync).toString();
                downloadSingleFileFromCloud(cloudFile, localFilePath);
            }
        } catch (Exception e) {
            System.err.println("从云端下载文件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 下载单个文件从云端到本地
     */
    private void downloadSingleFileFromCloud(FileMetadata cloudFile, String localFilePath) {
        try {
            System.out.println("下载文件: " + cloudFile.getFilename() + " -> " + localFilePath);
            
            // 读取云端文件（规范化存储路径）
            String fullStoragePath = cloudFile.getStoragePath();
            System.out.println("原始存储路径: " + fullStoragePath);
            String normalizedStoragePath = normalizeStoragePath(fullStoragePath);
            System.out.println("最终使用的路径: " + normalizedStoragePath);
            byte[] fileData = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(normalizedStoragePath));
            
            // 创建本地目录（如果不存在）
            File localFile = new File(localFilePath);
            if (localFile.getParentFile() != null) {
                localFile.getParentFile().mkdirs();
            }
            // 写入前标记抑制该文件路径的事件，窗口2秒
            String normalizedLocal = normalizeLocalPath(localFile.getPath());
            suppressedUntil.put(normalizedLocal, System.currentTimeMillis() + 2000);
            
            // 写入本地文件
            java.nio.file.Files.write(localFile.toPath(), fileData);
            
            System.out.println("文件下载完成: " + cloudFile.getFilename());
            
        } catch (Exception e) {
            System.err.println("下载单个文件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 检查文件是否一致
     */
    private boolean isFileConsistent(File localFile, FileMetadata cloudFile) {
        try {
            if (localFile.length() != cloudFile.getFileSize()) {
                return false;
            }
            
            // 计算本地文件哈希
            String localHash = deduplicationUtil.calculateFileHash(localFile);
            return localHash.equals(cloudFile.getFileHash());
            
        } catch (Exception e) {
            System.err.println("检查文件一致性失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 记录来自客户端的云端操作，方便通知其他客户端
     */
    public void recordClientOperation(Long userId,
                                      String clientId,
                                      String eventType,
                                      String filePath,
                                      Long fileSize,
                                      String fileHash) {
        User user = userService.getUserById(userId).orElseThrow(() -> new RuntimeException("用户不存在"));
        SyncEvent event = new SyncEvent();
        event.setEventType(eventType);
        event.setFilePath(filePath);
        event.setFileSize(fileSize);
        event.setFileHash(fileHash);
        event.setClientId(clientId);
        event.setIsProcessed(false);
        event.setUser(user);
        syncEventRepository.save(event);
    }
}
