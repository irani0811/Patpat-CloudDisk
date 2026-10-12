package utils;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.DataFormatException;

/**
 * 压缩工具类 - 实现GZIP和Zlib压缩
 */
@Component
public class CompressionUtil {
    
    private static final int BUFFER_SIZE = 1024;
    
    /**
     * 使用GZIP压缩数据
     */
    public byte[] compressGzip(byte[] data) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(baos)) {
            gzos.write(data);
            gzos.finish();
            return baos.toByteArray();
        }
    }
    
    /**
     * 使用GZIP解压数据
     */
    public byte[] decompressGzip(byte[] compressedData) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
             GzipCompressorInputStream gzis = new GzipCompressorInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            
            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = gzis.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        }
    }
    
    /**
     * 使用Zlib压缩数据
     */
    public byte[] compressZlib(byte[] data) throws IOException {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            baos.write(buffer, 0, count);
        }
        
        deflater.end();
        return baos.toByteArray();
    }
    
    /**
     * 使用Zlib解压数据
     */
    public byte[] decompressZlib(byte[] compressedData) throws IOException {
        Inflater inflater = new Inflater();
        inflater.setInput(compressedData);
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        
        try {
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                baos.write(buffer, 0, count);
            }
        } catch (DataFormatException e) {
            throw new IOException("解压数据格式错误: " + e.getMessage(), e);
        } finally {
            inflater.end();
        }
        
        return baos.toByteArray();
    }
    
    /**
     * 压缩文件
     */
    public byte[] compressFile(File file, String algorithm) throws IOException {
        byte[] fileData = java.nio.file.Files.readAllBytes(file.toPath());
        return compressFile(fileData, algorithm);
    }
    
    /**
     * 压缩字节数组
     */
    public byte[] compressFile(byte[] data, String algorithm) throws IOException {
        switch (algorithm.toUpperCase()) {
            case "GZIP":
                return compressGzip(data);
            case "ZLIB":
                return compressZlib(data);
            default:
                throw new IllegalArgumentException("Unsupported compression algorithm: " + algorithm);
        }
    }
    
    /**
     * 解压文件
     */
    public byte[] decompressFile(byte[] compressedData, String algorithm) throws IOException {
        switch (algorithm.toUpperCase()) {
            case "GZIP":
                return decompressGzip(compressedData);
            case "ZLIB":
                return decompressZlib(compressedData);
            default:
                throw new IllegalArgumentException("Unsupported compression algorithm: " + algorithm);
        }
    }
    
    /**
     * 计算压缩率
     */
    public double calculateCompressionRatio(byte[] originalData, byte[] compressedData) {
        return (double) compressedData.length / originalData.length;
    }
    
    /**
     * 默认压缩方法 (GZIP)
     */
    public byte[] compress(byte[] data) throws IOException {
        return compressGzip(data);
    }
    
    /**
     * 默认解压方法 (GZIP)
     */
    public byte[] decompress(byte[] compressedData) throws IOException {
        return decompressGzip(compressedData);
    }
}

