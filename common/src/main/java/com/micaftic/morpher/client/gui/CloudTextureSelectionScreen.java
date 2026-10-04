package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.client.gui.button.FlatColorButton;
import com.micaftic.morpher.util.InputUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.Consumer;

/** All textures remain reachable even when the embedded preview has very little height. */
public final class CloudTextureSelectionScreen extends Screen {
    private final Screen parent;
    private final String modelName;
    private final List<String> textures;
    private final Consumer<String> apply;
    private String selected;
    private int page;

    public CloudTextureSelectionScreen(Screen parent, String modelName, List<String> textures, String selected, Consumer<String> apply) {
        super(Component.translatable("gui.sparkle_morpher.model_panel.textures"));
        this.parent = parent; this.modelName = modelName; this.textures = List.copyOf(textures);
        this.selected = selected; this.apply = apply;
        int index = this.textures.indexOf(selected);
        if (index >= 0) page = index; // init converts the selected index to the first visible page.
    }
    private boolean initialized;
    private int panelW() { return Math.min(600, width - 16); }
    private int panelH() { return Math.min(420, height - 16); }
    private int panelX() { return (width - panelW()) / 2; }
    private int panelY() { return (height - panelH()) / 2; }
    private int rows() { return Math.max(1, (panelH() - 82) / 22); }
    private CloudModelPage pagination() { return CloudModelPage.cards(textures.size(), rows(), page); }

    @Override protected void init() {
        clearWidgets();
        if (!initialized) { page /= rows(); initialized = true; }
        CloudModelPage visible = pagination(); page = visible.scroll();
        int x = panelX() + 12, y = panelY() + 40, w = panelW() - 24;
        for (int i = visible.start(); i < visible.end(); i++) {
            String texture = textures.get(i);
            FlatColorButton button = addRenderableWidget(new FlatColorButton(x, y + (i - visible.start()) * 22, w, 20,
                    Component.literal(texture), pressed -> { apply.accept(texture); selected = texture; init(); }));
            button.setSelected(texture.equals(selected));
            button.setTooltipLines(List.of(Component.literal(texture)));
        }
        int footerY = panelY() + panelH() - 30;
        FlatColorButton previous = addRenderableWidget(new FlatColorButton(x, footerY, 26, 20, Component.literal("‹"),
                button -> { page = pagination().previous(); init(); }));
        previous.active = visible.scroll() > 0;
        FlatColorButton next = addRenderableWidget(new FlatColorButton(x + 30, footerY, 26, 20, Component.literal("›"),
                button -> { page = pagination().next(); init(); }));
        next.active = visible.hasNext();
        addRenderableWidget(new FlatColorButton(x + w - 64, footerY, 64, 20, Component.translatable("gui.done"), button -> onClose()));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = panelX(), y = panelY(), w = panelW(), h = panelH();
        g.fill(0, 0, width, height, 0xEE0A1417); g.fill(x, y, x + w, y + h, 0xFF19282D);
        g.fill(x, y, x + 3, y + h, 0xFF79D7BE);
        g.enableScissor(x + 12, y + 6, x + w - 12, y + 37);
        g.text(font, title, x + 12, y + 10, 0xFFE9E3D5);
        g.text(font, Component.literal(modelName + " · " + selected), x + 12, y + 24, 0xFFA1B4B2);
        g.disableScissor();
        CloudModelPage visible = pagination();
        String count = (page + 1) + "/" + (visible.maxScroll() + 1) + " · " + textures.size();
        g.text(font, Component.literal(count), x + 76, y + h - 24, 0xFFA1B4B2);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (sy != 0 && mx >= panelX() && mx < panelX() + panelW() && my >= panelY() && my < panelY() + panelH()) {
            page = sy > 0 ? pagination().previous() : pagination().next(); init(); return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }
    @Override public void onClose() { InputUtil.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
