package utils;

import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

/**
 * 去重工具类 - 实现文件级和块级去重
 */
@Component
public class DeduplicationUtil {
    
    private static final int CHUNK_SIZE = 4 * 1024 * 1024; // 4MB chunks
    private static final String HASH_ALGORITHM = "SHA-256";
    
    /**
     * 计算文件MD5哈希
     */
    public String calculateFileHash(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
        }
        return bytesToHex(md.digest());
    }
    
    /**
     * 计算字节数组的MD5哈希
     */
    public String calculateHash(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        return bytesToHex(md.digest(data));
    }
    
    /**
     * 计算字节数组的SHA-256哈希
     */
    public String calculateSHA256(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return bytesToHex(md.digest(data));
    }
    
    /**
     * 将文件分割成块并计算每个块的哈希
     */
    // DeduplicationUtil.java
    public List<ChunkInfo> calculateChunkHashes(byte[] data) throws NoSuchAlgorithmException {
        List<ChunkInfo> chunks = new ArrayList<>();
        MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);

        int offset = 0;
        int index = 0;
        while (offset < data.length) {
            int len = Math.min(CHUNK_SIZE, data.length - offset);
            byte[] chunkData = Arrays.copyOfRange(data, offset, offset + len);

            String chunkHash = bytesToHex(md.digest(chunkData));
            chunks.add(new ChunkInfo(index++, chunkHash, chunkData, len));
            md.reset();
            offset += len;
        }
        return chunks;
    }

        /**
     * 兼容旧代码：从 File 计算块哈希
     * 内部还是复用 byte[] 版本的实现
     */
    public List<ChunkInfo> calculateChunkHashes(File file)
            throws IOException, NoSuchAlgorithmException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] allBytes = fis.readAllBytes();
            return calculateChunkHashes(allBytes);
        }
    }

    
    /**
     * 使用滚动哈希算法计算块哈希（用于差分同步）
     */
    public List<RollingHashChunk> calculateRollingHashes(File file) throws IOException {
        List<RollingHashChunk> chunks = new ArrayList<>();
        
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[CHUNK_SIZE];
            int chunkIndex = 0;
            int bytesRead;
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] chunkData = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunkData, 0, bytesRead);
                
                long rollingHash = calculateRollingHash(chunkData);
                chunks.add(new RollingHashChunk(chunkIndex, rollingHash, chunkData, bytesRead));
                chunkIndex++;
            }
        }
        
        return chunks;
    }
    
    /**
     * 计算滚动哈希
     */
    private long calculateRollingHash(byte[] data) {
        long hash = 0;
        long base = 256;
        long mod = 1000000007; // Large prime number
        
        for (byte b : data) {
            hash = (hash * base + (b & 0xFF)) % mod;
        }
        
        return hash;
    }
    
    /**
     * 查找文件差异（使用滚动哈希）
     */
    public List<DeltaInfo> findDifferences(List<RollingHashChunk> oldChunks, List<RollingHashChunk> newChunks) {
        List<DeltaInfo> deltas = new ArrayList<>();
        
        // 创建旧文件的哈希映射
        java.util.Map<Long, RollingHashChunk> oldHashMap = new java.util.HashMap<>();
        for (RollingHashChunk chunk : oldChunks) {
            oldHashMap.put(chunk.getRollingHash(), chunk);
        }
        
        // 检查新文件的每个块
        for (RollingHashChunk newChunk : newChunks) {
            if (!oldHashMap.containsKey(newChunk.getRollingHash())) {
                // 这是一个新的或修改的块
                deltas.add(new DeltaInfo(newChunk.getChunkIndex(), DeltaType.ADD, newChunk.getData()));
            }
        }
        
        // 检查旧文件中是否还有块在新文件中
        for (RollingHashChunk oldChunk : oldChunks) {
            boolean found = false;
            for (RollingHashChunk newChunk : newChunks) {
                if (oldChunk.getRollingHash().equals(newChunk.getRollingHash())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                deltas.add(new DeltaInfo(oldChunk.getChunkIndex(), DeltaType.REMOVE, null));
            }
        }
        
        return deltas;
    }
    
    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    /**
     * 块信息类
     */
    public static class ChunkInfo {
        private int chunkIndex;
        private String chunkHash;
        private byte[] data;
        private int size;
        
        public ChunkInfo(int chunkIndex, String chunkHash, byte[] data, int size) {
            this.chunkIndex = chunkIndex;
            this.chunkHash = chunkHash;
            this.data = data;
            this.size = size;
        }
        
        // Getters and setters
        public int getChunkIndex() { return chunkIndex; }
        public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }
        
        public String getChunkHash() { return chunkHash; }
        public void setChunkHash(String chunkHash) { this.chunkHash = chunkHash; }
        
        public byte[] getData() { return data; }
        public void setData(byte[] data) { this.data = data; }
        
        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
    }
    
    /**
     * 滚动哈希块信息类
     */
    public static class RollingHashChunk {
        private int chunkIndex;
        private Long rollingHash;
        private byte[] data;
        private int size;
        
        public RollingHashChunk(int chunkIndex, Long rollingHash, byte[] data, int size) {
            this.chunkIndex = chunkIndex;
            this.rollingHash = rollingHash;
            this.data = data;
            this.size = size;
        }
        
        // Getters and setters
        public int getChunkIndex() { return chunkIndex; }
        public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }
        
        public Long getRollingHash() { return rollingHash; }
        public void setRollingHash(Long rollingHash) { this.rollingHash = rollingHash; }
        
        public byte[] getData() { return data; }
        public void setData(byte[] data) { this.data = data; }
        
        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
    }
    
    /**
     * 差异信息类
     */
    public static class DeltaInfo {
        private int chunkIndex;
        private DeltaType type;
        private byte[] data;
        
        public DeltaInfo(int chunkIndex, DeltaType type, byte[] data) {
            this.chunkIndex = chunkIndex;
            this.type = type;
            this.data = data;
        }
        
        // Getters and setters
        public int getChunkIndex() { return chunkIndex; }
        public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }
        
        public DeltaType getType() { return type; }
        public void setType(DeltaType type) { this.type = type; }
        
        public byte[] getData() { return data; }
        public void setData(byte[] data) { this.data = data; }
    }
    
    /**
     * 差异类型枚举
     */
    public enum DeltaType {
        ADD,    // 新增
        REMOVE, // 删除
        MODIFY  // 修改
    }
}

