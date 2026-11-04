package com.micaftic.morpher.core.api.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import java.nio.ByteBuffer;

public final class BufferBuilderBridge {
    private BufferBuilderBridge() {}
    public static boolean putBulkData(BufferBuilder builder, ByteBuffer buffer) { return false; }
    public static boolean supportsDirectTransfer() { return false; }
}