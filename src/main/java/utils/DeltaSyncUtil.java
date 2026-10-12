package utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class DeltaSyncUtil {

    public static class DiffResult {
        public int totalChunks;
        public int changedChunks;
        public long totalSize;
        public long chunkSize;
    }

    public static DiffResult rebuildWithDelta(Path baseFile, Path newLocalFile, Path outFile, int chunkSize) throws IOException {
        List<String> baseHashes = fileChunkHashes(baseFile, chunkSize);
        List<String> newHashes = fileChunkHashes(newLocalFile, chunkSize);

        int totalChunks = newHashes.size();
        int changed = 0;

        try (FileChannel baseCh = Files.exists(baseFile) ? FileChannel.open(baseFile) : null;
             FileChannel newCh = FileChannel.open(newLocalFile);
             FileChannel outCh = FileChannel.open(outFile, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {

            ByteBuffer buffer = ByteBuffer.allocate(chunkSize);
            for (int i = 0; i < totalChunks; i++) {
                boolean same = i < baseHashes.size() && baseHashes.get(i).equals(newHashes.get(i));
                if (same && baseCh != null) {
                    long pos = (long) i * chunkSize;
                    long len = Math.min(chunkSize, newCh.size() - pos);
                    transfer(baseCh, outCh, pos, len);
                } else {
                    changed++;
                    long pos = (long) i * chunkSize;
                    int len = (int) Math.min(chunkSize, newCh.size() - pos);
                    buffer.clear();
                    buffer.limit(len);
                    newCh.position(pos);
                    int read = newCh.read(buffer);
                    buffer.flip();
                    outCh.write(buffer);
                }
            }
        }

        DiffResult dr = new DiffResult();
        dr.totalChunks = totalChunks;
        dr.changedChunks = changed;
        dr.totalSize = Files.size(newLocalFile);
        dr.chunkSize = chunkSize;
        return dr;
    }

    private static List<String> fileChunkHashes(Path file, int chunkSize) throws IOException {
        List<String> hashes = new ArrayList<>();
        if (!Files.exists(file)) return hashes;
        try (FileInputStream fis = new FileInputStream(file.toFile());
             FileChannel ch = fis.getChannel()) {
            ByteBuffer buf = ByteBuffer.allocate(chunkSize);
            while (true) {
                buf.clear();
                int read = ch.read(buf);
                if (read <= 0) break;
                buf.flip();
                byte[] data = new byte[buf.remaining()];
                buf.get(data);
                hashes.add(bytesToHex(ChunkingUtil.sha256(data)));
            }
        }
        return hashes;
    }

    private static void transfer(FileChannel in, FileChannel out, long position, long count) throws IOException {
        long pos = position;
        long remaining = count;
        while (remaining > 0) {
            long n = in.transferTo(pos, remaining, out);
            if (n <= 0) break;
            pos += n;
            remaining -= n;
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
