package com.micaftic.morpher.core.api.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.architectury.injectables.annotations.ExpectPlatform;

import java.nio.ByteBuffer;

public final class BufferBuilderBridge {

    private BufferBuilderBridge() {
    }

    @ExpectPlatform
    public static boolean putBulkData(BufferBuilder builder, ByteBuffer buffer) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean supportsDirectTransfer() {
        throw new AssertionError();
    }
}
