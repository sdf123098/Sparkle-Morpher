package com.micaftic.morpher.resource.bbmodel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Blockbench 动画。
 *
 * <p>真实文件中 {@code loop} 字段是字符串：{@code "loop" / "once" / "hold"}。
 * 旧文件可能写成 boolean，解析器两种都接。</p>
 */
public class BBAnimation {
    public String uuid = "";
    public String name = "";
    /** 解析器拆出来的便捷布尔：true 表示循环。 */
    public boolean loop = false;
    /** Blockbench 原始 loop 模式字符串："loop" / "once" / "hold"。 */
    public String loopMode = "once";
    public float length = 0;
    public boolean selected = false;
    public float snapping = 24;
    public String path = "";
    public boolean anim_time_update = false;
    public boolean blend_weight = false;
    public boolean override = false;

    /** groupUuid -> animator。 */
    public Map<String, BBAnimator> animators = new LinkedHashMap<>();
    /** Effect/sound/particle timelines。 */
    public List<BBTimeline> timelines = new ArrayList<>();

    public static class BBAnimator {
        public String name = "";
        /** "bone" / "effect" / "particle" / "sound"。 */
        public String type = "bone";
        public List<BBKeyframe> keyframes = new ArrayList<>();
    }

    public static class BBKeyframe {
        /** "rotation" / "position" / "scale" / "particle" / "sound" / "timeline"。 */
        public String channel = "";
        /** 时间，单位秒。 */
        public float time = 0;
        /** "linear" / "catmullrom" / "bezier" / "step"。 */
        public String interpolation = "linear";
        public boolean bezier_linked = false;
        public float[] bezier_left_value = new float[0];
        public float[] bezier_right_value = new float[0];
        public float[] bezier_left_time = new float[0];
        public float[] bezier_right_time = new float[0];
        public List<BBDataPoint> data_points = new ArrayList<>();
        public String uuid = "";
    }

    /**
     * 数据点。Blockbench 把数值字段存成字符串以支持 Molang 表达式，
     * 但实际数值情况下也可能是 number；解析器已统一转字符串。
     */
    public static class BBDataPoint {
        public String x = "";
        public String y = "";
        public String z = "";
        public String w = "";
        public Map<String, String> values = new LinkedHashMap<>();
    }

    public static class BBTimeline {
        public String name = "";
        public String uuid = "";
        public List<BBTimelineEntry> entries = new ArrayList<>();
    }

    public static class BBTimelineEntry {
        public float time = 0;
        public String script = "";
    }
}
