package com.micaftic.morpher.client.model.processor;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.entity.GeoEntity;
import com.micaftic.morpher.client.model.ModelResourceBundle;
import com.micaftic.morpher.geckolib3.core.controller.IAnimationController;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 模型处理器流水线。各动画控制器类用静态 REGISTRY 做懒注册，而注册/构建会在
 * 多个后台模型解析线程（SM-Model-Parse-*）与渲染线程上并发触发，因此这里必须
 * 保证线程安全：
 * <ul>
 *     <li>{@link #initializeOnce} 把懒注册变成原子操作，并发调用者等待注册完成，
 *     不会重复注册，也不会读到半初始化的列表；</li>
 *     <li>{@link #register} 通过 copy-on-write 快照发布，{@link #buildAll} 迭代的是
 *     不可变快照，绝不出现旧实现（线程不安全的 ReferenceArrayList 直接遍历）那种
 *     读到 null 处理器导致的 NPE；</li>
 *     <li>{@link #buildAll} 对 null 处理器做防御性跳过并给出明确 WARN。</li>
 * </ul>
 */
public class ProcessorPipeline<T extends GeoEntity<?>, TModel> {

    private final Object lock = new Object();

    @SuppressWarnings("unchecked")
    private ModelProcessor<T, TModel>[] processors = (ModelProcessor<T, TModel>[]) new ModelProcessor<?, ?>[0];

    private boolean initialized;

    public boolean isEmpty() {
        synchronized (this.lock) {
            return this.processors.length == 0;
        }
    }

    /**
     * 原子化懒注册：只有第一个调用者会执行 {@code registration}，
     * 其余并发调用者会等待注册完成后再返回。
     */
    public void initializeOnce(Runnable registration) {
        synchronized (this.lock) {
            if (this.initialized) {
                return;
            }
            int before = this.processors.length;
            try {
                registration.run();
                this.initialized = true;
            } catch (RuntimeException | Error e) {
                // 回滚半完成的注册，避免下次重试时出现重复处理器
                this.processors = Arrays.copyOf(this.processors, before);
                throw e;
            }
        }
    }

    public Consumer<T> buildAll(TModel modelData, ModelResourceBundle resourceBundle) {
        ModelProcessor<T, TModel>[] snapshot;
        synchronized (this.lock) {
            snapshot = this.processors;
        }
        ReferenceArrayList<ControllerFactory<T>> installers = new ReferenceArrayList<>(snapshot.length);
        for (int i = 0; i < snapshot.length; i++) {
            ModelProcessor<T, TModel> processor = snapshot[i];
            if (processor == null) {
                // 防御性检查：register 已拒绝 null，正常流程到不了这里；
                // 一旦出现说明某个处理步骤未注册/不可用，跳过并给出明确提示。
                YesSteveModel.LOGGER.warn("[SM] Skipping missing model processor at pipeline index {} (of {}); " +
                        "the corresponding animation steps will be unavailable for this model.", i, snapshot.length);
                continue;
            }
            ControllerFactory<T> installer = processor.process(modelData, resourceBundle);
            if (installer != null) {
                installers.add(installer);
            }
        }
        return entity -> {
            Objects.requireNonNull(entity);
            Consumer<IAnimationController<T>> consumer = entity::addAnimationController;
            for (ControllerFactory<T> installer : installers) {
                if (installer != null) {
                    installer.create(entity, consumer);
                }
            }
        };
    }

    public ModelProcessor<T, TModel> register(ModelProcessor<T, TModel> processor) {
        if (processor == null) {
            throw new IllegalArgumentException("Cannot register a null ModelProcessor: the registration site must supply a real processor");
        }
        synchronized (this.lock) {
            ModelProcessor<T, TModel>[] old = this.processors;
            ModelProcessor<T, TModel>[] next = Arrays.copyOf(old, old.length + 1);
            next[old.length] = processor;
            this.processors = next;
        }
        return processor;
    }
}
