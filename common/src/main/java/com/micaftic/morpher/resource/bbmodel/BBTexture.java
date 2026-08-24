package com.micaftic.morpher.resource.bbmodel;

import java.util.UUID;

/**
 * Blockbench 纹理
 * 支持嵌入式（base64）和外部引用
 */
public class BBTexture {
    public String uuid = "";
    public String name = "";
    public String source = "";
    public String path = "";
    public String relative_path = "";
    public int[] frames = new int[0];
    public int frame_time = 1;
    public boolean visible = true;
    public boolean internal = false;
    public int id = 0;
    public int uv_width = 16;
    public int uv_height = 16;
    public int width = 0;
    public int height = 0;

    /**
     * 检查是否为嵌入式纹理（base64 数据）
     */
    public boolean isEmbedded() {
        return source != null && source.startsWith("data:");
    }

    /**
     * 检查是否为外部文件引用
     */
    public boolean isExternal() {
        return path != null && !path.isEmpty();
    }
}
