package com.micaftic.morpher.core.gui;

import com.micaftic.morpher.client.gui.ModelMetadataPresenter;
import com.micaftic.morpher.client.gui.PlayerModelScreen;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.texture.OuterFileTexture;
import com.micaftic.morpher.client.upload.IResourceLocatable;
import com.micaftic.morpher.client.upload.UploadManager;
import com.micaftic.morpher.model.format.ServerModelInfo;
import com.micaftic.morpher.resource.models.AuthorInfo;
import com.micaftic.morpher.resource.models.Metadata;
import com.micaftic.morpher.util.PlatformUtil;
import com.micaftic.morpher.util.data.OrderedStringMap;
import com.micaftic.morpher.util.data.StringPair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.StringUtils;
import com.micaftic.morpher.core.gui.components.*;
import com.micaftic.morpher.core.gui.components.buttons.FooterButton;
import com.micaftic.morpher.core.gui.components.groups.InfoGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ModernModelInfoScreen extends OptionScreen {

    public final ModelAssembly renderContext;
    private final ServerModelInfo modelData;
    private final List<IResourceLocatable> avatarLocatables = new ArrayList<>();

    public ModernModelInfoScreen(PlayerModelScreen parent, ModelAssembly modelAssembly) {
        super(Component.translatable("gui.sparkle_morpher.model_info.title"), parent);
        this.renderContext = modelAssembly;
        this.modelData = modelAssembly.getModelData();
        resolveAvatarTextures();
    }

    private void resolveAvatarTextures() {
        avatarLocatables.clear();
        List<AuthorInfo> authors = modelData.getExtraInfo().getAuthors();
        Map<String, OuterFileTexture> avatars = renderContext.getTextureRegistry().getAuthorAvatars();
        for (AuthorInfo author : authors) {
            OuterFileTexture avatar = avatars.get(author.getName());
            avatarLocatables.add(avatar != null ? UploadManager.getOrCreateLocatable(avatar, true) : null);
        }
    }

    @Override
    protected int computePanelWidth() {
        return Math.min(this.width - 40, 640);
    }

    @Override
    protected int computePanelHeight() {
        return Math.min(this.height - 40, 380);
    }

    @Override
    protected boolean showTabs() {
        return false;
    }

    @Override
    protected void registerGroups() {
        Metadata meta = modelData.getExtraInfo();
        if (meta == null) return;

        InfoGroup page = new InfoGroup("page");

        String name = ModelMetadataPresenter.getLocalizedModelString(renderContext, "metadata.name", meta.getName());
        if (StringUtils.isNotBlank(name)) {
            page.add(new HeaderRow(name));
        }
        StringPair license = meta.getLicense();
        if (license != null && StringUtils.isNotBlank(license.getFirst())) {
            String licenseValue = StringUtils.isNotBlank(license.getSecond()) ? license.getFirst() + "  —  " + license.getSecond() : license.getFirst();
            page.add(new LabelValueRow("gui.sparkle_morpher.model_info.license", licenseValue));
        }
        String tips = ModelMetadataPresenter.getLocalizedModelString(renderContext, "metadata.tips", meta.getTips());
        if (StringUtils.isNotBlank(tips)) {
            page.add(new TipsRow(tips));
        }

        OrderedStringMap<String, String> links = meta.getLink();
        if (links != null && !links.isEmpty()) {
            for (int i = 0; i < links.size(); i++) {
                page.add(new LinkRow(this, links.getKeyAt(i), links.getValueAt(i)));
            }
        }

        List<AuthorInfo> authors = meta.getAuthors();
        for (int i = 0; i < authors.size(); i++) {
            page.add(new AuthorRow(this, authors.get(i), i, avatarLocatables.get(i)));
        }

        if (!page.getRows().isEmpty()) groups.add(page);
    }

    @Override
    protected void init() {
        super.init();
        removeWidget(applyBtn);
        removeWidget(undoBtn);
        removeWidget(cancelBtn);
        applyBtn.visible = false;
        undoBtn.visible = false;
        cancelBtn.visible = false;
        applyBtn.active = false;
        undoBtn.active = false;
        saveBtn.setMessage(Component.translatable("gui.sparkle_morpher.config.done"));
        saveBtn.setX(panelRight - saveBtn.getWidth());
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parentScreen);
    }

    @Override
    protected void collectBlurRegions(List<int[]> out) {
        out.add(new int[]{panelLeft, panelTop, panelRight - panelLeft, 18});
        int rowScroll = Math.round(rowScrollDisplay);
        for (OptionRow<?> row : activeRows) {
            int y = row.getY() - rowScroll;
            int yBot = y + row.getHeight();
            if (yBot <= rowAreaTop || y >= rowAreaBottom) continue;
            int top = Math.max(y, rowAreaTop);
            int bot = Math.min(yBot, rowAreaBottom);
            out.add(new int[]{row.getX(), top, row.getWidth(), bot - top});
        }
        FooterButton btn = saveBtn;
        if (btn != null && btn.visible) {
            out.add(new int[]{btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight()});
        }
    }

    public void openUrlWithConfirm(String url) {
        if (StringUtils.isBlank(url)) return;
        Minecraft.getInstance().setScreen(new ConfirmLinkScreen(confirmed -> {
            if (confirmed) PlatformUtil.openUri(url);
            Minecraft.getInstance().setScreen(this);
        }, url, true));
    }
}
