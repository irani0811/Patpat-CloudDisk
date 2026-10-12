package utils;

/**
 * rsync-style rolling checksum utility (similar to Adler-like two-sum)
 * s1 = sum(bytes) mod M, s2 = sum of prefix sums mod M
 * roll: remove old byte, add new byte in O(1)
 */
public class RollingHashUtil {
    private static final int M = 1 << 16; // 65536

    public static int checksum(byte[] buf) {
        return checksum(buf, 0, buf.length);
    }

    public static int checksum(byte[] buf, int off, int len) {
        int s1 = 0;
        int s2 = 0;
        for (int i = 0; i < len; i++) {
            int b = buf[off + i] & 0xFF;
            s1 = (s1 + b) % M;
            s2 = (s2 + s1) % M;
        }
        return (s2 << 16) | s1;
    }

    public static class Rolling {
        private final int window;
        private int s1;
        private int s2;

        public Rolling(byte[] buf, int off, int len) {
            this.window = len;
            int s1 = 0, s2 = 0;
            for (int i = 0; i < len; i++) {
                int b = buf[off + i] & 0xFF;
                s1 = (s1 + b) % M;
                s2 = (s2 + s1) % M;
            }
            this.s1 = s1;
            this.s2 = s2;
        }

        public int value() {
            return (s2 << 16) | s1;
        }

        public int roll(byte outByte, byte inByte) {
            int out = outByte & 0xFF;
            int in = inByte & 0xFF;
            s1 = (s1 - out + in) % M;
            if (s1 < 0) s1 += M;
            s2 = (s2 - (window * out) + s1) % M;
            if (s2 < 0) s2 += M;
            return (s2 << 16) | s1;
        }
    }
}
