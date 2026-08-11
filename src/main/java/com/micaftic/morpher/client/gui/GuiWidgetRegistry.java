package com.micaftic.morpher.client.gui;

import java.util.Iterator;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * GUI 观察者注册表（R7 剩余：GUI observers 从 ClientModelManager 抽出）——
 * 模型加载/同步状态变更时遍历注册的 {@link IGuiWidget} 通知。
 *
 * <p>弱引用持有：widget 被 GC 后自动移除，避免屏幕关闭后泄漏。
 * 遍历时每个回调独立 try-catch（单个 widget 抛异常不阻塞其余通知）。
 */
public final class GuiWidgetRegistry {

    private static final WeakHashMap<IGuiWidget, Object> widgets = new WeakHashMap<>();

    private GuiWidgetRegistry() {
    }

    public static <T extends IGuiWidget> T register(T widget) {
        widgets.put(widget, null);
        return widget;
    }

    public static void unregister(IGuiWidget widget) {
        widgets.remove(widget, null);
    }

    public static void forEach(Consumer<IGuiWidget> consumer) {
        Iterator<IGuiWidget> it = widgets.keySet().iterator();
        while (it.hasNext()) {
            try {
                consumer.accept(it.next());
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    }
}
