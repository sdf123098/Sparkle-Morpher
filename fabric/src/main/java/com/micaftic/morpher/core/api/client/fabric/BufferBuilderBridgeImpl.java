package com.micaftic.morpher.core.api.client.fabric;

import com.mojang.blaze3d.vertex.BufferBuilder;

import java.nio.ByteBuffer;

public final class BufferBuilderBridgeImpl {

    private BufferBuilderBridgeImpl() {
    }

    public static boolean putBulkData(BufferBuilder builder, ByteBuffer buffer) {
        return false;
    }

    public static boolean supportsDirectTransfer() {
        return false;
    }
}
