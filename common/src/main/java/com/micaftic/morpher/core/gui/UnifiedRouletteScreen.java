package com.micaftic.morpher.core.gui;

import com.google.common.collect.Lists;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.animation.custom.CustomRouletteLayout;
import com.micaftic.morpher.client.animation.custom.CustomRouletteStore;
import com.micaftic.morpher.client.event.AnimationLockEvent;
import com.micaftic.morpher.client.gui.CustomRouletteEditorScreen;
import com.micaftic.morpher.client.gui.ModelMetadataPresenter;
import com.micaftic.morpher.client.gui.custom.ExtraAnimationButtons;
import com.micaftic.morpher.client.input.AnimationRouletteKey;
import com.micaftic.morpher.client.input.ExtraAnimationKey;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.core.api.client.KeyMappingFactory;
import com.micaftic.morpher.core.gpu.Pie;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.C2SPlayAnimationPacket;
import com.micaftic.morpher.util.data.OrderedStringMap;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Unified animation roulette - single screen for all four Sparkle
 * Morpher subprojects. Replaces the old Classic and Modern roulettes.
 *
 * <p>Visuals come from a colored slice ring drawn through
 * {@link Pie} plus white-on-transparent PNG sprites tinted via
 * {@link RouletteTheme}. Behavior mirrors the previous Modern
 * roulette: model-aware custom layouts, nav stack, gear sub-screens,
 * pagination, and key-binding hint.</p>
 */
public class UnifiedRouletteScreen extends Screen {

    private static final LinkedList<Pair<String, Integer>> navigationStack = Lists.newLinkedList();
    private static String lastModelId = StringPool.EMPTY;

    // ---- Custom roulette layout (persistent across screen rebuilds) ----
    private static OrderedStringMap<String, String> customRootProperties = null;
    private static Map<String, OrderedStringMap<String, String>> customClassifyMap = null;
    private static Map<String, Integer> customOriginalIndexMap = new HashMap<>();
    private static Map<String, String> customOriginalCategoryMap = new HashMap<>();
    private static boolean usingCustomLayout = false;

    private int centerX;
    private int centerY;

    private int hoveredIndex = -1;
    private int hoveredGearIndex = -1;
    private int hoveredPathSegment = -1;
    private boolean hoveredPrev;
    private boolean hoveredNext;
    private boolean hoveredEdit;

    private Pair<String, Integer> currentNavEntry;

    private final OrderedStringMap<String, String> currentProperties;
    private final Map<String, ExtraAnimationButtons> renderGroups;
    private final Map<String, OrderedStringMap<String, String>> textProperties;
    private final AnimatableEntity<?> animatableModel;
    private final ModelAssembly renderContext;

    public UnifiedRouletteScreen(String modelId, ModelAssembly modelAssembly, AnimatableEntity<?> animatable) {
        super(Component.literal("sparkle Roulette"));
        this.renderContext = modelAssembly;
        this.animatableModel = animatable;

        OrderedStringMap<String, String> customRoot = null;
        Map<String, OrderedStringMap<String, String>> customClassify = null;

        if (!lastModelId.equals(modelId)) {
            navigationStack.clear();
            lastModelId = modelId;
            if (GeneralConfig.ROULETTE_CONTENT_MODE.get() == GeneralConfig.RouletteContentMode.CUSTOM) {
                CustomRouletteLayout layout = CustomRouletteStore.load(modelId);
                if (layout != null) {
                    usingCustomLayout = true;
                    customRoot = CustomRouletteStore.buildRootMap(layout);
                    customClassify = CustomRouletteStore.buildClassifyMap(layout);
                    customOriginalIndexMap = CustomRouletteStore.buildIndexMap(layout);
                    customOriginalCategoryMap = CustomRouletteStore.buildCategoryMap(layout);
                    customRootProperties = customRoot;
                    customClassifyMap = customClassify;
                } else {
                    resetCustomState();
                }
            } else {
                resetCustomState();
            }
        } else if (usingCustomLayout) {
            customRoot = customRootProperties;
            customClassify = customClassifyMap;
        }

        this.textProperties = customClassify != null
                ? customClassify
                : modelAssembly.getModelData().getModelProperties().getExtraAnimationClassify();
        this.renderGroups = modelAssembly.getModelData().getModelProperties().getExtraAnimationButtons();
        if (navigationStack.isEmpty()) navigationStack.add(MutablePair.of(StringPool.EMPTY, 0));
        this.currentNavEntry = navigationStack.peekLast();
        if (this.textProperties.containsKey(this.currentNavEntry.getLeft())) {
            this.currentProperties = this.textProperties.get(this.currentNavEntry.getLeft());
        } else {
            this.currentProperties = customRoot != null
                    ? customRoot
                    : modelAssembly.getModelData().getModelProperties().getExtraAnimation();
            navigationStack.clear();
            navigationStack.add(MutablePair.of(StringPool.EMPTY, this.currentNavEntry.getRight()));
            this.currentNavEntry = navigationStack.peekLast();
        }
    }

    private static void resetCustomState() {
        usingCustomLayout = false;
        customRootProperties = null;
        customClassifyMap = null;
        customOriginalIndexMap.clear();
        customOriginalCategoryMap.clear();
    }

    /**
     * Seed the navigation stack so the next opened roulette starts in
     * the given submenu. Used by {@link ExtraAnimationKey} when a hot
     * key targets a {@code "#submenu"} slot.
     */
    public static void setInitialSubmenu(String submenuKey) {
        navigationStack.clear();
        navigationStack.add(MutablePair.of(StringPool.EMPTY, 0));
        if (submenuKey != null && !submenuKey.isEmpty()) {
            navigationStack.addLast(MutablePair.of(submenuKey, 0));
        }
    }

    @Override
    protected void init() {
        this.centerX = this.width / 2;
        this.centerY = this.height / 2;
        if (currentNavEntry.getRight() >= pageCount()) currentNavEntry.setValue(0);
    }

    // ---- Layout helpers ----------------------------------------------------

    private int pageCount() {
        return Math.max(1, (currentProperties.size() + 7) / 8);
    }

    private int page() {
        return currentNavEntry.getRight();
    }

    private float sliceStartOffset() {
        return -Pie.tau / 16.0f;
    }

    // ---- Rendering ---------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Dim veil to focus attention
        g.fill(0, 0, this.width, this.height, RouletteTheme.BG_VEIL);

        updateHover(mouseX, mouseY);
        renderSlices(g);
        renderLabels(g);
        renderCenter(g);
        renderPageButtons(g);
        renderEditButton(g);
        renderPathAndPage(g, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void updateHover(int mouseX, int mouseY) {
        float dx = mouseX - centerX;
        float dy = mouseY - centerY;
        float r = (float) Math.sqrt(dx * dx + dy * dy);
        float ang = (float) Math.atan2(dy, dx);
        if (ang < 0.0f) ang += Pie.tau;
        ang = (ang - sliceStartOffset() + Pie.tau) % Pie.tau;
        int idx = Mth.clamp((int) (ang / (Pie.tau / 8.0f)), 0, 7);

        hoveredIndex = -1;
        hoveredGearIndex = -1;
        int absoluteIdx = idx + page() * 8;
        if (absoluteIdx < currentProperties.size()
                && r >= RouletteTheme.WHEEL_INNER_R
                && r <= RouletteTheme.WHEEL_OUTER_R) {
            boolean hasGear = currentProperties.getValueAt(absoluteIdx).startsWith("#");
            if (hasGear && r <= RouletteTheme.WHEEL_GEAR_R) hoveredGearIndex = absoluteIdx;
            else hoveredIndex = absoluteIdx;
        }

        float prevDx = mouseX - (centerX - RouletteTheme.PAGE_BTN_OFFSET);
        float nextDx = mouseX - (centerX + RouletteTheme.PAGE_BTN_OFFSET);
        float btnDy = mouseY - centerY;
        float rr = RouletteTheme.PAGE_BTN_RADIUS * RouletteTheme.PAGE_BTN_RADIUS;
        hoveredPrev = page() > 0 && (prevDx * prevDx + btnDy * btnDy) <= rr;
        hoveredNext = (page() + 1) * 8 < currentProperties.size() && (nextDx * nextDx + btnDy * btnDy) <= rr;

        hoveredEdit = RoulettePanelStyle.inside(mouseX, mouseY, editButtonX(), editButtonY(),
                RoulettePanelStyle.ICON, RoulettePanelStyle.ICON);
    }

    private void renderSlices(GuiGraphics g) {
        float sliceSpan = Pie.tau / 8.0f;
        for (int i = 0; i < 8; i++) {
            int absoluteIdx = i + page() * 8;
            if (absoluteIdx >= currentProperties.size()) {
                drawGlassSlice(g, i, sliceSpan, RouletteTheme.WHEEL_INNER_R, RouletteTheme.WHEEL_OUTER_R,
                        RouletteTheme.SLICE_EMPTY, false);
                continue;
            }
            boolean isHover = absoluteIdx == hoveredIndex;
            boolean gearHover = absoluteIdx == hoveredGearIndex;
            boolean isSubmenu = currentProperties.getKeyAt(absoluteIdx).startsWith("#");
            boolean hasGear = currentProperties.getValueAt(absoluteIdx).startsWith("#");

            int mainColor;
            if (isSubmenu) mainColor = isHover ? RouletteTheme.SLICE_SUBMENU_HOV : RouletteTheme.SLICE_SUBMENU;
            else mainColor = isHover ? RouletteTheme.SLICE_HOVER : RouletteTheme.SLICE_IDLE;

            if (hasGear) {
                int gearColor = gearHover ? RouletteTheme.GEAR_HOVER : RouletteTheme.GEAR_IDLE;
                drawGlassSlice(g, i, sliceSpan, RouletteTheme.WHEEL_GEAR_R, RouletteTheme.WHEEL_OUTER_R, mainColor, isHover);
                drawGlassSlice(g, i, sliceSpan, RouletteTheme.WHEEL_INNER_R, RouletteTheme.WHEEL_GEAR_R, gearColor, gearHover);
                drawGearIcon(g, i, sliceSpan);
            } else {
                drawGlassSlice(g, i, sliceSpan, RouletteTheme.WHEEL_INNER_R, RouletteTheme.WHEEL_OUTER_R, mainColor, isHover);
            }
        }
    }

    private void drawGlassSlice(GuiGraphics g, int sliceIndex, float sliceSpan, float inner, float outer, int color, boolean hover) {
        float start = sliceStartOffset() + sliceIndex * sliceSpan + RouletteTheme.GAP_ANGLE_PADDING;
        float end = sliceStartOffset() + (sliceIndex + 1) * sliceSpan - RouletteTheme.GAP_ANGLE_PADDING;
        Pie.draw(g, centerX, centerY + 2, inner + 1.0f, outer + 1.0f, start, end, RouletteTheme.SLICE_SHADOW, 1.0f);
        Pie.draw(g, centerX, centerY, inner, outer, start, end, color, 1.0f);
        Pie.draw(g, centerX, centerY, Math.max(0.0f, outer - 2.0f), outer, start, end, hover ? 0xD8FFFFFF : RouletteTheme.SLICE_OUTLINE, 1.0f);
        Pie.draw(g, centerX, centerY, inner, inner + 1.5f, start, end, RouletteTheme.SLICE_INNER_GLOW, 1.0f);
    }

    private void drawGearIcon(GuiGraphics g, int sliceIndex, float sliceSpan) {
        float mid = sliceStartOffset() + (sliceIndex + 0.5f) * sliceSpan;
        float r = (RouletteTheme.WHEEL_INNER_R + RouletteTheme.WHEEL_GEAR_R) * 0.5f;
        int size = RouletteTheme.ICON_SIZE_GEAR;
        int ix = centerX + (int) (r * Math.cos(mid)) - size / 2;
        int iy = centerY + (int) (r * Math.sin(mid)) - size / 2;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(RouletteIcons.SETTINGS, ix, iy, size, size, 0.0f, 0.0f, 32, 32, 32, 32);
        RenderSystem.disableBlend();
    }

    private void renderLabels(GuiGraphics g) {
        float sliceSpan = Pie.tau / 8.0f;
        for (int i = 0; i < 8; i++) {
            int absoluteIdx = i + page() * 8;
            if (absoluteIdx >= currentProperties.size()) continue;
            float midAngle = sliceStartOffset() + (i + 0.5f) * sliceSpan;
            boolean hasGear = currentProperties.getValueAt(absoluteIdx).startsWith("#");
            boolean isSubmenuLink = currentProperties.getKeyAt(absoluteIdx).startsWith("#");
            boolean hover = absoluteIdx == hoveredIndex || absoluteIdx == hoveredGearIndex;
            float innerEdge = hasGear ? RouletteTheme.WHEEL_GEAR_R : RouletteTheme.WHEEL_INNER_R;
            float labelR = (innerEdge + RouletteTheme.WHEEL_OUTER_R) * 0.5f + 4.0f;
            int lx = centerX + Math.round(labelR * (float) Math.cos(midAngle));
            int ly = centerY + Math.round(labelR * (float) Math.sin(midAngle));
            String text = displayLabel(absoluteIdx);
            if (StringUtils.isBlank(text)) continue;

            List<KeyMapping> bindings = ExtraAnimationKey.getKeyMappings();
            boolean showKey = page() == 0 && navigationStack.size() == 1 && absoluteIdx < bindings.size();
            int labelWidth = sliceTextWidth(labelR, innerEdge);
            int iconSize = optionIconSize(absoluteIdx, isSubmenuLink, hasGear);
            int totalH = iconSize > 0 ? iconSize + 12 + (showKey ? 10 : 0) : 10 + (showKey ? 10 : 0);
            int y = ly - totalH / 2;
            if (iconSize > 0) {
                drawOptionIcon(g, absoluteIdx, isSubmenuLink, hasGear, lx, y, iconSize, hover);
                y += iconSize + 3;
            }
            int textColor = isSubmenuLink ? RouletteTheme.TEXT_LABEL_LINK : RouletteTheme.TEXT_LABEL;
            drawSliceLabel(g, text, lx, y, labelWidth, textColor, hover);
            if (showKey) renderKeyBinding(g, absoluteIdx, lx, y + 10, Math.min(labelWidth, 64), bindings, hover);
        }
    }

    private void renderKeyBinding(GuiGraphics g, int slot, int x, int y, int maxWidth, List<KeyMapping> bindings, boolean hover) {
        if (slot >= bindings.size()) return;
        KeyMapping km = bindings.get(slot);
        MutableComponent label = Component.literal("[ ").withStyle(ChatFormatting.YELLOW);
        if (km.isUnbound()) label.append(Component.translatable("key.sparkle_morpher.extra_animation.none"));
        else label.append(km.getTranslatedKeyMessage());
        label.append(" ]");
        drawSliceLabel(g, label.getString(), x, y, maxWidth, RouletteTheme.TEXT_KEYBIND, hover);
    }

    private int sliceTextWidth(float labelRadius, float innerEdge) {
        float radiusRoom = Math.min(labelRadius - innerEdge - 4.0f, RouletteTheme.WHEEL_OUTER_R - labelRadius - 6.0f);
        float chord = (float) (2.0d * labelRadius * Math.sin(Pie.tau / 16.0d - RouletteTheme.GAP_ANGLE_PADDING * 2.0f));
        return Mth.clamp((int) Math.min(chord - 18.0f, radiusRoom * 2.0f + 42.0f), 34, 82);
    }

    private int optionIconSize(int absoluteIdx, boolean submenu, boolean hasGear) {
        String key = currentProperties.getKeyAt(absoluteIdx);
        if (submenu || "#return".equals(key) || hasGear) {
            return RouletteTheme.ICON_SIZE_OPTION;
        }
        return 0;
    }

    private void drawOptionIcon(GuiGraphics g, int absoluteIdx, boolean submenu, boolean hasGear, int centerX, int y, int size, boolean hover) {
        ResourceLocation tex = null;
        String key = currentProperties.getKeyAt(absoluteIdx);
        if ("#return".equals(key)) {
            tex = RouletteIcons.ARROW_LEFT;
        } else if (hasGear) {
            tex = RouletteIcons.SETTINGS;
        } else if (submenu) {
            tex = RouletteIcons.ARROW_RIGHT;
        }
        if (tex == null) {
            return;
        }
        int pad = hover ? 3 : 2;
        int cx = centerX - size / 2;
        Pie.draw(g, centerX, y + size / 2.0f, 0.0f, size / 2.0f + pad, 0.0f, Pie.tau, hover ? 0x80FFFFFF : 0x44FFFFFF, 1.0f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(tex, cx, y, size, size, 0.0f, 0.0f, 32, 32, 32, 32);
        RenderSystem.disableBlend();
    }

    private void drawSliceLabel(GuiGraphics g, String text, int centerX, int y, int maxWidth, int color, boolean hover) {
        String clean = text.replace('\n', ' ').trim();
        if (clean.isEmpty() || maxWidth <= 4) {
            return;
        }
        int textWidth = this.font.width(clean);
        int left = centerX - maxWidth / 2;
        if (textWidth <= maxWidth) {
            g.drawString(this.font, clean, centerX - textWidth / 2, y, color, true);
            return;
        }
        if (!hover) {
            String clipped = trimToWidth(clean, maxWidth);
            g.drawString(this.font, clipped, centerX - this.font.width(clipped) / 2, y, color, true);
            return;
        }
        int travel = textWidth - maxWidth + 14;
        int offset = (int) ((System.currentTimeMillis() / 55L) % Math.max(1, travel * 2));
        if (offset > travel) {
            offset = travel * 2 - offset;
        }
        g.enableScissor(left, y - 1, left + maxWidth, y + 10);
        try {
            g.drawString(this.font, clean, left - offset + 7, y, color, true);
        } finally {
            g.disableScissor();
        }
    }

    private String trimToWidth(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int keep = text.length();
        while (keep > 0 && this.font.width(text.substring(0, keep) + ellipsis) > maxWidth) {
            keep--;
        }
        return text.substring(0, Math.max(0, keep)) + ellipsis;
    }

    private String displayLabel(int absoluteIdx) {
        String key = currentProperties.getKeyAt(absoluteIdx);
        String value = currentProperties.getValueAt(absoluteIdx);
        String display = value;
        if (value.startsWith("#")) {
            String sub = value.substring(1);
            if (renderGroups.containsKey(sub)) display = renderGroups.get(sub).getName();
        }
        if (StringUtils.isBlank(display)) display = key;
        return ModelMetadataPresenter.getLocalizedModelString(renderContext,
                "properties.extra_animation.%s".formatted(key), display);
    }

    private void renderCenter(GuiGraphics g) {
        if (animatableModel.getEntity() instanceof Player) {
            boolean locked = AnimationLockEvent.isLocked();
            // Tinted disc behind the white sprite gives a state-aware look without setColor().
            int tint = locked ? RouletteTheme.LOCK_TINT : RouletteTheme.UNLOCK_TINT;
            Pie.draw(g, centerX, centerY, 0.0f, RouletteTheme.WHEEL_INNER_R - 2.0f, 0.0f, Pie.tau, tint, 1.0f);
            Pie.draw(g, centerX, centerY, RouletteTheme.WHEEL_INNER_R - 2.0f, RouletteTheme.WHEEL_INNER_R,
                    0.0f, Pie.tau, RouletteTheme.CENTER_RING, 1.0f);

            ResourceLocation tex = locked ? RouletteIcons.LOCK : RouletteIcons.UNLOCK;
            int size = RouletteTheme.ICON_SIZE_LOCK;
            int ix = centerX - size / 2;
            int iy = centerY - size / 2;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.blit(tex, ix, iy, size, size, 0.0f, 0.0f, 64, 64, 64, 64);
            RenderSystem.disableBlend();
        } else {
            Pie.draw(g, centerX, centerY, 0.0f, RouletteTheme.WHEEL_INNER_R, 0.0f, Pie.tau,
                    RouletteTheme.CENTER_FILL, 1.0f);
            g.drawCenteredString(this.font, Component.translatable("gui.sparkle_morpher.roulette.stop"),
                    centerX, centerY - 4, RouletteTheme.TEXT_STOP);
        }
    }

    private void renderPageButtons(GuiGraphics g) {
        if (pageCount() <= 1) return;
        boolean prevEnabled = page() > 0;
        boolean nextEnabled = (page() + 1) * 8 < currentProperties.size();
        drawPageButton(g, centerX - RouletteTheme.PAGE_BTN_OFFSET, centerY, prevEnabled, hoveredPrev, true);
        drawPageButton(g, centerX + RouletteTheme.PAGE_BTN_OFFSET, centerY, nextEnabled, hoveredNext, false);
    }

    private void renderEditButton(GuiGraphics g) {
        RoulettePanelStyle.iconButton(g, hoveredEdit ? editButtonX() + 1 : -1, hoveredEdit ? editButtonY() + 1 : -1,
                editButtonX(), editButtonY(), RoulettePanelStyle.Glyph.ROULETTE, true);
    }

    private int editButtonX() {
        return centerX - RoulettePanelStyle.ICON / 2;
    }

    private int editButtonY() {
        return this.height - RouletteTheme.EDIT_BTN_BOTTOM_MARGIN;
    }

    private void drawPageButton(GuiGraphics g, float cx, float cy, boolean enabled, boolean hover, boolean left) {
        int fill;
        if (!enabled) fill = RouletteTheme.PAGE_BTN_FILL_DISABLED;
        else fill = hover ? RouletteTheme.PAGE_BTN_FILL_HOVER : RouletteTheme.PAGE_BTN_FILL;
        Pie.draw(g, cx, cy, 0.0f, RouletteTheme.PAGE_BTN_RADIUS, 0.0f, Pie.tau, fill, 1.0f);
        Pie.draw(g, cx, cy, RouletteTheme.PAGE_BTN_RADIUS - 1.5f, RouletteTheme.PAGE_BTN_RADIUS,
                0.0f, Pie.tau, RouletteTheme.PAGE_BTN_RING, 1.0f);

        ResourceLocation tex = left ? RouletteIcons.ARROW_LEFT : RouletteIcons.ARROW_RIGHT;
        int size = RouletteTheme.ICON_SIZE_ARROW;
        int ix = (int) cx - size / 2;
        int iy = (int) cy - size / 2;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(tex, ix, iy, size, size, 0.0f, 0.0f, 16, 16, 16, 16);
        RenderSystem.disableBlend();
    }

    private void renderPathAndPage(GuiGraphics g, int mouseX, int mouseY) {
        layoutAndDrawPath(g, mouseX, mouseY);
        String pageStr = String.format("%d/%d", page() + 1, pageCount());
        g.drawCenteredString(this.font, Component.literal(pageStr),
                centerX, centerY + RouletteTheme.PAGE_INDICATOR_Y_OFFSET, RouletteTheme.TEXT_PAGE);
    }

    private void layoutAndDrawPath(GuiGraphics g, int mouseX, int mouseY) {
        int pathY = centerY + RouletteTheme.PATH_Y_OFFSET;
        String prefix = Component.translatable("gui.sparkle_morpher.roulette.path.prefix").getString();
        String rootLabel = Component.translatable("gui.sparkle_morpher.roulette.path.root").getString();
        int prefixW = this.font.width(prefix);
        int sep = this.font.width(" > ");
        int total = prefixW;
        for (int i = 0; i < navigationStack.size(); i++) {
            String s = navigationStack.get(i).getLeft();
            total += this.font.width(StringUtils.isBlank(s) ? rootLabel : s);
            if (i < navigationStack.size() - 1) total += sep;
        }
        int x = centerX - total / 2;
        g.drawString(this.font, prefix, x, pathY, RouletteTheme.TEXT_PATH_DIM, true);
        x += prefixW;

        hoveredPathSegment = -1;
        for (int i = 0; i < navigationStack.size(); i++) {
            String raw = navigationStack.get(i).getLeft();
            String s = StringUtils.isBlank(raw) ? rootLabel : raw;
            int w = this.font.width(s);
            boolean isLast = i == navigationStack.size() - 1;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= pathY - 2 && mouseY < pathY + 10;
            int color = isLast ? RouletteTheme.TEXT_PATH_CUR : (hover ? 0xFFFFFFFF : RouletteTheme.TEXT_PATH_DIM);
            g.drawString(this.font, s, x, pathY, color, true);
            if (hover && !isLast) {
                g.fill(x, pathY + 9, x + w, pathY + 10, color);
                hoveredPathSegment = i;
            }
            x += w;
            if (i < navigationStack.size() - 1) {
                g.drawString(this.font, " > ", x, pathY, RouletteTheme.TEXT_SEPARATOR, true);
                x += sep;
            }
        }
    }

    // ---- Input -------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hoveredPrev) {
            playClick();
            previousPage();
            return true;
        }
        if (hoveredNext) {
            playClick();
            nextPage();
            return true;
        }
        if (hoveredPathSegment >= 0 && hoveredPathSegment < navigationStack.size() - 1) {
            playClick();
            navigateTo(hoveredPathSegment);
            return true;
        }
        if (hoveredEdit) {
            playClick();
            Minecraft.getInstance().setScreen(new CustomRouletteEditorScreen(lastModelId, renderContext));
            return true;
        }
        if (hoveredGearIndex >= 0) {
            playClick();
            String value = currentProperties.getValueAt(hoveredGearIndex);
            if (value.startsWith("#")) {
                String sub = value.substring(1);
                if (hasConfigGroup(sub)) {
                    Minecraft.getInstance().setScreen(new ModelSettingsScreen(renderContext, animatableModel, this, sub, false));
                    return true;
                }
            }
        }
        if (hoveredIndex >= 0) {
            playClick();
            String key = currentProperties.getKeyAt(hoveredIndex);
            if ("#return".equals(key)) navigateBack();
            else if (key.startsWith("#")) navigateToSubmenu(key);
            else playAnimation(key);
            return true;
        }
        double cdx = mouseX - centerX;
        double cdy = mouseY - centerY;
        if (cdx * cdx + cdy * cdy <= RouletteTheme.WHEEL_INNER_R * RouletteTheme.WHEEL_INNER_R) {
            if (animatableModel.getEntity() instanceof Player) {
                AnimationLockEvent.toggleLock();
            } else {
                NetworkHandler.sendToServer(C2SPlayAnimationPacket.createWithIndex(animatableModel.getEntity().getId()));
                onClose();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY < 0.0) nextPage(); else previousPage();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (KeyMappingFactory.isActiveAndMatches(AnimationRouletteKey.KEY_ROULETTE, keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---- Navigation --------------------------------------------------------

    private void previousPage() {
        currentNavEntry.setValue(Math.max(0, page() - 1));
    }

    private void nextPage() {
        if ((page() + 1) * 8 < currentProperties.size()) currentNavEntry.setValue(page() + 1);
    }

    private void navigateTo(int targetIndex) {
        while (navigationStack.size() > targetIndex + 1) navigationStack.removeLast();
        Minecraft.getInstance().setScreen(new UnifiedRouletteScreen(lastModelId, renderContext, animatableModel));
    }

    private void navigateToSubmenu(String value) {
        if (navigationStack.size() > 5) {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p != null) p.sendSystemMessage(Component.translatable("gui.sparkle_morpher.roulette.too_long"));
            return;
        }
        String sub = value.substring(1);
        if (textProperties.get(sub) != null) {
            navigationStack.addLast(MutablePair.of(sub, 0));
            Minecraft.getInstance().setScreen(new UnifiedRouletteScreen(lastModelId, renderContext, animatableModel));
        }
    }

    private void navigateBack() {
        if (navigationStack.size() > 1) {
            navigationStack.removeLast();
            Minecraft.getInstance().setScreen(new UnifiedRouletteScreen(lastModelId, renderContext, animatableModel));
            return;
        }
        Minecraft.getInstance().setScreen(null);
    }

    private void playAnimation(String key) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (NetworkHandler.isClientConnected()) {
            Entity entity = animatableModel.getEntity();
            if (usingCustomLayout) {
                int realIndex = customOriginalIndexMap.getOrDefault(key, hoveredIndex);
                String realCategory = customOriginalCategoryMap.getOrDefault(key, StringPool.EMPTY);
                if (entity instanceof Player) NetworkHandler.sendToServer(new C2SPlayAnimationPacket(realIndex, realCategory));
                else NetworkHandler.sendToServer(new C2SPlayAnimationPacket(realIndex, realCategory, entity.getId()));
            } else {
                Pair<String, Integer> last = navigationStack.peekLast();
                String submenu = (last != null && StringUtils.isNotBlank(last.getLeft())) ? last.getLeft() : StringPool.EMPTY;
                if (entity instanceof Player) NetworkHandler.sendToServer(new C2SPlayAnimationPacket(hoveredIndex, submenu));
                else NetworkHandler.sendToServer(new C2SPlayAnimationPacket(hoveredIndex, submenu, entity.getId()));
            }
        } else if (player != null) {
            PlayerCapability.get(player).ifPresent(cap -> cap.requestModelSwitch(key));
        }
        if (player != null && GeneralConfig.PRINT_ANIMATION_ROULETTE_MSG.get()) {
            player.sendSystemMessage(Component.translatable("message.sparkle_morpher.model.animation_roulette.play", key));
        }
        Minecraft.getInstance().setScreen(null);
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    private boolean hasConfigGroup(String id) {
        ExtraAnimationButtons group = renderGroups.get(id);
        return group != null && group.getConfigForms() != null && group.getConfigForms().length > 0;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
