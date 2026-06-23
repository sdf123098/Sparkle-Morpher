package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.client.gui.button.AuthorButton;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.texture.OuterFileTexture;
import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.upload.UploadManager;
import com.micaftic.morpher.resource.models.AuthorInfo;
import com.micaftic.morpher.resource.models.Metadata;
import com.micaftic.morpher.client.gui.button.FlatColorButton;
import com.micaftic.morpher.model.format.ServerModelInfo;
import com.micaftic.morpher.client.upload.IResourceLocatable;
import com.micaftic.morpher.mixin.client.ScreenAccessor;
import com.micaftic.morpher.util.PlatformUtil;
import com.google.common.collect.ImmutableMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ModelInfoScreen extends Screen {

    private static final Identifier DEFAULT_AVATAR = Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "texture/default_avatar.png");

    private static final Map<String, Component> URL_LABELS = ImmutableMap.of("home", Component.translatable("gui.sparkle_morpher.url.home"), "donate", Component.translatable("gui.sparkle_morpher.url.donate"));

    private final List<IResourceLocatable> textureList;

    private final PlayerModelScreen parentScreen;

    private final ModelAssembly renderContext;

    private final ServerModelInfo modelData;

    private int selectedTextureIndex;

    private int guiLeft;

    private int guiTop;

    public ModelInfoScreen(PlayerModelScreen playerModelScreen, ModelAssembly modelAssembly) {
        super(Component.literal("Model Info GUI"));
        this.textureList = new ArrayList();
        this.selectedTextureIndex = 0;
        this.parentScreen = playerModelScreen;
        this.renderContext = modelAssembly;
        this.modelData = modelAssembly.getModelData();
        initWidgets();
    }

    private void initWidgets() {
        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        this.textureList.clear();
        List<AuthorInfo> authorInfo = this.modelData.getExtraInfo().getAuthors();
        Map<String, OuterFileTexture> avatars = this.renderContext.getTextureRegistry().getAuthorAvatars();
        for (int i = 0; i < authorInfo.size(); i++) {
            OuterFileTexture avatar = avatars.get(authorInfo.get(i).getName());
            if (avatar != null) {
                textureManager.register(Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "avatars/" + i), avatar);
                this.textureList.add(UploadManager.getOrCreateLocatable(avatar, true));
            } else {
                this.textureList.add(null);
            }
        }
    }

    public void init() {
        clearWidgets();
        this.guiLeft = (this.width - 420) / 2;
        this.guiTop = (this.height - 235) / 2;
        Metadata metadata = this.modelData.getExtraInfo();
        List<AuthorInfo> authorInfos = metadata.getAuthors();
        if (authorInfos.size() <= this.selectedTextureIndex) {
            this.selectedTextureIndex = 0;
        }
        int slot = 0;
        while (slot < 5) {
            int authorIndex = this.selectedTextureIndex + slot;
            if (authorIndex >= authorInfos.size()) {
                while (slot < 5) {
                    addRenderableWidget(AuthorButton.createAuthorButton(this.guiLeft + 25 + (75 * slot), this.guiTop + 15, this));
                    slot++;
                }
            } else {
                AuthorInfo authorInfo = authorInfos.get(authorIndex);
                IResourceLocatable resourceLocatable = this.textureList.get(authorIndex);
                addRenderableWidget(new AuthorButton(this.guiLeft + 25 + (75 * slot), this.guiTop + 15, authorInfo, this.renderContext, resourceLocatable != null ? resourceLocatable.getResourceLocation().get() : DEFAULT_AVATAR, authorIndex, this));
            }
            slot++;
        }
        addRenderableWidget(new FlatColorButton(this.guiLeft + 2, this.guiTop + 25, 18, 100, Component.literal("<"), button -> {
            this.selectedTextureIndex = Math.max(0, this.selectedTextureIndex - 5);
            init();
        }).setTooltipText("gui.sparkle_morpher.pre_page"));
        addRenderableWidget(new FlatColorButton(this.guiLeft + 25 + 375, this.guiTop + 25, 18, 100, Component.literal(">"), button2 -> {
            this.selectedTextureIndex += 5;
            init();
        }).setTooltipText("gui.sparkle_morpher.next_page"));
        int linkY = this.guiTop + 150;
        for (int linkIndex = 0; linkIndex < Math.min(metadata.getLink().size(), 2); linkIndex++) {
            String str = metadata.getLink().getKeyAt(linkIndex);
            String str2 = metadata.getLink().getValueAt(linkIndex);
            Component component = URL_LABELS.get(str);
            if (component == null) {
                component = Component.literal(str);
            }
            addRenderableWidget(new FlatColorButton(this.guiLeft + 310, linkY, 85, 20, component, button3 -> {
                openUrl(str2);
            }));
            linkY += 25;
        }
        addRenderableWidget(new FlatColorButton(this.guiLeft + 310, linkY, 85, 20, Component.translatable("gui.sparkle_morpher.model.return"), button4 -> {
            Minecraft.getInstance().setScreen(this.parentScreen);
        }));
    }

    private void openUrl(@Nullable String str) {
        if (str != null && StringUtils.isNoneBlank(str)) {
            Minecraft.getInstance().setScreen(new ConfirmLinkScreen(confirmed -> {
                if (confirmed) {
                    PlatformUtil.openUri(str);
                }
                Minecraft.getInstance().setScreen(this);
            }, str, true));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        GuiGraphicsExtractor guiGraphics = extractor;
        extractTransparentBackground(extractor);
        guiGraphics.fillGradient(this.guiLeft + 25, this.guiTop + 150, this.guiLeft + 305, this.guiTop + 220, -1889838245, -1889838245);
        Metadata metadata2 = this.modelData.getExtraInfo();
        if (metadata2 != null) {
            int lineOffset = 0;
            Iterator it = this.font.split(Component.literal(ModelMetadataPresenter.getLocalizedModelString(this.renderContext, "metadata.tips", metadata2.getTips())), 270).iterator();
            while (it.hasNext()) {
                guiGraphics.text(this.font, (FormattedCharSequence) it.next(), this.guiLeft + 30, this.guiTop + 154 + lineOffset, -1);
                Objects.requireNonNull(this.font);
                lineOffset += 9;
                Objects.requireNonNull(this.font);
                if (lineOffset > 9 * 7) {
                    break;
                }
            }
        }
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        ((ScreenAccessor) this).ysm$getRenderables().stream().filter(renderable -> {
            return renderable instanceof AuthorButton;
        }).forEach(renderable2 -> {
            ((AuthorButton) renderable2).refreshContactComponents(guiGraphics, this, mouseX, mouseY);
        });
        ((ScreenAccessor) this).ysm$getRenderables().stream().filter(renderable -> {
            return renderable instanceof FlatColorButton;
        }).forEach(renderable -> {
            ((FlatColorButton) renderable).renderTooltip(guiGraphics, this, mouseX, mouseY);
        });
    }

    public boolean isPauseScreen() {
        return false;
    }
}
