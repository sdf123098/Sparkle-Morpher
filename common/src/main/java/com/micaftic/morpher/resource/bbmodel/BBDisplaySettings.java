package com.micaftic.morpher.resource.bbmodel;

import java.util.HashMap;
import java.util.Map;

/**
 * Blockbench 显示设置
 * 用于定义物品在不同位置的显示方式
 */
public class BBDisplaySettings {
    public BBDisplayTransform thirdperson_righthand;
    public BBDisplayTransform thirdperson_lefthand;
    public BBDisplayTransform firstperson_righthand;
    public BBDisplayTransform firstperson_lefthand;
    public BBDisplayTransform gui;
    public BBDisplayTransform head;
    public BBDisplayTransform ground;
    public BBDisplayTransform fixed;

    public static class BBDisplayTransform {
        public float[] rotation = new float[3];
        public float[] translation = new float[3];
        public float[] scale = new float[3];
    }
}
