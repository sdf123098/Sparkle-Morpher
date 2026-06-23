package com.micaftic.morpher.resource.bbmodel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Blockbench 动画控制器。
 *
 * <p>Blockbench 真实 schema 里 {@code states} 是<b>数组</b>（顺序有意义，决定默认初始状态）。
 * 旧 plugin 偶尔写成对象；解析器两种都接，并保留 {@link #stateOrder} 记录原始顺序。</p>
 */
public class BBAnimationController {
    public String uuid = "";
    public String name = "";
    public boolean selected = false;
    public String path = "";
    public String initial_state = "";

    /** stateName -> state，便于快速查找。 */
    public Map<String, BBControllerState> states = new LinkedHashMap<>();
    /** state 的原始顺序，用于决定无 initial_state 时的默认 fallback。 */
    public List<String> stateOrder = new ArrayList<>();

    public static class BBControllerState {
        public String uuid = "";
        public String name = "";
        /** 此 state 同时激活的 animation 引用列表（UUID 或名字）。 */
        public List<String> animations = new ArrayList<>();
        public List<BBControllerTransition> transitions = new ArrayList<>();
        public List<String> on_entry = new ArrayList<>();
        public List<String> on_exit = new ArrayList<>();
    }

    public static class BBControllerTransition {
        /** 目标 state 名。 */
        public String target = "";
        /** Molang 条件表达式。 */
        public String condition = "";
    }
}
