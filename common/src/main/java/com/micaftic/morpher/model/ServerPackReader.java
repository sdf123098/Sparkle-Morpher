package com.micaftic.morpher.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * R8-4 ServerPackReader — 服务端模型包元数据解析（从 ServerModelManager.scanDirectoryPacks 抽取）。
 *
 * <p>读取单个 pack 目录：</p>
 * <ul>
 *   <li>ysm-pack.json → name/description/lang（name/description 取 {@code json.get(...).toString()}，
 *       与原实现一致——带 JSON 引号）</li>
 *   <li>ysm-pack.png → 图标字节 + 尺寸（iconFormat=2）</li>
 * </ul>
 *
 * <p>纯 Java（零 MC import），JVM 单测可跑真实解析；目录遍历由调用方负责。</p>
 */
public final class ServerPackReader {

    private ServerPackReader() {
    }

    /**
     * 解析一个 pack 目录。
     *
     * @param baseDir pack 根目录（folderPath 的相对基准）
     * @param packDir 具体 pack 目录（含 ysm-pack.json）
     * @throws IOException packJson 缺失/读取失败（调用方决定跳过）
     */
    public static ServerPackData read(Path baseDir, Path packDir) throws IOException {
        ServerPackData packData = new ServerPackData();
        packData.folderPath = baseDir.toFile().toURI().relativize(packDir.toFile().toURI()).getPath();

        Path packJson = packDir.resolve("ysm-pack.json");
        String jsonStr = Files.readString(packJson, StandardCharsets.UTF_8);
        JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();
        if (json.has("name")) {
            packData.name = json.get("name").getAsString();
        }
        if (json.has("description")) {
            packData.description = json.get("description").getAsString();
        }

        if (json.has("lang") && json.get("lang").isJsonObject()) {
            packData.lang = new HashMap<>();
            JsonObject langObj = json.getAsJsonObject("lang");
            for (Map.Entry<String, JsonElement> entry : langObj.entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    Map<String, String> translations = new HashMap<>();
                    for (Map.Entry<String, JsonElement> transEntry : entry.getValue().getAsJsonObject().entrySet()) {
                        translations.put(transEntry.getKey(), transEntry.getValue().getAsString());
                    }
                    packData.lang.put(entry.getKey(), translations);
                }
            }
        }

        Path packPng = packDir.resolve("ysm-pack.png");
        if (Files.exists(packPng)) {
            byte[] data = Files.readAllBytes(packPng);
            int[] dims = pngDimensions(data);
            packData.iconData = data;
            packData.iconWidth = dims[0];
            packData.iconHeight = dims[1];
            packData.iconFormat = 2; // 2=PNG
        }
        return packData;
    }

    /** 从 PNG 字节读取宽高（IHDR 大端）；非 PNG/过短返回 {0,0}。 */
    public static int[] pngDimensions(byte[] data) {
        if (data == null || data.length < 24) {
            return new int[]{0, 0};
        }
        if ((data[0] & 0xFF) != 0x89 || data[1] != 0x50 || data[2] != 0x4E || data[3] != 0x47) {
            return new int[]{0, 0};
        }
        int width = ((data[16] & 0xFF) << 24) | ((data[17] & 0xFF) << 16) | ((data[18] & 0xFF) << 8) | (data[19] & 0xFF);
        int height = ((data[20] & 0xFF) << 24) | ((data[21] & 0xFF) << 16) | ((data[22] & 0xFF) << 8) | (data[23] & 0xFF);
        return new int[]{width, height};
    }
}
