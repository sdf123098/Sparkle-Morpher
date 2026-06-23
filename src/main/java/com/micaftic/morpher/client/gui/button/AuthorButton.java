package com.micaftic.morpher.client.gui.button;

import com.micaftic.morpher.client.gui.ModelMetadataPresenter;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.resource.models.AuthorInfo;
import com.micaftic.morpher.util.PlatformUtil;
import com.google.common.collect.Lists;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public class AuthorButton extends Button {

    private final AuthorInfo authorInfo;

    private final ModelAssembly modelAssembly;

    private final Identifier Identifier;

    private final int authorIndex;

    private final List<Component> componentList;

    private int selectedContactIndex;

    private final Screen parentScreen;

    public AuthorButton(int x, int y, AuthorInfo authorInfo, ModelAssembly modelAssembly, Identifier Identifier, int authorIndex, Screen screen) {
        super(x, y, 70, 130, Component.empty(), button -> {
        }, DEFAULT_NARRATION);
        this.selectedContactIndex = -1;
        this.authorInfo = authorInfo;
        this.modelAssembly = modelAssembly;
        this.Identifier = Identifier;
        this.authorIndex = authorIndex;
        this.componentList = Lists.newArrayList();
        if (this.authorInfo != null) {
            renderTooltip(false);
        }
        this.parentScreen = screen;
    }

    public static AuthorButton createAuthorButton(int x, int y, Screen screen) {
        return new AuthorButton(x, y, null, null, null, -1, screen);
    }

    private static int opaque(ChatFormatting formatting) {
        Integer color = formatting.getColor();
        return color == null ? 0xFFFFFFFF : 0xFF000000 | color.intValue();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        GuiGraphicsExtractor guiGraphics = extractor;
        Font font = Minecraft.getInstance().font;
        if (this.authorInfo == null || this.modelAssembly == null || this.Identifier == null) {
            guiGraphics.fillGradient(getX(), getY(), getX() + this.width, getY() + this.height, -1891417534, -1891417534);
            guiGraphics.centeredText(font, Component.literal("......"), getX() + (this.width / 2), getY() + (this.height / 2), opaque(ChatFormatting.GRAY));
            return;
        }
        if (isHoveredOrFocused()) {
            guiGraphics.fillGradient(getX(), getY(), getX() + this.width, getY() + this.height, -1892652116, -1892652116);
        } else {
            guiGraphics.fillGradient(getX(), getY(), getX() + this.width, getY() + this.height, -1891417534, -1891417534);
        }
        guiGraphics.blit(this.Identifier, getX() + 3, getY() + 3, getX() + 67, getY() + 67, 0.0f, 1.0f, 0.0f, 1.0f);
        String str = ModelMetadataPresenter.getLocalizedModelString(this.modelAssembly, "metadata.authors.%d.name".formatted(this.authorIndex), this.authorInfo.getName());
        String str2 = ModelMetadataPresenter.getLocalizedModelString(this.modelAssembly, "metadata.authors.%d.role".formatted(this.authorIndex), this.authorInfo.getRole());
        String str3 = ModelMetadataPresenter.getLocalizedModelString(this.modelAssembly, "metadata.authors.%d.comment".formatted(this.authorIndex), this.authorInfo.getComment());
        drawCenteredClipped(guiGraphics, font, str, getX() + 2, getY() + 72, this.width - 4, opaque(ChatFormatting.GOLD));
        if (str2 != null && !str2.isBlank()) {
            drawCenteredClipped(guiGraphics, font, str2, getX() + 2, getY() + 82, this.width - 4, opaque(ChatFormatting.GREEN));
        }
        if (str3 != null && !str3.isBlank()) {
            drawWrappedText(guiGraphics, Component.literal(str3), getX() + 3, getY() + 95, 64, -1);
        }
    }

    private void drawCenteredClipped(GuiGraphicsExtractor guiGraphics, Font font, String text, int x, int y, int width, int color) {
        if (text == null || text.isBlank()) {
            return;
        }
        List<FormattedCharSequence> lines = font.split(Component.literal(text), width);
        if (lines.isEmpty()) {
            return;
        }
        FormattedCharSequence line = lines.get(0);
        guiGraphics.text(font, line, x + ((width - font.width(line)) / 2), y, color, true);
    }

    public void drawWrappedText(GuiGraphicsExtractor guiGraphics, FormattedText formattedText, int x, int y, int wrapWidth, int color) {
        Font font = Minecraft.getInstance().font;
        for (FormattedCharSequence formattedCharSequence : font.split(formattedText, wrapWidth)) {
            guiGraphics.text(font, formattedCharSequence, x, y, color, false);
            y += 9;
            if (y > getY() + this.height) {
                return;
            }
        }
    }

    public void refreshContactComponents(GuiGraphicsExtractor guiGraphics, Screen screen, int mouseX, int mouseY) {
        if (this.isHovered && !this.componentList.isEmpty()) {
            guiGraphics.setComponentTooltipForNextFrame(Minecraft.getInstance().font, this.componentList, mouseX, mouseY);
/*             GuiGraphicsExtractor.renderComponentTooltip(Minecraft.getInstance().font, this.componentList, mouseX, mouseY);
 */
        } else if (this.selectedContactIndex != -1) {
            this.selectedContactIndex = -1;
            renderTooltip(false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta, double dy) {
        if (delta > 0.0d) {
            if (this.selectedContactIndex > 0) {
                this.selectedContactIndex--;
                renderTooltip(false);
                return true;
            }
            return true;
        }
        if (delta < 0.0d) {
            if (this.selectedContactIndex < this.componentList.size() - 2) {
                this.selectedContactIndex++;
                renderTooltip(false);
                return true;
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta, dy);
    }

    private void renderTooltip(boolean copied) {
        if (this.authorInfo == null) {
            return;
        }
        this.componentList.clear();
        for (int i = 0; i < this.authorInfo.getContact().size(); i++) {
            MutableComponent componentLiteral = Component.literal(this.authorInfo.getContact().getKeyAt(i) + ": " + this.authorInfo.getContact().getValueAt(i));
            if (i == this.selectedContactIndex) {
                componentLiteral.append(Component.literal(copied ? " [OK]" : " [>]").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            }
            this.componentList.add(componentLiteral);
        }
        if (!this.componentList.isEmpty()) {
            this.componentList.add(Component.translatable("gui.sparkle_morpher.model.info.contact.click_hint").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public void onPress(InputWithModifiers modifiers) {
        String link;
        if (this.authorInfo == null) {
            return;
        }
        int i = this.selectedContactIndex;
        if (i == -1) {
            i = 0;
        }
        if (i < 0 || i >= this.authorInfo.getContact().size() || (link = this.authorInfo.getContact().getValueAt(i)) == null) {
            return;
        }
        if (link.startsWith("http://") || link.startsWith("https://")) {
            Minecraft.getInstance().setScreen(new ConfirmLinkScreen(confirmed -> {
                if (confirmed) {
                    PlatformUtil.openUri(link);
                }
                Minecraft.getInstance().setScreen(this.parentScreen);
            }, link, true));
            return;
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(link);
        if (this.selectedContactIndex == -1) {
            this.selectedContactIndex = 0;
        }
        renderTooltip(true);
    }
}
