package com.micaftic.morpher.resource.bbmodel;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Blockbench element 基类，支持 cube/mesh/locator/null_object 类型。
 *
 * <p>所有元素平铺在 {@link BBModelFile#elements} 数组里，通过 outliner 树用 UUID 字符串引用。</p>
 */
public class BBElement {
    public String uuid = "";
    public String type = "cube";
    public String name = "";
    public boolean visibility = true;
    public boolean locked = false;

    // ---------- Cube 类型属性 ----------
    /** Cube 起点（局部坐标，单位:像素/blockbench 单位）。 */
    public float[] from = new float[3];
    /** Cube 终点。 */
    public float[] to = new float[3];
    /** 元素自身欧拉旋转（度），不包含父 group。 */
    public float[] rotation = new float[3];
    /** 元素自身的枢轴点（pivot）。 */
    public float[] origin = new float[3];
    public boolean mirror_uv = false;
    public boolean shade = true;
    public float inflate = 0;
    /** 0 不缩放；1 沿轴向缩放。 */
    public int rescale = 0;

    // ---------- Mesh 类型属性 ----------
    /** vertexKey -> 顶点（顶点 key 是字符串如 "abc1"）。 */
    public Map<String, BBMeshVertex> vertices = new LinkedHashMap<>();
    /** faceKey -> mesh 面。 */
    public Map<String, BBMeshFace> faces = new LinkedHashMap<>();

    // ---------- Cube 面数据 ----------
    /** "north"/"south"/"east"/"west"/"up"/"down" -> 立方体面。 */
    public Map<String, BBFace> cube_faces = new LinkedHashMap<>();

    // ---------- Locator 属性 ----------
    public float[] position = new float[3];
    public boolean render_order = false;

    /** mesh 顶点。bbmodel 里既可能是裸 [x,y,z]，也可能是 {position, visible}。 */
    public static class BBMeshVertex {
        public float[] position = new float[3];
        public boolean[] visible = new boolean[]{true, true, true};
    }

    /** mesh 面，N 边形。 */
    public static class BBMeshFace {
        /** 引用到 element.vertices 的 key（字符串）。 */
        public String[] vertices = new String[0];
        /** vertexKey -> [u, v]（Blockbench 像素坐标，未归一化）。 */
        public Map<String, float[]> uv = new LinkedHashMap<>();
        /** 引用 texture 的 id 或 uuid，可为 null。 */
        public String texture = null;
    }

    /** cube 单面。 */
    public static class BBFace {
        /** [x1, y1, x2, y2] 像素坐标（基于 resolution.width/height，未归一化）。 */
        public float[] uv = new float[4];
        /** 引用 texture 的 id 或 uuid。 */
        public String texture = null;
        /** 面 UV 旋转，仅取 0/90/180/270。 */
        public int rotation = 0;
        public boolean enabled = true;
        public String cullface = "";
        public int tint = -1;
    }
}
