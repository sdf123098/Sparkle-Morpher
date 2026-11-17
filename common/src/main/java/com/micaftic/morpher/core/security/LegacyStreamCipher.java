package com.micaftic.morpher.core.security;

import dev.spm.core.codec.legacy.LegacyHash;

import java.util.Objects;

/**
 * Package-private wire-compatible stream cipher and MT19937-64 mask primitives backing
 * {@link YsmCrypt}, {@link YSMClientCache}, and {@link ModelCacheKeyDerivation}. Hashing
 * delegates directly to {@link LegacyHash}.
 */
final class LegacyStreamCipher {
    private LegacyStreamCipher() {
    }

    static final class Random64 {
        private final long[] words = new long[312];
        private int position = 312;

        Random64(long seed) {
            words[0] = seed;
            for (int i = 1; i < words.length; i++) {
                words[i] = 6364136223846793005L * (words[i - 1] ^ (words[i - 1] >>> 62)) + i;
            }
        }

        long next() {
            if (position == 312) {
                for (int i = 0; i < 312; i++) {
                    long merged = (words[i] & 0xffffffff80000000L) | (words[(i + 1) % 312] & 0x7fffffffL);
                    words[i] = words[(i + 156) % 312] ^ (merged >>> 1) ^ ((merged & 1) == 0 ? 0 : 0xb5026f5aa96619e9L);
                }
                position = 0;
            }
            long result = words[position++];
            result ^= (result >>> 29) & 0x5555555555555555L;
            result ^= (result << 17) & 0x71d67fffeda60000L;
            result ^= (result << 37) & 0xfff7eee000000000L;
            return result ^ (result >>> 43);
        }
    }

    static byte[] mt19937Xor(byte[] data, int offset, int length, byte[] keyIv, long seed) {
        byte[] out = new byte[length];
        System.arraycopy(data, offset, out, 0, length);
        mt19937XorInPlace(out, 0, length, keyIv, seed);
        return out;
    }

    static void mt19937XorInPlace(byte[] data, int offset, int length, byte[] keyIv, long seed) {
        Objects.checkFromIndexSize(offset, length, data.length);
        Random64 mt = new Random64(LegacyHash.seeded(keyIv, 0, keyIv.length, seed));
        long bits = 0;
        for (int i = 0; i < length; i++) {
            if ((i & 7) == 0) {
                bits = mt.next();
            }
            data[offset + i] ^= (byte) bits;
            bits >>>= 8;
        }
    }

    static byte[] xchacha30(byte[] key, byte[] iv, byte[] input, int offset, int length) {
        byte[] output = new byte[length];
        xchacha30Into(key, iv, input, offset, output, 0, length);
        return output;
    }

    static void xchacha30Into(byte[] key, byte[] iv, byte[] in, int inOff, byte[] out, int outOff, int length) {
        byte[] password = combineKeyIv(key, iv);
        Stream stream = new Stream(password, 30);
        stream.apply(in, inOff, out, outOff, length);
    }

    static byte[] dynamicEncrypt(byte[] plainText, byte[] key, byte[] iv, long seed) {
        byte[] keyIv = combineKeyIv(key, iv);
        Stream stream = Stream.seeded(keyIv, seed);
        byte[] out = new byte[plainText.length];
        for (int done = 0; done < plainText.length;) {
            int full = stream.blocks * 64;
            int count = Math.min(full, plainText.length - done);
            long nextHash = count == full ? LegacyHash.seeded(plainText, done, count, seed) : 0L;
            stream.apply(plainText, done, out, done, count);
            if (count == full) {
                stream.advance(nextHash);
            }
            done += count;
        }
        return out;
    }

    static byte[] dynamicDecrypt(byte[] data, int off, int len, byte[] key, byte[] iv, long seed) {
        byte[] result = new byte[len];
        System.arraycopy(data, off, result, 0, len);
        dynamicDecryptInPlace(result, 0, len, key, iv, seed);
        return result;
    }

    static void dynamicDecryptInPlace(byte[] data, int off, int len, byte[] key, byte[] iv, long seed) {
        byte[] keyIv = combineKeyIv(key, iv);
        Stream stream = Stream.seeded(keyIv, seed);
        for (int done = 0; done < len;) {
            int full = stream.blocks * 64;
            int count = Math.min(full, len - done);
            stream.apply(data, off + done, data, off + done, count);
            if (count == full) {
                long nextHash = LegacyHash.seeded(data, off + done, count, seed);
                stream.advance(nextHash);
            }
            done += count;
        }
    }

    private static byte[] combineKeyIv(byte[] key, byte[] iv) {
        if (key.length != 32 || iv.length != 24) {
            throw new IllegalArgumentException("Expected 32-byte key and 24-byte iv");
        }
        byte[] keyIv = new byte[56];
        System.arraycopy(key, 0, keyIv, 0, 32);
        System.arraycopy(iv, 0, keyIv, 32, 24);
        return keyIv;
    }

    private static int[] initial() {
        return new int[]{0x61707865, 0x3320646e, 0x79622d32, 0x6b206574, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    }

    private static int[] permute(int[] state, int rounds) {
        int[] work = state.clone();
        for (int r = 0; r < rounds; r += 2) {
            for (int i = 0; i < 4; i++) quarter(work, i, i + 4, i + 8, i + 12);
            for (int i = 0; i < 4; i++) quarter(work, i, 4 + (i + 1) % 4, 8 + (i + 2) % 4, 12 + (i + 3) % 4);
        }
        return work;
    }

    private static void quarter(int[] x, int a, int b, int c, int d) {
        x[a] += x[b]; x[d] = Integer.rotateLeft(x[d] ^ x[a], 16);
        x[c] += x[d]; x[b] = Integer.rotateLeft(x[b] ^ x[c], 12);
        x[a] += x[b]; x[d] = Integer.rotateLeft(x[d] ^ x[a], 8);
        x[c] += x[d]; x[b] = Integer.rotateLeft(x[b] ^ x[c], 7);
    }

    private static final class Stream {
        private final int[] state;
        private int blocks;
        private int rounds;

        static Stream seeded(byte[] keyIv, long seed) {
            long h = LegacyHash.seeded(keyIv, 0, keyIv.length, seed);
            int rounds = 10 * (1 + (int) Long.remainderUnsigned(h, 3));
            Stream stream = new Stream(keyIv, rounds);
            stream.parameters(h);
            return stream;
        }

        Stream(byte[] password, int initialRounds) {
            this.rounds = initialRounds;
            int[] keyState = initial();
            for (int i = 0; i < 12; i++) {
                keyState[4 + i] = LegacyHash.littleInt(password, i * 4);
            }
            int[] expanded = permute(keyState, initialRounds);
            this.state = initial();
            for (int i = 0; i < 4; i++) {
                this.state[4 + i] = expanded[i];
                this.state[8 + i] = expanded[12 + i];
            }
            this.state[14] = LegacyHash.littleInt(password, 48);
            this.state[15] = LegacyHash.littleInt(password, 52);
            this.blocks = 64;
        }

        void parameters(long hash) {
            blocks = 64 + (int) Long.remainderUnsigned(hash, 64);
            rounds = 10 * (1 + (int) Long.remainderUnsigned(hash, 3));
        }

        void apply(byte[] in, int inOff, byte[] out, int outOff, int size) {
            for (int cursor = 0; cursor < size; cursor += 64) {
                int[] word = permute(state, rounds);
                for (int i = 0; i < 16; i++) {
                    word[i] += state[i];
                }
                int block = Math.min(64, size - cursor);
                for (int i = 0; i < block; i++) {
                    byte k = (byte) (word[i >>> 2] >>> ((i & 3) * 8));
                    out[outOff + cursor + i] = (byte) (in[inOff + cursor + i] ^ k);
                }
                state[12]++;
                if (state[12] == 0) {
                    state[13]++;
                }
            }
        }

        void advance(long hash) {
            parameters(hash);
            for (int i = 4; i < 16; i++) {
                state[i] ^= (int) (hash >>> ((i & 1) * 32));
            }
        }
    }
}
