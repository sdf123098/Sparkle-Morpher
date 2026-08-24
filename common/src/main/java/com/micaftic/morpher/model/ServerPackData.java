package com.micaftic.morpher.model;

/**
 * R8-4 服务端模型包元数据（从 ServerModelManager 内部类提升）。
 *
 * <p>对应本地模型目录下 {pack}/ysm-pack.json + ysm-pack.png 的描述：</p>
 * <ul>
 *   <li>folderPath：baseDir 相对路径（packs map 的 key）</li>
 *   <li>name / description：pack 显示信息</li>
 *   <li>lang：语言 → 翻译键 → 值</li>
 *   <li>iconData/iconWidth/iconHeight/iconFormat：包图标（iconFormat 2 = PNG）</li>
 * </ul>
 */
public class ServerPackData {
    public String folderPath;
    public byte[] iconData;
    public int iconWidth;
    public int iconHeight;
    public int iconFormat;
    public String name;
    public String description;
    public java.util.Map<String, java.util.Map<String, String>> lang;
}
