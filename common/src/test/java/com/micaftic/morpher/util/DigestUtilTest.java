package com.micaftic.morpher.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * R0 冒烟测试：验证 JUnit 链路 + 纯工具类基线。
 * 仅依赖 JDK 标准库，不触碰任何 Minecraft 类。
 */
class DigestUtilTest {

    @Test
    void md5Hex_matchesKnownVector() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72",
                DigestUtil.md5Hex("abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void sha256Hex_matchesKnownVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                DigestUtil.sha256Hex("abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void md5Hex_emptyInput() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e",
                DigestUtil.md5Hex(new byte[0]));
    }

    @Test
    void sha256Hex_emptyInput() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                DigestUtil.sha256Hex(new byte[0]));
    }
}
