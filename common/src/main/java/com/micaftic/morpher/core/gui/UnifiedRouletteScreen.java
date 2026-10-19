package com.micaftic.morpher.core.gui;

import com.micaftic.morpher.cloud.client.CloudMotionSources;
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
import com.micaftic.morpher.util.InputUtil;
import com.micaftic.morpher.util.data.OrderedStringMap;
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
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import com.micaftic.morpher.util.AnimationRouletteDebugLog;

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
    private float layoutScale = 1.0f;

    private int hoveredIndex = -1;
    private int hoveredGearIndex = -1;
    private int hoveredPathSegment = -1;
    private boolean hoveredPrev;
    private boolean hoveredNext;
    private boolean hoveredEdit;
    private Supplier<Screen> pendingScreenFactory;

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

        var modelData = modelAssembly == null ? null : modelAssembly.getModelData();
        var modelProperties = modelData == null ? null : modelData.getModelProperties();
        if (modelProperties == null || !modelAssembly.getPresentationCapabilities().playerActionControlsAvailable()) {
            navigationStack.clear();
            navigationStack.add(MutablePair.of(StringPool.EMPTY, 0));
            this.currentNavEntry = navigationStack.peekLast();
            this.textProperties = Map.of();
            this.renderGroups = Map.of();
            this.currentProperties = new OrderedStringMap<>(new String[0], new String[0]);
            return;
        }
        this.textProperties = customClassify != null
                ? customClassify
                : modelProperties.getExtraAnimationClassify();
        this.renderGroups = modelProperties.getExtraAnimationButtons();
        if (navigationStack.isEmpty()) navigationStack.add(MutablePair.of(StringPool.EMPTY, 0));
        this.currentNavEntry = navigationStack.peekLast();
        if (this.textProperties.containsKey(this.currentNavEntry.getLeft())) {
            this.currentProperties = this.textProperties.get(this.currentNavEntry.getLeft());
        } else {
            this.currentProperties = customRoot != null
                    ? customRoot
                    : modelProperties.getExtraAnimation();
            navigationStack.clear();
            navigationStack.add(MutablePair.of(StringPool.EMPTY, this.currentNavEntry.getRight()));
            this.currentNavEntry = navigationStack.peekLast();
        }
    }

    public Screen settingsScreen(){return new RouletteSettingsScreen(this,lastModelId,renderContext,animatableModel);}
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
        this.centerY = this.height / 2 + 22;
        this.layoutScale = computeLayoutScale();
        if (currentNavEntry.getRight() >= pageCount()) currentNavEntry.setValue(0);
    }

    /**
     * Uniform scale (&lt;= 1) that shrinks the fixed-size wheel so it fits the
     * current GUI-scaled screen. Keeps the roulette from overflowing at high
     * vanilla GUI Scale or on small resolutions.
     */
    private float computeLayoutScale(){return Math.max(.25f,Math.min(1f,Math.min((width-64)/380f,(height-120)/330f)));}

    /** Convert a raw (screen) mouse coordinate into the wheel's unscaled logical space. */
    private int logicalMouseX(double mouseX) {
        return (int) Math.round(centerX + (mouseX - centerX) / layoutScale);
    }

    private int logicalMouseY(double mouseY) {
        return (int) Math.round(centerY + (mouseY - centerY) / layoutScale);
    }

    // ---- Layout helpers ----------------------------------------------------

    private int pageCount() {
        return Math.max(1, (currentProperties.size() + 7) / 8);
    }

    private int page() {
        return currentNavEntry.getRight();
    }

    private float sliceStartOffset(){return -(float)Math.PI/2;}

    // ---- Rendering ---------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float pt){
        var c=IndependentPanelChrome.of(width,height);RoulettePanelStyle.glassPanel(g,c.left(),c.top(),c.right()-c.left(),c.bottom()-c.top());int x=c.contentLeft(),y=c.top()+42;
        IndependentPanelChrome.text(g,font,Component.literal("动作轮盘 · "+renderContext.getDisplayName(lastModelId)),x,c.top()+15,1.4f,RoulettePanelStyle.TEXT);
        RoulettePanelStyle.Glyph[] icons={RoulettePanelStyle.Glyph.UP,RoulettePanelStyle.Glyph.USERS,RoulettePanelStyle.Glyph.SETTINGS,RoulettePanelStyle.Glyph.EDIT};for(int i=0;i<4;i++)IndependentPanelChrome.icon(g,mx,my,x+i*26,y,icons[i]);
        updateHover(logicalMouseX(mx),logicalMouseY(my));hoveredEdit=false;
        g.pose().pushMatrix();g.pose().translate(centerX,centerY);g.pose().scale(layoutScale,layoutScale);g.pose().translate(-centerX,-centerY);
        renderSlices(g);renderLabels(g);renderCenter(g);renderPageButtons(g);g.pose().popMatrix();
        g.text(font,Component.literal(navigationStack.size()>1?"左上角返回上一级 · Esc 关闭":"松开按键播放 · Esc 关闭"),x,height-c.top()-22,RoulettePanelStyle.MUTED,false);
        if(IndependentPanelChrome.inside(mx,my,x,y,104,22)){String[] tips={navigationStack.size()>1?"返回上一级轮盘":"返回游戏","选择动作目标","轮盘设置","编辑自定义布局"};int i=Math.min(3,(mx-x)/26);g.setComponentTooltipForNextFrame(font,java.util.List.of(Component.literal(tips[i])),mx,my);}
    }

    @Override
    public void tick() {
        super.tick();
        if (pendingScreenFactory != null) {
            Supplier<Screen> factory = pendingScreenFactory;
            pendingScreenFactory = null;
            ((Executor) Minecraft.getInstance()).execute(() -> InputUtil.setScreen(factory.get()));
        }
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
        boolean inSector=RouletteGeometry.contains(idx,dx,dy);
        if (absoluteIdx < currentProperties.size()
                && r >= RouletteTheme.WHEEL_INNER_R
                && r <= RouletteTheme.WHEEL_OUTER_R && inSector) {
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

    private void renderSlices(GuiGraphicsExtractor g) {
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

    private void drawGlassSlice(GuiGraphicsExtractor g,int index,float span,float inner,float outer,int color,boolean hover){
        var polygon=RouletteGeometry.sector(index,inner,outer);int shade=hover?0xBFDC4F5D:0x11FFFFFF;
        if(inner==RouletteTheme.WHEEL_INNER_R&&outer==RouletteTheme.WHEEL_GEAR_R)shade=hover?0xBFDC4F5D:0x16FFFFFF;
        for(int y=-(int)Math.ceil(outer);y<=(int)Math.ceil(outer);y++){int[] row=RouletteGeometry.span(polygon,y);if(row!=null&&row[1]>row[0])g.fill(centerX+row[0],centerY+y,centerX+row[1],centerY+y+1,shade);}
    }

    private void drawGearIcon(GuiGraphicsExtractor g, int sliceIndex, float sliceSpan) {
        float mid = sliceStartOffset() + (sliceIndex + 0.5f) * sliceSpan;
        float r = (RouletteTheme.WHEEL_INNER_R + RouletteTheme.WHEEL_GEAR_R) * 0.5f;
        int size = RouletteTheme.ICON_SIZE_GEAR;
        int ix = centerX + (int) (r * Math.cos(mid)) - size / 2;
        int iy = centerY + (int) (r * Math.sin(mid)) - size / 2;
        g.blit(RouletteIcons.SETTINGS, ix, iy, ix + size, iy + size, 0.0f, 1.0f, 0.0f, 1.0f);
    }

    private void renderLabels(GuiGraphicsExtractor g){for(int i=0;i<8;i++){int slot=i+page()*8;if(slot>=currentProperties.size())continue;double angle=-Math.PI/2+(i+.5)*Math.PI/4;int x=centerX+(int)Math.round(104*Math.cos(angle)),y=centerY+(int)Math.round(104*Math.sin(angle))-5;String text=displayLabel(slot);drawSliceLabel(g,text,x,y,72,RoulettePanelStyle.TEXT,slot==hoveredIndex);}}

    private void renderKeyBinding(GuiGraphicsExtractor g, int slot, int x, int y, int maxWidth, List<KeyMapping> bindings, boolean hover) {
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

    private void drawOptionIcon(GuiGraphicsExtractor g, int absoluteIdx, boolean submenu, boolean hasGear, int centerX, int y, int size, boolean hover) {
        Identifier tex = null;
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
        g.blit(tex, cx, y, cx + size, y + size, 0.0f, 1.0f, 0.0f, 1.0f);
    }

    private void drawSliceLabel(GuiGraphicsExtractor g, String text, int centerX, int y, int maxWidth, int color, boolean hover) {
        String clean = text.replace('\n', ' ').trim();
        if (clean.isEmpty() || maxWidth <= 4) {
            return;
        }
        int textWidth = this.font.width(clean);
        int left = centerX - maxWidth / 2;
        if (textWidth <= maxWidth) {
            g.text(this.font, clean, centerX - textWidth / 2, y, color, true);
            return;
        }
        if (!hover || layoutScale < 0.999f) {
            // When the wheel is scaled down we skip the scrolling marquee: the
            // scissor rect is applied in screen space and would not line up
            // with the scaled text. Ellipsis clipping is scale-safe.
            String clipped = trimToWidth(clean, maxWidth);
            g.text(this.font, clipped, centerX - this.font.width(clipped) / 2, y, color, true);
            return;
        }
        int travel = textWidth - maxWidth + 14;
        int offset = (int) ((System.currentTimeMillis() / 55L) % Math.max(1, travel * 2));
        if (offset > travel) {
            offset = travel * 2 - offset;
        }
        g.enableScissor(left, y - 1, left + maxWidth, y + 10);
        try {
            g.text(this.font, clean, left - offset + 7, y, color, true);
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

    private void renderCenter(GuiGraphicsExtractor g){String name=currentNavEntry.getLeft();if(name==null||name.isBlank())name="根目录";g.centeredText(font,Component.literal(name),centerX,centerY-6,RoulettePanelStyle.TEXT);}

    private void renderPageButtons(GuiGraphicsExtractor g) {
        if (pageCount() <= 1) return;
        boolean prevEnabled = page() > 0;
        boolean nextEnabled = (page() + 1) * 8 < currentProperties.size();
        drawPageButton(g, centerX - RouletteTheme.PAGE_BTN_OFFSET, centerY, prevEnabled, hoveredPrev, true);
        drawPageButton(g, centerX + RouletteTheme.PAGE_BTN_OFFSET, centerY, nextEnabled, hoveredNext, false);
    }

    private void renderEditButton(GuiGraphicsExtractor g) {
        RoulettePanelStyle.iconButton(g, hoveredEdit ? editButtonX() + 1 : -1, hoveredEdit ? editButtonY() + 1 : -1,
                editButtonX(), editButtonY(), RoulettePanelStyle.Glyph.ROULETTE, true);
    }

    private int editButtonX() {
        return centerX - RoulettePanelStyle.ICON / 2;
    }

    private int editButtonY() {
        return this.height - RouletteTheme.EDIT_BTN_BOTTOM_MARGIN;
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
        g.blit(tex, ix, iy, ix + size, iy + size, 0.0f, 1.0f, 0.0f, 1.0f);
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
        var c=IndependentPanelChrome.of(width,height);int x=c.contentLeft(),y=c.top()+42;
        if(com.micaftic.morpher.core.api.client.InputCodeAdapter.legacyMouseButtonFromSdlButton(event.button())==0&&IndependentPanelChrome.inside(event.x(),event.y(),x,y,104,22)){int i=(int)(event.x()-x)/26;switch(i){case 0->navigateBack();case 1->com.micaftic.morpher.client.gui.TargetActionSelectionScreen.open();case 2->openScreenNextTick(()->new RouletteSettingsScreen(this,lastModelId,renderContext,animatableModel));case 3->openScreenNextTick(()->new CustomRouletteEditorScreen(lastModelId,renderContext,this));}return true;}

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
            openScreenNextTick(() -> new CustomRouletteEditorScreen(lastModelId, renderContext,this));
            return true;
        }
        if (hoveredGearIndex >= 0) {
            playClick();
            String value = currentProperties.getValueAt(hoveredGearIndex);
            if (value.startsWith("#")) {
                String sub = value.substring(1);
                if (hasConfigGroup(sub)) {
                    openScreenNextTick(() -> new ModelSettingsScreen(renderContext, animatableModel, this, sub, false));
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
        double cdx = logicalMouseX(event.x()) - centerX;
        double cdy = logicalMouseY(event.y()) - centerY;
        if (cdx * cdx + cdy * cdy <= RouletteTheme.WHEEL_INNER_R * RouletteTheme.WHEEL_INNER_R) {
            if (animatableModel.getEntity() == Minecraft.getInstance().player) {
                AnimationLockEvent.toggleLock();
            } else {
                CloudMotionSources.stop(animatableModel);
                onClose();
            }
            return true;
        }
        return super.mouseClicked(event, flag);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if(scrollY==0)return super.mouseScrolled(mouseX,mouseY,scrollX,scrollY);
        if (scrollY < 0.0) nextPage(); else previousPage();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (KeyMappingFactory.isActiveAndMatches(AnimationRouletteKey.KEY_ROULETTE, event.key(), event.keycode())) {
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
        InputUtil.setScreen(new UnifiedRouletteScreen(lastModelId, renderContext, animatableModel));
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
            InputUtil.setScreen(new UnifiedRouletteScreen(lastModelId, renderContext, animatableModel));
        }
    }

    private void navigateBack() {
        if (navigationStack.size() > 1) {
            navigationStack.removeLast();
            InputUtil.setScreen(new UnifiedRouletteScreen(lastModelId, renderContext, animatableModel));
            return;
        }
        InputUtil.setScreen(null);
    }

    private void playAnimation(String key) {
        LocalPlayer player = Minecraft.getInstance().player;
        CloudMotionSources.play(animatableModel, key);
        if (player != null && GeneralConfig.PRINT_ANIMATION_ROULETTE_MSG.get()) {
            player.sendSystemMessage(Component.translatable("message.sparkle_morpher.model.animation_roulette.play", key));
        }
        InputUtil.setScreen(null);
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    private void openScreenNextTick(Supplier<Screen> screenFactory) {
        pendingScreenFactory = screenFactory;
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
