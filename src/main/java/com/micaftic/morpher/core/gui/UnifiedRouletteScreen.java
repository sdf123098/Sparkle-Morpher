package com.micaftic.morpher.core.gui;

import com.google.common.collect.Lists;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.animation.custom.CustomRouletteLayout;
import com.micaftic.morpher.client.animation.custom.CustomRouletteStore;
import com.micaftic.morpher.client.event.AnimationLockEvent;
import com.micaftic.morpher.client.gui.CustomRouletteEditorScreen;
import com.micaftic.morpher.client.gui.ModelMetadataPresenter;
import com.micaftic.morpher.client.gui.button.FlatColorButton;
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
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
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
 * Unified animation roulette — single screen for all four Sparkle
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
        clearWidgets();
        this.centerX = this.width / 2;
        this.centerY = this.height / 2;
        if (currentNavEntry.getRight() >= pageCount()) currentNavEntry.setValue(0);
        addRenderableWidget(new FlatColorButton(
                centerX - RouletteTheme.EDIT_BTN_WIDTH / 2,
                centerY + RouletteTheme.EDIT_BTN_Y_OFFSET,
                RouletteTheme.EDIT_BTN_WIDTH,
                RouletteTheme.EDIT_BTN_HEIGHT,
                Component.translatable("gui.sparkle_morpher.roulette.editor.edit"),
                button -> Minecraft.getInstance().setScreen(new CustomRouletteEditorScreen(lastModelId, renderContext))));
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
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        GuiGraphicsExtractor g = extractor;
        g.fill(0, 0, this.width, this.height, RouletteTheme.BG_VEIL);

        updateHover(mouseX, mouseY);
        renderSlices(g);
        renderLabels(g);
        renderCenter(g);
        renderPageButtons(g);
        renderPathAndPage(g, mouseX, mouseY);

        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
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
    }

    private void renderSlices(GuiGraphicsExtractor g) {
        float sliceSpan = Pie.tau / 8.0f;
        for (int i = 0; i < 8; i++) {
            int absoluteIdx = i + page() * 8;
            if (absoluteIdx >= currentProperties.size()) {
                drawSlice(g, i, sliceSpan, RouletteTheme.WHEEL_INNER_R, RouletteTheme.WHEEL_OUTER_R,
                        RouletteTheme.SLICE_EMPTY);
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
                drawSlice(g, i, sliceSpan, RouletteTheme.WHEEL_GEAR_R, RouletteTheme.WHEEL_OUTER_R, mainColor);
                drawSlice(g, i, sliceSpan, RouletteTheme.WHEEL_INNER_R, RouletteTheme.WHEEL_GEAR_R, gearColor);
                drawGearIcon(g, i, sliceSpan);
            } else {
                drawSlice(g, i, sliceSpan, RouletteTheme.WHEEL_INNER_R, RouletteTheme.WHEEL_OUTER_R, mainColor);
            }
        }
    }

    private void drawSlice(GuiGraphicsExtractor g, int sliceIndex, float sliceSpan, float inner, float outer, int color) {
        float start = sliceStartOffset() + sliceIndex * sliceSpan + RouletteTheme.GAP_ANGLE_PADDING;
        float end = sliceStartOffset() + (sliceIndex + 1) * sliceSpan - RouletteTheme.GAP_ANGLE_PADDING;
        Pie.draw(g, centerX, centerY, inner, outer, start, end, color, 1.0f);
    }

    private void drawGearIcon(GuiGraphicsExtractor g, int sliceIndex, float sliceSpan) {
        float mid = sliceStartOffset() + (sliceIndex + 0.5f) * sliceSpan;
        float r = (RouletteTheme.WHEEL_INNER_R + RouletteTheme.WHEEL_GEAR_R) * 0.5f;
        int size = RouletteTheme.ICON_SIZE_GEAR;
        int ix = centerX + (int) (r * Math.cos(mid)) - size / 2;
        int iy = centerY + (int) (r * Math.sin(mid)) - size / 2;
        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(770, 771, 1, 0);
        g.blit(RouletteIcons.SETTINGS, ix, iy, ix + size, iy + size, 0.0f, 1.0f, 0.0f, 1.0f);
        GlStateManager._disableBlend();
    }

    private void renderLabels(GuiGraphicsExtractor g) {
        float sliceSpan = Pie.tau / 8.0f;
        for (int i = 0; i < 8; i++) {
            int absoluteIdx = i + page() * 8;
            if (absoluteIdx >= currentProperties.size()) continue;
            float midAngle = sliceStartOffset() + (i + 0.5f) * sliceSpan;
            boolean hasGear = currentProperties.getValueAt(absoluteIdx).startsWith("#");
            boolean isSubmenuLink = currentProperties.getKeyAt(absoluteIdx).startsWith("#");
            float innerEdge = hasGear ? RouletteTheme.WHEEL_GEAR_R : RouletteTheme.WHEEL_INNER_R;
            float labelR = innerEdge * 0.5f + 50.0f;
            int lx = centerX + (int) (labelR * Math.cos(midAngle));
            int ly = centerY + (int) (labelR * Math.sin(midAngle));
            String text = displayLabel(absoluteIdx);
            if (StringUtils.isBlank(text)) continue;
            MutableComponent comp = Component.literal(text);
            if (isSubmenuLink) comp = comp.withStyle(ChatFormatting.GOLD);

            List<KeyMapping> bindings = ExtraAnimationKey.getKeyMappings();
            boolean showKey = page() == 0 && navigationStack.size() == 1 && absoluteIdx < bindings.size();
            int wrapWidth = (int) ((RouletteTheme.WHEEL_OUTER_R - innerEdge) * 0.9f);
            List<FormattedCharSequence> lines = this.font.split(comp, wrapWidth);
            int totalH = lines.size() * 9 + (showKey ? 10 : 0);
            int lineY = ly - totalH / 2;
            int textColor = isSubmenuLink ? RouletteTheme.TEXT_LABEL_LINK : RouletteTheme.TEXT_LABEL;
            for (FormattedCharSequence line : lines) {
                g.centeredText(this.font, line, lx, lineY, textColor);
                lineY += 9;
            }
            if (showKey) renderKeyBinding(g, absoluteIdx, lx, lineY + 1, bindings);
        }
    }

    private void renderKeyBinding(GuiGraphicsExtractor g, int slot, int x, int y, List<KeyMapping> bindings) {
        if (slot >= bindings.size()) return;
        KeyMapping km = bindings.get(slot);
        MutableComponent label = Component.literal("[ ").withStyle(ChatFormatting.YELLOW);
        if (km.isUnbound()) label.append(Component.translatable("key.sparkle_morpher.extra_animation.none"));
        else label.append(km.getTranslatedKeyMessage());
        label.append(" ]");
        g.centeredText(this.font, label, x, y, RouletteTheme.TEXT_KEYBIND);
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

    private void renderCenter(GuiGraphicsExtractor g) {
        if (animatableModel.getEntity() instanceof Player) {
            boolean locked = AnimationLockEvent.isLocked();
            int tint = locked ? RouletteTheme.LOCK_TINT : RouletteTheme.UNLOCK_TINT;
            Pie.draw(g, centerX, centerY, 0.0f, RouletteTheme.WHEEL_INNER_R - 2.0f, 0.0f, Pie.tau, tint, 1.0f);
            Pie.draw(g, centerX, centerY, RouletteTheme.WHEEL_INNER_R - 2.0f, RouletteTheme.WHEEL_INNER_R,
                    0.0f, Pie.tau, RouletteTheme.CENTER_RING, 1.0f);

            Identifier tex = locked ? RouletteIcons.LOCK : RouletteIcons.UNLOCK;
            int size = RouletteTheme.ICON_SIZE_LOCK;
            int ix = centerX - size / 2;
            int iy = centerY - size / 2;
            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(770, 771, 1, 0);
            g.blit(tex, ix, iy, ix + size, iy + size, 0.0f, 1.0f, 0.0f, 1.0f);
            GlStateManager._disableBlend();
        } else {
            Pie.draw(g, centerX, centerY, 0.0f, RouletteTheme.WHEEL_INNER_R, 0.0f, Pie.tau,
                    RouletteTheme.CENTER_FILL, 1.0f);
            g.centeredText(this.font, Component.translatable("gui.sparkle_morpher.roulette.stop"),
                    centerX, centerY - 4, RouletteTheme.TEXT_STOP);
        }
    }

    private void renderPageButtons(GuiGraphicsExtractor g) {
        if (pageCount() <= 1) return;
        boolean prevEnabled = page() > 0;
        boolean nextEnabled = (page() + 1) * 8 < currentProperties.size();
        drawPageButton(g, centerX - RouletteTheme.PAGE_BTN_OFFSET, centerY, prevEnabled, hoveredPrev, true);
        drawPageButton(g, centerX + RouletteTheme.PAGE_BTN_OFFSET, centerY, nextEnabled, hoveredNext, false);
    }

    private void drawPageButton(GuiGraphicsExtractor g, float cx, float cy, boolean enabled, boolean hover, boolean left) {
        int fill;
        if (!enabled) fill = RouletteTheme.PAGE_BTN_FILL_DISABLED;
        else fill = hover ? RouletteTheme.PAGE_BTN_FILL_HOVER : RouletteTheme.PAGE_BTN_FILL;
        Pie.draw(g, cx, cy, 0.0f, RouletteTheme.PAGE_BTN_RADIUS, 0.0f, Pie.tau, fill, 1.0f);
        Pie.draw(g, cx, cy, RouletteTheme.PAGE_BTN_RADIUS - 1.5f, RouletteTheme.PAGE_BTN_RADIUS,
                0.0f, Pie.tau, RouletteTheme.PAGE_BTN_RING, 1.0f);

        Identifier tex = left ? RouletteIcons.ARROW_LEFT : RouletteIcons.ARROW_RIGHT;
        int size = RouletteTheme.ICON_SIZE_ARROW;
        int ix = (int) cx - size / 2;
        int iy = (int) cy - size / 2;
        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(770, 771, 1, 0);
        g.blit(tex, ix, iy, ix + size, iy + size, 0.0f, 1.0f, 0.0f, 1.0f);
        GlStateManager._disableBlend();
    }

    private void renderPathAndPage(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        layoutAndDrawPath(g, mouseX, mouseY);
        String pageStr = String.format("%d/%d", page() + 1, pageCount());
        g.centeredText(this.font, Component.literal(pageStr),
                centerX, centerY + RouletteTheme.PAGE_INDICATOR_Y_OFFSET, RouletteTheme.TEXT_PAGE);
    }

    private void layoutAndDrawPath(GuiGraphicsExtractor g, int mouseX, int mouseY) {
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
        g.text(this.font, prefix, x, pathY, RouletteTheme.TEXT_PATH_DIM, true);
        x += prefixW;

        hoveredPathSegment = -1;
        for (int i = 0; i < navigationStack.size(); i++) {
            String raw = navigationStack.get(i).getLeft();
            String s = StringUtils.isBlank(raw) ? rootLabel : raw;
            int w = this.font.width(s);
            boolean isLast = i == navigationStack.size() - 1;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= pathY - 2 && mouseY < pathY + 10;
            int color = isLast ? RouletteTheme.TEXT_PATH_CUR : (hover ? 0xFFFFFFFF : RouletteTheme.TEXT_PATH_DIM);
            g.text(this.font, s, x, pathY, color, true);
            if (hover && !isLast) {
                g.fill(x, pathY + 9, x + w, pathY + 10, color);
                hoveredPathSegment = i;
            }
            x += w;
            if (i < navigationStack.size() - 1) {
                g.text(this.font, " > ", x, pathY, RouletteTheme.TEXT_SEPARATOR, true);
                x += sep;
            }
        }
    }

    // ---- Input -------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean flag) {
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
        if (hoveredGearIndex >= 0) {
            playClick();
            String value = currentProperties.getValueAt(hoveredGearIndex);
            if (value.startsWith("#")) {
                String sub = value.substring(1);
                if (renderGroups.containsKey(sub)) {
                    Minecraft.getInstance().setScreen(new ModelSettingsScreen(renderContext, animatableModel, this, sub));
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
        double cdx = event.x() - centerX;
        double cdy = event.y() - centerY;
        if (cdx * cdx + cdy * cdy <= RouletteTheme.WHEEL_INNER_R * RouletteTheme.WHEEL_INNER_R) {
            if (animatableModel.getEntity() instanceof Player) {
                AnimationLockEvent.toggleLock();
            } else {
                NetworkHandler.sendToServer(C2SPlayAnimationPacket.createWithIndex(animatableModel.getEntity().getId()));
                onClose();
            }
            return true;
        }
        return super.mouseClicked(event, flag);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY < 0.0) nextPage(); else previousPage();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (KeyMappingFactory.isActiveAndMatches(AnimationRouletteKey.KEY_ROULETTE, event.key(), event.scancode())) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
