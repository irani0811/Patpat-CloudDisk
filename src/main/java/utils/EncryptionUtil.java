package utils;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 加密工具类 - 实现AES加密
 */
@Component
public class EncryptionUtil {
    
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    private static final int KEY_LENGTH = 256;
    
    static {
        java.security.Security.addProvider(new BouncyCastleProvider());
    }
    
    /**
     * 生成AES密钥
     */
    public String generateKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
        keyGenerator.init(KEY_LENGTH);
        SecretKey key = keyGenerator.generateKey();
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }
    
    /**
     * 从字符串生成密钥
     */
    public SecretKeySpec getKeyFromString(String keyString) {
        byte[] key = Base64.getDecoder().decode(keyString);
        return new SecretKeySpec(key, ALGORITHM);
    }
    
    /**
     * 加密数据
     */
    public byte[] encrypt(byte[] data, String keyString) throws Exception {
        SecretKeySpec key = getKeyFromString(keyString);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(data);
    }
    
    /**
     * 解密数据
     */
    public byte[] decrypt(byte[] encryptedData, String keyString) throws Exception {
        SecretKeySpec key = getKeyFromString(keyString);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key);
        return cipher.doFinal(encryptedData);
    }
    
    /**
     * 加密文件
     */
    public byte[] encryptFile(byte[] fileData, String keyString) throws Exception {
        return encrypt(fileData, keyString);
    }
    
    /**
     * 解密文件
     */
    public byte[] decryptFile(byte[] encryptedData, String keyString) throws Exception {
        return decrypt(encryptedData, keyString);
    }
    
    /**
     * 生成随机IV（用于CBC模式）
     */
    public byte[] generateIV() {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        return iv;
    }
    
    /**
     * 使用CBC模式加密（更安全）
     */
    public byte[] encryptCBC(byte[] data, String keyString) throws Exception {
        SecretKeySpec key = getKeyFromString(keyString);
        byte[] iv = generateIV();
        
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new javax.crypto.spec.IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(data);
        
        // 将IV和加密数据合并
        byte[] result = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
        
        return result;
    }
    
    /**
     * 使用CBC模式解密
     */
    public byte[] decryptCBC(byte[] encryptedData, String keyString) throws Exception {
        SecretKeySpec key = getKeyFromString(keyString);
        
        // 分离IV和加密数据
        byte[] iv = new byte[16];
        byte[] encrypted = new byte[encryptedData.length - 16];
        System.arraycopy(encryptedData, 0, iv, 0, 16);
        System.arraycopy(encryptedData, 16, encrypted, 0, encrypted.length);
        
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, key, new javax.crypto.spec.IvParameterSpec(iv));
        
        return cipher.doFinal(encrypted);
    }
    
    /**
     * 默认加密方法 (使用默认密钥)
     */
    public byte[] encrypt(byte[] data) throws Exception {
        String defaultKey = "mySecretKey123456789012345678901234567890"; // 32字节密钥
        return encrypt(data, defaultKey);
    }
    
    /**
     * 默认解密方法 (使用默认密钥)
     */
    public byte[] decrypt(byte[] encryptedData) throws Exception {
        String defaultKey = "mySecretKey123456789012345678901234567890"; // 32字节密钥
        return decrypt(encryptedData, defaultKey);
    }
}

