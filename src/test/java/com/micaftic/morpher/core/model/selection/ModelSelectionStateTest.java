package com.micaftic.morpher.core.model.selection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R7.3 ModelSelectionState 测试：模型选择状态的集中化（selected / localOnly 双轨）。
 *
 * <p>从 ClientModelManager 的 4 个 volatile 字段 + remember/clear 逻辑抽取；
 * 判定输入（isLocalOnly、是否匹配当前 localOnly）由调用方算好传入，state 保持纯状态。</p>
 */
class ModelSelectionStateTest {

    @Test
    void initialState_isAllNull() {
        ModelSelectionState state = new ModelSelectionState();
        assertNull(state.selectedModelId());
        assertNull(state.selectedTextureId());
        assertNull(state.localOnlyModelId());
        assertNull(state.localOnlyTextureId());
    }

    @Test
    void remember_nonLocalOnly_updatesSelectedOnly() {
        ModelSelectionState state = new ModelSelectionState();
        state.remember("cirno", "tex-a", false, false);
        assertEquals("cirno", state.selectedModelId());
        assertEquals("tex-a", state.selectedTextureId());
        assertNull(state.localOnlyModelId());
        assertNull(state.localOnlyTextureId());
    }

    @Test
    void remember_localOnly_updatesBothTracks() {
        ModelSelectionState state = new ModelSelectionState();
        state.remember("cirno", "tex-a", true, false);
        assertEquals("cirno", state.selectedModelId());
        assertEquals("tex-a", state.selectedTextureId());
        assertEquals("cirno", state.localOnlyModelId());
        assertEquals("tex-a", state.localOnlyTextureId());
    }

    @Test
    void remember_matchesCurrentLocalOnly_clearsLocalOnlyTrack() {
        ModelSelectionState state = new ModelSelectionState();
        state.remember("cirno", "tex-a", true, false);
        // 切换到同名非 localOnly 模型（服务器公布了同名模型）→ localOnly 轨清除
        state.remember("cirno", "tex-b", false, true);
        assertEquals("cirno", state.selectedModelId());
        assertEquals("tex-b", state.selectedTextureId());
        assertNull(state.localOnlyModelId(), "切换为服务器模型后 localOnly 轨应清除");
        assertNull(state.localOnlyTextureId());
    }

    @Test
    void remember_nonLocalOnlyNotMatching_keepsLocalOnlyTrack() {
        ModelSelectionState state = new ModelSelectionState();
        state.remember("cirno", "tex-a", true, false);
        state.remember("dai", "tex-b", false, false);
        assertEquals("dai", state.selectedModelId());
        assertEquals("cirno", state.localOnlyModelId(), "不同模型的非 localOnly 选择不干扰 localOnly 轨");
        assertEquals("tex-a", state.localOnlyTextureId());
    }

    @Test
    void remember_nullModelId_keepsLocalOnlyTrack() {
        ModelSelectionState state = new ModelSelectionState();
        state.remember("cirno", "tex-a", true, false);
        state.remember(null, null, false, false);
        assertNull(state.selectedModelId());
        assertEquals("cirno", state.localOnlyModelId(), "null 选择不触发 localOnly 清除（原逻辑 modelId != null 守卫）");
    }

    @Test
    void rememberPlain_updatesSelectedOnly() {
        ModelSelectionState state = new ModelSelectionState();
        state.rememberPlain("cirno", "tex-a");
        assertEquals("cirno", state.selectedModelId());
        assertEquals("tex-a", state.selectedTextureId());
        assertNull(state.localOnlyModelId());
    }

    @Test
    void setSelectedId_replacesIdOnly() {
        ModelSelectionState state = new ModelSelectionState();
        state.rememberPlain("old-id", "tex-a");
        state.setSelectedId("new-id");
        assertEquals("new-id", state.selectedModelId());
        assertEquals("tex-a", state.selectedTextureId(), "只更新 id，texture 保持不变");
    }

    @Test
    void clearLocalOnly_keepsSelectedTrack() {
        ModelSelectionState state = new ModelSelectionState();
        state.remember("cirno", "tex-a", true, false);
        state.clearLocalOnly();
        assertEquals("cirno", state.selectedModelId());
        assertNull(state.localOnlyModelId());
        assertNull(state.localOnlyTextureId());
    }

    @Test
    void clear_resetsEverything() {
        ModelSelectionState state = new ModelSelectionState();
        state.remember("cirno", "tex-a", true, false);
        state.clear();
        assertNull(state.selectedModelId());
        assertNull(state.selectedTextureId());
        assertNull(state.localOnlyModelId());
        assertNull(state.localOnlyTextureId());
    }

    @Test
    void matchesSelected_comparesByCanonicalKey() {
        ModelSelectionState state = new ModelSelectionState();
        state.remember("Cirno", "tex-a", false, false);
        assertTrue(state.matchesSelected("cirno", String::toLowerCase));
        assertFalse(state.matchesSelected("dai", String::toLowerCase));
        assertFalse(state.matchesSelected(null, String::toLowerCase));
        assertFalse(new ModelSelectionState().matchesSelected("cirno", String::toLowerCase), "无选择时不应匹配");
    }
}
