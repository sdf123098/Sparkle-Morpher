package com.micaftic.morpher.resource.bbmodel;

import java.util.ArrayList;
import java.util.List;

/**
 * Blockbench outliner 树节点。
 *
 * <p>Blockbench 的 .bbmodel 文件**没有**顶层 {@code groups[]} 数组；
 * bone 层级是嵌套在 {@code outliner[]} 树里的内联组对象，叶子是元素 UUID 字符串。
 * 因此本节点同时承担 "group 节点"（含 name/origin/rotation/children）
 * 和 "element 引用"（仅 elementUuid）两种身份。</p>
 *
 * <pre>
 * outliner: [
 *   {
 *     "name": "head",
 *     "uuid": "...",
 *     "origin": [0, 24, 0],
 *     "rotation": [0, 0, 0],
 *     "children": [
 *       "element-uuid-string",          // 叶子: 通过 isElementRef() 判定
 *       { "name": "sub", "children": [...] }   // 子 group
 *     ]
 *   }
 * ]
 * </pre>
 */
public class BBOutlinerNode {
    /** group 节点的 uuid；element 引用时与 elementUuid 相同。 */
    public String uuid = "";

    /** group 显示名；element 引用时为空。 */
    public String name = "";

    /** 编辑器折叠状态。 */
    public boolean isOpen = false;
    public boolean visibility = true;
    public boolean locked = false;
    public boolean autouv = false;
    public boolean export = true;
    public boolean mirror_uv = false;

    /** group 枢轴点（pivot），3 维。 */
    public float[] origin = new float[3];
    /** group 欧拉角（度），3 维。 */
    public float[] rotation = new float[3];

    /**
     * true → 此节点是叶子，仅引用一个 element（uuid = elementUuid）；
     * false → 此节点是 group，children 有效。
     */
    public boolean elementRef = false;
    public String elementUuid = "";

    /** 嵌套子节点（既可以是 group 也可以是 element 引用）。 */
    public List<BBOutlinerNode> children = new ArrayList<>();

    public boolean isGroup() {
        return !elementRef;
    }

    public boolean isElementRef() {
        return elementRef;
    }

    /** 构造一个 element 引用节点。 */
    public static BBOutlinerNode forElement(String elementUuid) {
        BBOutlinerNode n = new BBOutlinerNode();
        n.elementRef = true;
        n.elementUuid = elementUuid;
        n.uuid = elementUuid;
        return n;
    }
}
