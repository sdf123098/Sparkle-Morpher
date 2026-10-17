package com.micaftic.morpher.peer;

import com.micaftic.morpher.core.security.YSMByteBuf;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class PeerLegacyTextureTest {
    @Test void rgbaArrayLargerThanScalarCeilingIsAcceptedWhileCountsStayBounded() {
        byte[] rgba = new byte[1024 * 1024 * 4];
        try(var writer = new YSMByteBuf(Unpooled.buffer())) {
            writer.writeByteArray(rgba); writer.writeVarInt(1_048_577);
            try(var scope = new PeerDecodeScope()) {
                assertArrayEquals(rgba, writer.readByteArray());
                assertThrows(IllegalArgumentException.class, writer::readVarInt);
            }
        }
    }
    @Test void truncatedOrOversizedArrayIsRejectedBeforeAllocation() {
        for(int length : new int[] {-1, (int)PeerLimits.RESOURCE_BYTES + 1, 2000}) {
            try(var writer = new YSMByteBuf(Unpooled.buffer()); var scope = new PeerDecodeScope()) {
                writer.writeVarInt(length);
                assertThrows(IllegalArgumentException.class, writer::readByteArray);
            }
        }
    }
    @Test void rawRgbaUsesDimensionsAndExactByteCountInsteadOfImageIo() throws Exception {
        var raw = new RawYsmModel(); raw.mainEntity.mainModel = new RawYsmModel.RawGeometry();
        var texture = new RawYsmModel.RawTexture(); texture.width = texture.height = 1024; texture.imageFormat = -1;
        texture.data = new byte[4 * 1024 * 1024]; raw.mainEntity.textures.put("default", texture);
        assertDoesNotThrow(() -> PeerModelPreflight.validateParsed(raw));
        texture.data = new byte[3]; assertThrows(IOException.class, () -> PeerModelPreflight.validateParsed(raw));
        texture.width = 8192; assertThrows(IOException.class, () -> PeerModelPreflight.validateParsed(raw));
    }
}
