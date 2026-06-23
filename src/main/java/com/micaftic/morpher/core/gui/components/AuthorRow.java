package com.micaftic.morpher.core.gui.components;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.gui.ModelMetadataPresenter;
import com.micaftic.morpher.client.upload.IResourceLocatable;
import net.minecraft.client.input.MouseButtonEvent;
import com.micaftic.morpher.resource.models.AuthorInfo;
import com.micaftic.morpher.util.data.OrderedStringMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.apache.commons.lang3.StringUtils;
import com.micaftic.morpher.core.gui.ModernModelInfoScreen;
import com.micaftic.morpher.core.gui.OptionRow;

import java.util.List;

public final class AuthorRow extends OptionRow<Object> {
    private static final Identifier DEFAULT_AVATAR = Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "texture/default_avatar.png");
    private static final int AVATAR_SIZE = 48;

    private final ModernModelInfoScreen owner;
    private final AuthorInfo author;
    private final int authorIndex;
    private final IResourceLocatable avatarLocatable;
    private int hoveredContactIndex = -1;

    public AuthorRow(ModernModelInfoScreen owner, AuthorInfo author, int authorIndex, IResourceLocatable avatarLocatable) {
        super(0, 0, 0, AVATAR_SIZE + 8, null);
        this.owner = owner;
        this.author = author;
        this.authorIndex = authorIndex;
        this.avatarLocatable = avatarLocatable;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        GuiGraphicsExtractor g = extractor;
        boolean hover = isHovered();
        g.fill(getX(), getY(), getX() + width, getY() + height, hover ? 0x90171717 : 0x90000000);
        int ax = getX() + 4;
        int ay = getY() + 4;
        Identifier avatar = avatarLocatable != null ? avatarLocatable.getResourceLocation().orElse(DEFAULT_AVATAR) : DEFAULT_AVATAR;
        g.blit(avatar, ax, ay, ax + AVATAR_SIZE, ay + AVATAR_SIZE, 0.0f, 1.0f, 0.0f, 1.0f);

        Font font = Minecraft.getInstance().font;
        String name = ModelMetadataPresenter.getLocalizedModelString(owner.renderContext, "metadata.authors.%d.name".formatted(authorIndex), author.getName());
        String role = ModelMetadataPresenter.getLocalizedModelString(owner.renderContext, "metadata.authors.%d.role".formatted(authorIndex), author.getRole());
        String comment = ModelMetadataPresenter.getLocalizedModelString(owner.renderContext, "metadata.authors.%d.comment".formatted(authorIndex), author.getComment());

        int tx = ax + AVATAR_SIZE + 8;
        int textRight = getX() + width - 6;
        int maxTextW = Math.max(40, textRight - tx);
        g.text(font, Component.literal(name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), tx, getY() + 6, -1, false);
        if (StringUtils.isNotBlank(role)) {
            int nameW = font.width(name);
            g.text(font, Component.literal(role).withStyle(ChatFormatting.GREEN), tx + nameW + 8, getY() + 6, -1, false);
        }
        int commentY = getY() + 17;
        if (StringUtils.isNotBlank(comment)) {
            List<FormattedCharSequence> lines = font.split(Component.literal(comment), maxTextW);
            int max = Math.min(lines.size(), 2);
            for (int i = 0; i < max; i++) {
                g.text(font, lines.get(i), tx, commentY, 0xFFCCCCCC, false);
                commentY += 10;
            }
        }

        hoveredContactIndex = -1;
        OrderedStringMap<String, String> contacts = author.getContact();
        if (contacts != null && contacts.size() > 0) {
            int chipY = getY() + height - 14;
            int chipX = tx;
            for (int i = 0; i < contacts.size(); i++) {
                String key = contacts.getKeyAt(i);
                int chipW = font.width(key) + 8;
                if (chipX + chipW > textRight) break;
                boolean chipHover = mouseX >= chipX && mouseX < chipX + chipW && mouseY >= chipY && mouseY < chipY + 12;
                g.fill(chipX, chipY, chipX + chipW, chipY + 12, chipHover ? 0xC0444444 : 0x80222222);
                g.text(font, Component.literal(key).withStyle(ChatFormatting.YELLOW), chipX + 4, chipY + 2, -1, false);
                if (chipHover) hoveredContactIndex = i;
                chipX += chipW + 4;
            }
        }
    }

    @Override
    protected void renderControl(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean flag) {
        if (hoveredContactIndex < 0) return;
        OrderedStringMap<String, String> contacts = author.getContact();
        if (contacts == null || hoveredContactIndex >= contacts.size()) return;
        String value = contacts.getValueAt(hoveredContactIndex);
        if (StringUtils.isBlank(value)) return;
        if (value.startsWith("http://") || value.startsWith("https://")) {
            owner.openUrlWithConfirm(value);
        } else {
            Minecraft.getInstance().keyboardHandler.setClipboard(value);
        }
    }
}
