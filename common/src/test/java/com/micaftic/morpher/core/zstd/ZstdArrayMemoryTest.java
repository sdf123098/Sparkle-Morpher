package com.micaftic.morpher.core.zstd;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ZstdArrayMemoryTest {
    // Captured from the previous Unsafe implementation before migration.
    private static final String[] LEGACY_SHA256 = {
        "f96deff1816083fdff8bc3e46c3fe6ca46a6bb49f4d5a00627616c13237a512c",
        "f96deff1816083fdff8bc3e46c3fe6ca46a6bb49f4d5a00627616c13237a512c",
        "f96deff1816083fdff8bc3e46c3fe6ca46a6bb49f4d5a00627616c13237a512c",
        "684acb067121a8e146010d37997454ff94fd087c2affaa96dea588ceeda0929d",
        "06da451d956681353968f715abfd0af9915187f5e7fa9cdba9af28d380b21ca7",
        "494e839d811f7d7bec248a9d603c126dd7563aa70cc642acb600a46f59b0cbe8",
        "e4fcea8331574f3fb8f3e52015b3e8f0c218188439864f76a9fd7b719c593b50",
        "321e13bfbbebe61d12994249a6346b3c2f26ed845680aaf32ede77c6e33438fb",
        "6d7751442459d82127b8768b13d5247001e5e4f0b69e1d3fa8d434c6d2ed080c",
        "2954db249a77b265612581ed798e03794a4592c36373ec2d4cd20979b9524c1b",
        "bf9af81c45cdab18093c337bde7e62bb5c2633525368b8a82ab139f1ab32b560",
        "faf3cf56fe48eb05997df7b9494a2693c3a5d1261bd1a9405a552827668e5c4a",
        "b60a7f3a340dac01fe8df460414aef12b0768151ae9515a2ed9ab5452e3daa66",
        "020a31d90b6a5e6a41bb8a0c3b8e93c1cdfce8d733fbaa748c83cb9fbca1fd85",
        "26f6bbf56d5f5819caa3605892c89f669091d313a63728113378aa85cfbfc0b7",
        "7a1808305bacb8c5a633b5d2479c1aba1c4767be587240cd154f6b9a74748f2c",
        "1bcaa13d9d78a381cf3e1d9f6db6889b750c27fa6ccebd9e3fdeaaa0547dbd49",
        "a51e8770416fcab141aa09746466b146983243fbece8d180a752cbb07068bdbb",
        "723d3f3b278e8053f7e7241f98dd93e47eeb73e2dd559d963f1bbdd51d3aad45",
        "08fbabd77cde1c823307db1f848aeba61dbad609d0bf0a3ad1081758211d5fad",
        "c6ebd7924a0a3e1c51db2e0298dc683dbd9a54309aa9ec523958d4180e7bb475",
        "030e0621e91a08793955326f0970b79131535ec4321a0ec7698f65889bad85ae",
        "b36faca20e19260758f5da01484ea87bc847133e467eff7f8a663110090cb3de",
        "0fde0cb2237dae7bb5304f5807a981a1e4c74135a34eec06b784ac8266546b9b",
        "3f168c812b0d00e0080ae49f5ee838f0ec6969b3d49bc9f354ed89b05abf99c3",
        "679f1162c42702f6fa85bfb997082de1921fd555e5ffe5a91af5ba50188ba0f7",
        "fc20b8194fdf791bcc46de210ce6cf3db619ec12cea8dc6e935e59c9a3ef1e9c",
        "e27df4ca9e2e0c4dfb03412f9a305595af1d72f084ae67f8605328f751812c88",
        "61433a7ce0ee4922231b9aabea5d86e2e4c33e2f461ebf6b72e0c4f5b58e0b18",
        "c817b0a6a6d6fd73910c831730be200fec8da49378f2222ce515d572062ab1f3"
    };

    @Test void preservesLegacyFramesAndRoundTripsAcrossBlockBoundaries() throws Exception {
        var sha256 = MessageDigest.getInstance("SHA-256");
        int fixture = 0;
        for (int size : new int[]{0,1,7,8,31,256,1024,131071,131072,262145}) {
            for (int kind=0; kind<3; kind++) {
                byte[] input = new byte[size];
                new Random(12345).nextBytes(input);
                if (kind==1) Arrays.fill(input, (byte)42);
                if (kind==2) for (int i=0; i<size; i++) input[i]=(byte)(i%251);
                var compressor = new ZstdCompressor();
                byte[] compressed = new byte[compressor.maxCompressedLength(size)+64];
                int length = compressor.compress(input,0,size,compressed,0,compressed.length);
                compressed = Arrays.copyOf(compressed,length);
                assertEquals(LEGACY_SHA256[fixture++], HexFormat.of().formatHex(sha256.digest(compressed)), size+":"+kind);
                byte[] restored = new byte[size];
                assertEquals(size,new ZstdDecompressor().decompress(compressed,0,length,restored,0,size));
                assertArrayEquals(input,restored);
            }
        }
    }

    @Test void acceptsIndependentFramesWithKnownAndUnknownContentSizes() throws Exception {
        // Generated with python-zstandard 0.25.0 (libzstd), levels 3 and 19.
        byte[] expected = "Sparkle Morpher: independent Zstandard interoperability fixture.\n".repeat(1000).getBytes(StandardCharsets.UTF_8);
        for (String frame : new String[]{"KLUv/WDo/B0CABIEDhSgqQ1I25ll/dtKC03boX7rt3evFUDgL8vcQc0enlG3nnvzyF6WtUpBm7a02vJIuBH9GciYvJQIAQABaZ/QQxQ=","KLUv/QAwDQIAEoQNEbA7YGbYwWK+eWVpdWGXPRcDIKy/h4wyPi826tuY7zfncT2t70p+7+lte5pT+KbmbOQu5dpIAQABaZ/QQxQ="}) {
            try (var input = new ZstdInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(frame)))) {
                assertArrayEquals(expected,input.readAllBytes());
            }
        }
    }

    @Test void preservesUnalignedInputAndOutputSlicesAndStreaming() throws Exception {
        byte[] expected = new byte[300001];
        new Random(6789).nextBytes(expected);
        byte[] input = new byte[expected.length+11];
        System.arraycopy(expected,0,input,3,expected.length);
        var compressor = new ZstdCompressor();
        byte[] output = new byte[compressor.maxCompressedLength(expected.length)+80];
        Arrays.fill(output,(byte)0x5a);
        int length = compressor.compress(input,3,expected.length,output,5,output.length-10);
        byte[] restored = new byte[expected.length+17];
        Arrays.fill(restored,(byte)0x6b);
        assertEquals(expected.length,new ZstdDecompressor().decompress(output,5,length,restored,7,expected.length));
        assertArrayEquals(expected,Arrays.copyOfRange(restored,7,7+expected.length));
        assertEquals((byte)0x5a,output[4]);
        assertEquals((byte)0x6b,restored[6]);
        assertEquals((byte)0x6b,restored[7+expected.length]);
        var stream = new ByteArrayOutputStream();
        try (var compressed = new ZstdOutputStream(stream)) {
            compressed.write(expected,0,1);
            compressed.write(expected,1,131071);
            compressed.write(expected,131072,expected.length-131072);
        }
        try (var decompressed = new ZstdInputStream(new ByteArrayInputStream(stream.toByteArray()))) {
            assertArrayEquals(expected,decompressed.readAllBytes());
        }
    }
}
