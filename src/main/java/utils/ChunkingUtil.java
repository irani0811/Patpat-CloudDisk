package utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class ChunkingUtil {

    public static final int DEFAULT_CHUNK_SIZE = 32 * 1024; // 32KB

    public static List<String> computeChunkSHA256(Path file, int chunkSize) throws IOException {
        List<String> hashes = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file.toFile());
             FileChannel channel = fis.getChannel()) {
            ByteBuffer buffer = ByteBuffer.allocate(chunkSize);
            while (true) {
                buffer.clear();
                int read = channel.read(buffer);
                if (read <= 0) break;
                buffer.flip();
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);
                hashes.add(bytesToHex(sha256(data)));
            }
        }
        return hashes;
    }

    public static String computeFileSHA256(Path file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file.toFile())) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = fis.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
            return bytesToHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
