package com.micaftic.morpher.client.gui.resource.station;

import com.micaftic.morpher.resource.MemoryModelParser;
import com.micaftic.morpher.resource.bundle.TextureDecoder;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import com.micaftic.morpher.peer.PeerDecodeScope;
import com.micaftic.morpher.legacy.compat.LegacyCompatModelFormat;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.zip.ZipInputStream;

/** Extracts authored card art on an IO worker; never installs or applies a model. */
public final class EmbeddedModelPreview {
    public static final int MAX_MODEL_BYTES = 16 * 1024 * 1024;
    private static final int MAX_EXPANDED_BYTES = 32 * 1024 * 1024;
    private static final int MAX_ARCHIVE_ENTRIES = 2048;
    private static final int MAX_EDGE = 512;

    private EmbeddedModelPreview() {}

    /** Worker-only image normalization: inspect actual dimensions, cap 4MP and retain at most 512px. */
    public static byte[] normalizeImage(byte[] source) {
        var image = new RawYsmModel.RawImage();
        image.data = source;
        image.format = 0;
        return thumbnail(image);
    }

    public static byte[] extract(byte[] source, String fileName) throws Exception {
        if (source.length > MAX_MODEL_BYTES) throw new IllegalArgumentException("Preview model is too large");
        var kind = MemoryModelParser.detectKind(fileName);
        if (kind == MemoryModelParser.Kind.ZIP) checkArchiveBudget(source);
        if (kind == MemoryModelParser.Kind.YSM) {
            int cryptoVersion = LegacyCompatModelFormat.detectCryptoVersion(source);
            // The old AES/deflate containers have no bounded decoder; keep their external covers.
            if (cryptoVersion == 1 || cryptoVersion == 2) return null;
        }
        // Existing pure-Java decode guards bound binary objects, zstd expansion and allocations.
        // This scope installs no model, native mesh, runtime, script or texture registration.
        try (PeerDecodeScope ignored = new PeerDecodeScope()) {
            return extract(MemoryModelParser.parseLegacy(source, fileName));
        }
    }

    private static void checkArchiveBudget(byte[] source) throws IOException {
        long expanded = 0;
        int entries = 0;
        byte[] buffer = new byte[8192];
        // Entry names are irrelevant here; GBK also handles archives lacking the UTF-8 flag.
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(source), Charset.forName("GBK"))) {
            while (zip.getNextEntry() != null) {
                if (++entries > MAX_ARCHIVE_ENTRIES) throw new IOException("Preview archive has too many entries");
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expanded += read;
                    if (expanded > MAX_EXPANDED_BYTES) throw new IOException("Preview archive expansion exceeds budget");
                }
            }
        }
    }

    public static byte[] extract(RawYsmModel raw) {
        if (raw == null || raw.properties == null) return null;
        RawYsmModel.RawImage background = null;
        for (var image : raw.properties.backgroundImages) {
            String name = image.name == null ? "" : image.name.toLowerCase(Locale.ROOT);
            if (name.contains("preview") || name.contains("thumbnail") || name.contains("cover")) {
                byte[] preview = thumbnail(image);
                if (preview != null) return preview;
            }
            if (image.name != null && (image.name.equals(raw.properties.guiBackground)
                    || name.equals("gui_background"))) background = image;
        }
        byte[] rendered = StaticModelThumbnail.render(raw);
        return rendered != null ? rendered : background == null ? null : thumbnail(background);
    }

    private static byte[] thumbnail(RawYsmModel.RawImage image) {
        if (image.data == null || image.data.length == 0 || image.data.length > MAX_MODEL_BYTES) return null;
        BufferedImage decoded = decodeImage(image.data, image.format, image.width, image.height);
        if (decoded == null) return null;
        double scale = Math.min(1, (double) MAX_EDGE / Math.max(decoded.getWidth(), decoded.getHeight()));
        BufferedImage small = new BufferedImage(Math.max(1, (int) Math.round(decoded.getWidth() * scale)),
                Math.max(1, (int) Math.round(decoded.getHeight() * scale)), BufferedImage.TYPE_INT_ARGB);
        var graphics = small.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(decoded, 0, 0, small.getWidth(), small.getHeight(), null);
        } finally { graphics.dispose(); decoded.flush(); }
        return TextureDecoder.encodeToPng(small, null);
    }

    static BufferedImage decodeImage(byte[] data, int format, int width, int height) {
        if (data == null || data.length == 0 || data.length > MAX_MODEL_BYTES) return null;
        format = TextureDecoder.resolveImageFormat(data, format);
        // Header-only dimensions keep this preview independent from optional WebP/AVIF decoders.
        if (format != -1 && format != 1 && format != 2 && format != 3) return null;
        var declared = com.micaftic.morpher.resource.ImageHeaderDimensions.read(data, format);
        if (format != -1 && declared == null) return null;
        if (declared != null) { width = declared.width(); height = declared.height(); }
        if (width <= 0 || height <= 0 || (long) width * height > 4_194_304) return null;
        return TextureDecoder.decodeToImage(data, format, width, height);
    }
}
