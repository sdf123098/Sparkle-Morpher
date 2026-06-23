package com.micaftic.morpher.resource.bbmodel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 嗅探一个 zip 字节流的内容并分类。
 *
 * <p>用于在 {@code ClientModelManager.parseImportModel} 里决定一个 .zip 文件到底是：</p>
 * <ul>
 *   <li>{@link Kind#YSM_FOLDER} — 传统 YSM 模型包（含 {@code ysm.json} 或 {@code main.json+arm.json}）</li>
 *   <li>{@link Kind#FIGURA_AVATAR} — Figura avatar（含 {@code avatar.json} + 至少一个 .bbmodel）</li>
 *   <li>{@link Kind#PLAIN_BBMODEL} — 纯 bbmodel 包（只有 .bbmodel + 可选纹理，没有 avatar.json）</li>
 *   <li>{@link Kind#UNKNOWN} — 无法识别</li>
 * </ul>
 *
 * <p>同时把 bbmodel 文件的内容和 PNG 纹理读出来供后续直接使用。所有读取在内存中完成，
 * 不写临时文件。</p>
 */
public final class ZipModelSniffer {

    public enum Kind {
        YSM_FOLDER,
        FIGURA_AVATAR,
        PLAIN_BBMODEL,
        UNKNOWN
    }

    public final Kind kind;
    /** .bbmodel 文件内容（如有）。 */
    public final byte[] bbmodelBytes;
    /** .bbmodel 文件在 zip 内的相对路径（带斜杠的全名）。 */
    public final String bbmodelPath;
    /** zip 同目录下的所有 PNG 纹理（key 不含目录前缀，仅文件名）。 */
    public final Map<String, byte[]> sideTextures;
    /** avatar.json 内容（如有）。 */
    public final byte[] avatarJsonBytes;
    /** 检测到 ysm.json / main.json / arm.json 时为 true。 */
    public final boolean hasYsmMarkers;

    private ZipModelSniffer(Kind kind, byte[] bbmodelBytes, String bbmodelPath,
                            Map<String, byte[]> sideTextures, byte[] avatarJsonBytes,
                            boolean hasYsmMarkers) {
        this.kind = kind;
        this.bbmodelBytes = bbmodelBytes;
        this.bbmodelPath = bbmodelPath;
        this.sideTextures = sideTextures;
        this.avatarJsonBytes = avatarJsonBytes;
        this.hasYsmMarkers = hasYsmMarkers;
    }

    /**
     * 嗅探 zip 字节流。不抛异常，无法识别时返回 {@link Kind#UNKNOWN}。
     * 失败的 zip 解析也返回 UNKNOWN（让上层走老的 YSM 路径产生原始错误信息）。
     *
     * @param zipBytes 整个 .zip 文件的字节
     * @param sizeLimit 单条目最大字节数，超过即丢弃（防 zip-bomb）。0 = 不限制。
     */
    public static ZipModelSniffer sniff(byte[] zipBytes, long sizeLimit) {
        if (zipBytes == null || zipBytes.length < 22 /* EOCD min size */) {
            return new ZipModelSniffer(Kind.UNKNOWN, null, null, null, null, false);
        }

        byte[] bbmodel = null;
        String bbmodelPath = null;
        byte[] avatar = null;
        Map<String, byte[]> textures = new LinkedHashMap<>();
        boolean ysmMarker = false;
        // 选 bbmodel 时优先：体积大的（主模型）> 其它
        long bestBbmodelSize = -1;

        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String fullName = entry.getName();
                String lower = fullName.toLowerCase(Locale.ROOT);
                String base = baseName(lower);

                // YSM 标记：ysm.json / main.json / arm.json 任意一个出现即可视为有 YSM 标记
                if ("ysm.json".equals(base) || "main.json".equals(base) || "arm.json".equals(base)) {
                    ysmMarker = true;
                    continue; // YSM 路径会自己读
                }

                if (lower.endsWith(".bbmodel")) {
                    byte[] data = readEntry(zin, sizeLimit);
                    if (data != null && entry.getSize() > bestBbmodelSize) {
                        bbmodel = data;
                        bbmodelPath = fullName;
                        bestBbmodelSize = entry.getSize();
                    }
                } else if ("avatar.json".equals(base)) {
                    avatar = readEntry(zin, sizeLimit);
                } else if (lower.endsWith(".png")) {
                    byte[] data = readEntry(zin, sizeLimit);
                    if (data != null) {
                        textures.put(base, data);
                    }
                }
            }
        } catch (IOException e) {
            // 损坏的 zip：直接走 UNKNOWN，让上层处理
            return new ZipModelSniffer(Kind.UNKNOWN, null, null, null, null, false);
        }

        Kind kind;
        if (ysmMarker) {
            kind = Kind.YSM_FOLDER;
        } else if (bbmodel != null && avatar != null) {
            kind = Kind.FIGURA_AVATAR;
        } else if (bbmodel != null) {
            kind = Kind.PLAIN_BBMODEL;
        } else {
            kind = Kind.UNKNOWN;
        }

        return new ZipModelSniffer(kind, bbmodel, bbmodelPath, textures, avatar, ysmMarker);
    }

    /** 拆下 zip 条目，超过 {@code sizeLimit} 时丢弃返回 null。0 表示无上限。 */
    private static byte[] readEntry(InputStream in, long sizeLimit) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (sizeLimit > 0 && total > sizeLimit) {
                return null;
            }
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }

    private static String baseName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash < 0 ? path : path.substring(slash + 1);
    }

    /**
     * 解析 avatar.json 里的 name 字段，失败返回 null。
     * 简易解析，不依赖 Gson 也能用（避免循环依赖）。
     */
    public static String parseAvatarName(byte[] avatarJsonBytes) {
        if (avatarJsonBytes == null || avatarJsonBytes.length == 0) return null;
        String text = new String(avatarJsonBytes, StandardCharsets.UTF_8);
        // 抓 "name" : "..."，宽松匹配
        int idx = text.indexOf("\"name\"");
        if (idx < 0) return null;
        int colon = text.indexOf(':', idx + 6);
        if (colon < 0) return null;
        int quote = text.indexOf('"', colon + 1);
        if (quote < 0) return null;
        int endQuote = text.indexOf('"', quote + 1);
        if (endQuote < 0) return null;
        return text.substring(quote + 1, endQuote);
    }

    private ZipModelSniffer() {
        // not instantiable except via static factory
        this(Kind.UNKNOWN, null, null, null, null, false);
    }
}
