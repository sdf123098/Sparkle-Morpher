package com.micaftic.morpher.resource.bbmodel;

import java.util.ArrayList;
import java.util.List;

/**
 * Blockbench 顶层 {@code groups[]} 数组中的一个组元数据。
 *
 * <p>当 outliner 节点是"瘦"的（只有 {uuid, isOpen, children}）时，bone 的完整元数据
 * （name/origin/rotation 等）来自这里，按 {@link #uuid} 与 outliner 节点关联。
 * 这是 Figura/Blockbench 5 "free" 格式的常见写法。</p>
 */
public class BBGroup {
    public String uuid = "";
    public String name = "";
    public boolean visibility = true;
    public boolean locked = false;
    public boolean autouv = false;
    public boolean export = true;
    public boolean mirror_uv = false;
    public boolean isOpen = false;

    public float[] origin = new float[3];
    public float[] rotation = new float[3];
    public float[] color = new float[3];
    /** 父 group uuid（旧 schema 用，新 schema 走 outliner 树）。 */
    public String parent = "";
    public int render_order = 0;

    /** 旧 schema：直接列子 element/group 的 uuid。新 schema 走 outliner 树，此字段通常空。 */
    public List<String> children = new ArrayList<>();

    public BBGroup() {}

    public BBGroup(String uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }
}
