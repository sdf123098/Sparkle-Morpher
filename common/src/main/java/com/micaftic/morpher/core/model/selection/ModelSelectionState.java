package com.micaftic.morpher.core.model.selection;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * R7.3 ModelSelectionState — 模型选择状态（从 ClientModelManager 的 4 个 volatile 字段抽取）。
 *
 * <p>持有两条选择轨：</p>
 * <pre>
 *   selected        玩家当前选择的模型（任何来源）
 *   localOnly       最近一次"仅本地模型"的选择（断线/换服后用于恢复）
 * </pre>
 *
 * <p>{@link #remember} 对齐原 rememberSelectedModel 语义：选择为 localOnly 时双轨更新；
 * 选择为同名服务器模型时清除 localOnly 轨（服务器已公布同名模型，不再是"仅本地"）。
 * 判定输入（isLocalOnly、是否匹配当前 localOnly id）由调用方算好传入——state 保持纯状态，
 * 不依赖 modelAssemblyMap / lazyModelSources 等运行时目录。</p>
 */
public final class ModelSelectionState {

    private volatile String selectedModelId;
    private volatile String selectedTextureId;
    private volatile String selectedLocalOnlyModelId;
    private volatile String selectedLocalOnlyTextureId;

    public String selectedModelId() {
        return selectedModelId;
    }

    public String selectedTextureId() {
        return selectedTextureId;
    }

    public String localOnlyModelId() {
        return selectedLocalOnlyModelId;
    }

    public String localOnlyTextureId() {
        return selectedLocalOnlyTextureId;
    }

    /**
     * 记录一次模型选择（rememberSelectedModel 语义）。
     *
     * @param modelId       选择的模型 id（可空：清除 selected 轨）
     * @param textureId     选择的纹理 id
     * @param isLocalOnly   该模型当前是否为"仅本地"模型
     * @param matchesLocalOnly 选择是否与当前 localOnly 轨同 id（仅当 !isLocalOnly 且 modelId 非空时触发清除）
     */
    public void remember(String modelId, String textureId, boolean isLocalOnly, boolean matchesLocalOnly) {
        selectedModelId = modelId;
        selectedTextureId = textureId;
        if (isLocalOnly) {
            selectedLocalOnlyModelId = modelId;
            selectedLocalOnlyTextureId = textureId;
        } else if (modelId != null && matchesLocalOnly) {
            clearLocalOnly();
        }
    }

    /** 直接写 selected 轨（不动 localOnly 轨）；用于无模组服务器上的选择恢复。 */
    public void rememberPlain(String modelId, String textureId) {
        selectedModelId = modelId;
        selectedTextureId = textureId;
    }

    /** 只更新 selected id（texture 保持不变）；用于服务器模型重命名/重映射。 */
    public void setSelectedId(String modelId) {
        selectedModelId = modelId;
    }

    /** 清除 localOnly 轨（selected 轨保留）。 */
    public void clearLocalOnly() {
        selectedLocalOnlyModelId = null;
        selectedLocalOnlyTextureId = null;
    }

    /** 清除全部选择状态。 */
    public void clear() {
        clearLocalOnly();
        selectedModelId = null;
        selectedTextureId = null;
    }

    /**
     * 给定模型 id 是否与当前 selected 轨同 id（canonical 归一后比较）。
     *
     * @param canonicalKey id 归一函数（如 LocalModelCatalog::canonicalKey）；null 或空状态返回 false
     */
    public boolean matchesSelected(String modelId, UnaryOperator<String> canonicalKey) {
        if (modelId == null || selectedModelId == null || canonicalKey == null) {
            return false;
        }
        return Objects.equals(canonicalKey.apply(modelId), canonicalKey.apply(selectedModelId));
    }
}
