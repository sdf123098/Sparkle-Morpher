package com.micaftic.morpher.resource.bbmodel;

import java.util.ArrayList;
import java.util.List;

/**
 * Blockbench {@code .bbmodel} 文件顶层结构。
 *
 * <p>真实 Blockbench 5 schema 关键点：</p>
 * <ul>
 *   <li><b>没有</b>顶层 {@code groups[]} —— bone 层级在 {@link #outliner} 树里嵌套。</li>
 *   <li>{@link #elements} 平铺所有 cube/mesh/locator/null_object。</li>
 *   <li>{@link #outliner} 的 children 中字符串 = element UUID 引用，对象 = 子 group。</li>
 *   <li>{@code animation_controllers[].states} 是数组（顺序有意义）。</li>
 * </ul>
 */
public class BBModelFile {
    public BBMetadata meta = new BBMetadata();
    public BBResolution resolution = new BBResolution();
    public String name = "";
    public String model_identifier = "";
    public String geometry_name = "";
    public boolean box_uv = false;
    public String parent_model_id = "";

    /** 所有 elements 平铺。 */
    public List<BBElement> elements = new ArrayList<>();

    /**
     * 可选的并行 groups[] 数组。
     *
     * <p>Blockbench 5 有两种 outliner schema 写法：</p>
     * <ul>
     *   <li><b>"胖" outliner</b>：outliner 节点直接内联 {@code name/origin/rotation}，
     *       此时 {@code groups[]} 通常为空。</li>
     *   <li><b>"瘦" outliner</b>（Figura 常见）：outliner 节点只有
     *       {@code {uuid, isOpen, children}}，完整 bone 元数据存在这个 {@code groups[]} 数组里，
     *       按 uuid 一一对应。</li>
     * </ul>
     *
     * <p>{@link BBToRawConverter} 会优先用 outliner 节点自身的元数据，没有时回查 groups[]。</p>
     */
    public List<BBGroup> groups = new ArrayList<>();

    /** Outliner 树根节点列表（每个节点要么是 group，要么是 element 引用）。 */
    public List<BBOutlinerNode> outliner = new ArrayList<>();

    public List<BBTexture> textures = new ArrayList<>();
    public List<BBAnimation> animations = new ArrayList<>();
    public List<BBAnimationController> animation_controllers = new ArrayList<>();

    public BBDisplaySettings display;
    public List<BBCollection> collections = new ArrayList<>();

    public static class BBMetadata {
        public String format_version = "4.5";
        public String model_format = "";
        public boolean box_uv = false;
        public boolean backup = false;
        public long creation_time = 0;
    }

    public static class BBResolution {
        public int width = 16;
        public int height = 16;
    }
}
