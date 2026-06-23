package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.NativeLibLoader;
import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.AuthModelsCapability;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.capability.StarModelsCapability;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.entity.PlayerPreviewEntity;
import com.micaftic.morpher.client.gui.button.*;
import com.micaftic.morpher.client.input.PlayerModelToggleKey;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.client.renderer.RendererManager;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.config.ServerConfig;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.mixin.client.ScreenAccessor;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.resource.models.AuthorInfo;
import com.micaftic.morpher.resource.models.Metadata;
import com.micaftic.morpher.resource.models.ModelPackData;
import com.micaftic.morpher.util.FileTypeUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.architectury.platform.Platform;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import com.micaftic.morpher.core.gpu.GpuCapability;

import java.util.*;

public class PlayerModelScreen extends Screen implements IGuiWidget {

    private static final String AUTHOR_SEARCH_PREFIX = "@";

    private static final String TAG_SEARCH_PREFIX = "#";

    private final HashSet<String> hiddenModels;

    private final Map<String, ModelPackData> modelPackMap;

    private Map<String, ModelAssembly> filteredModels;

    private Map<String, ModelPackData> filteredPacks;

    private List<String> sortedModelKeys;

    private List<String> sortedPackKeys;

    public int guiLeft;

    public int guiTop;

    private int maxPage;

    private EditBox searchBox;

    private Category category;

    private boolean selectionMode;

    private final Set<String> selectedModelIds = new HashSet<>();

    private Component selectionStatus = Component.empty();

    private static final PlayerPreviewEntity[] previewHolders = new PlayerPreviewEntity[10];

    private static final Object2IntMap<String> pageIndexMap = new Object2IntOpenHashMap();

    private static String currentPath = StringPool.EMPTY;

    static {
        for (int i = 0; i < previewHolders.length; i++) {
            previewHolders[i] = new PlayerPreviewEntity();
        }
    }

    public PlayerModelScreen() {
        super(Component.literal("Model Selection GUI"));
        this.hiddenModels = Sets.newHashSet();
        this.filteredModels = Maps.newHashMap();
        this.filteredPacks = Maps.newHashMap();
        this.category = Category.ALL;
        if (NetworkHandler.isClientConnected()) {
            this.hiddenModels.addAll(ServerConfig.CLIENT_NOT_DISPLAY_MODELS.get());
        }
        ClientModelManager.registerGuiWidget(this);
        this.modelPackMap = new Object2ReferenceOpenHashMap<>(ClientModelManager.getModelPackMap());
    }

    public ModelButton createModelButton(int x, int y, boolean isAuthLocked, PlayerPreviewEntity previewEntity, ModelAssembly modelAssembly) {
        return new ModelButton(x, y, isAuthLocked, previewEntity, modelAssembly);
    }

    public PlayerTextureScreen createTextureScreen(PlayerModelScreen other, String str, ModelAssembly modelAssembly) {
        return new PlayerTextureScreen(other, str, modelAssembly);
    }

    public ModelInfoScreen createModelInfoScreen(PlayerModelScreen other, ModelAssembly modelAssembly) {
        return new ModelInfoScreen(other, modelAssembly);
    }

    private Map<String, ModelAssembly> buildFilteredModelMap() {
        HashMap mapNewHashMap = Maps.newHashMap();
        if (StringUtils.isBlank(currentPath)) {
            mapNewHashMap.putAll(ClientModelManager.getModelAssemblyMap());
        }
        ClientModelManager.getModelAssemblyMap().forEach((str, modelAssembly) -> {
            if (str.startsWith(currentPath)) {
                mapNewHashMap.put(str, modelAssembly);
            }
            String str2 = FileTypeUtil.splitFileNameAndParentDir(str).right();
            if (StringUtils.isNotBlank(str2)) {
                ensurePackHierarchy(str2, this.modelPackMap);
            }
        });
        return mapNewHashMap;
    }

    private static void ensurePackHierarchy(String str, Map<String, ModelPackData> map) {
        if (StringUtils.isBlank(str) || !str.contains("/")) {
            return;
        }
        String[] strArrSplit = str.split("/");
        StringBuilder sb = new StringBuilder();
        for (String str2 : strArrSplit) {
            if (!str2.isEmpty()) {
                sb.append(str2).append("/");
                String string = sb.toString();
                map.putIfAbsent(string, new ModelPackData(string, FileTypeUtil.getFinalPathSegment(string), StringPool.EMPTY, null, null));
            }
        }
    }

    private Map<String, ModelPackData> buildFilteredPackMap() {
        HashMap<String, ModelPackData> mapNewHashMap = Maps.newHashMap();
        if (StringUtils.isBlank(currentPath)) {
            return Maps.newHashMap(this.modelPackMap);
        }
        this.modelPackMap.forEach((str, c0616x1389bc7f) -> {
            if (str.startsWith(currentPath)) {
                mapNewHashMap.put(str, c0616x1389bc7f);
            }
        });
        return mapNewHashMap;
    }

    private void refreshModelList() {
        String lowerCase;
        this.filteredModels = Maps.newHashMap();
        this.filteredPacks = Maps.newHashMap();
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        LocalPlayer localPlayer = this.minecraft.player;
        if (this.category == Category.ALL) {
            this.filteredModels = buildFilteredModelMap();
            this.filteredPacks = buildFilteredPackMap();
        }
        if (this.category == Category.AUTH) {
            AuthModelsCapability.get(localPlayer).ifPresent(cap -> {
                for (Map.Entry<String, ModelAssembly> entry : ClientModelManager.getModelAssemblyMap().entrySet()) {
                    if (cap.containsModel(entry.getKey()) || !entry.getValue().getTextureRegistry().isAuthModel()) {
                        this.filteredModels.put(entry.getKey(), entry.getValue());
                    }
                }
            });
        }
        if (this.category == Category.STAR) {
            StarModelsCapability.get(localPlayer).ifPresent(cap2 -> {
                for (Map.Entry<String, ModelAssembly> entry : ClientModelManager.getModelAssemblyMap().entrySet()) {
                    if (cap2.containsModel(entry.getKey())) {
                        this.filteredModels.put(entry.getKey(), entry.getValue());
                    }
                }
            });
        }
        if (this.searchBox != null) {
            lowerCase = this.searchBox.getValue().toLowerCase(Locale.ENGLISH);
        } else {
            lowerCase = StringPool.EMPTY;
        }
        if (StringUtils.isBlank(lowerCase)) {
            this.filteredModels.entrySet().removeIf(entry -> {
                Pair<String, String> pair = FileTypeUtil.splitFileNameAndParentDir(entry.getKey());
                return this.hiddenModels.contains(pair.left()) || !pair.right().equals(currentPath);
            });
            this.filteredPacks.entrySet().removeIf(entry2 -> {
                return !isDirectChild(currentPath, entry2.getKey());
            });
        } else {
            String str = lowerCase;
            this.filteredModels.entrySet().removeIf(entry3 -> {
                return shouldFilterModel(FileTypeUtil.splitFileNameAndParentDir(entry3.getKey()).left(), entry3.getValue(), str);
            });
            String str2 = lowerCase;
            this.filteredPacks.entrySet().removeIf(entry4 -> {
                return shouldFilterPack(FileTypeUtil.splitFileNameAndParentDir(entry4.getKey()).left(), entry4.getValue(), str2);
            });
        }
        this.sortedModelKeys = Lists.newArrayList(this.filteredModels.keySet());
        this.sortedModelKeys.sort((v0, v1) -> {
            return v0.compareTo(v1);
        });
        this.sortedPackKeys = Lists.newArrayList(this.filteredPacks.keySet());
        this.sortedPackKeys.sort((v0, v1) -> {
            return v0.compareTo(v1);
        });
        this.maxPage = ((this.filteredModels.size() + this.filteredPacks.size()) - 1) / 10;
    }

    private boolean isDirectChild(String str, String str2) {
        String strSubstring;
        int iIndexOf;
        if (str.equals(str2)) {
            return false;
        }
        if (!StringUtils.isBlank(str)) {
            return str2.startsWith(str) && (iIndexOf = (strSubstring = str2.substring(str.length())).indexOf(47)) == strSubstring.length() - 1 && strSubstring.lastIndexOf(47) == iIndexOf;
        }
        int iIndexOf2 = str2.indexOf(47);
        return iIndexOf2 == str2.length() - 1 && str2.lastIndexOf(47) == iIndexOf2;
    }

    private boolean shouldFilterPack(String str, ModelPackData packData, String str2) {
        if (StringUtils.isBlank(str2)) {
            return false;
        }
        if (str2.startsWith(TAG_SEARCH_PREFIX)) {
            str2 = str2.substring(TAG_SEARCH_PREFIX.length());
        }
        if (str.toLowerCase(Locale.ENGLISH).contains(str2)) {
            return false;
        }
        if (packData.getTranslations() != null) {
            if (ModelMetadataPresenter.getLocalizedString(packData, "name", packData.getName()).toLowerCase(Locale.ENGLISH).contains(str2)) {
                return false;
            }
            String str3 = packData.getDescription();
            return str3 == null || !ModelMetadataPresenter.getLocalizedString(packData, "description", str3).toLowerCase(Locale.ENGLISH).contains(str2);
        }
        return true;
    }

    private boolean shouldFilterModel(String str, ModelAssembly modelAssembly, String str2) {
        if (this.hiddenModels.contains(str)) {
            return true;
        }
        if (StringUtils.isBlank(str2)) {
            return false;
        }
        if (str2.startsWith(TAG_SEARCH_PREFIX)) {
            return true;
        }
        if (str2.startsWith(AUTHOR_SEARCH_PREFIX)) {
            String strSubstring = str2.substring(AUTHOR_SEARCH_PREFIX.length());
            Metadata metadata2 = modelAssembly.getModelData().getExtraInfo();
            if (metadata2 != null) {
                return matchesAuthorSearch(modelAssembly, strSubstring, metadata2);
            }
            return true;
        }
        if (str.toLowerCase(Locale.ENGLISH).contains(str2)) {
            return false;
        }
        Metadata metadata3 = modelAssembly.getModelData().getExtraInfo();
        if (metadata3 != null) {
            if (ModelMetadataPresenter.getLocalizedModelString(modelAssembly, "metadata.name", metadata3.getName()).toLowerCase(Locale.ENGLISH).contains(str2) || ModelMetadataPresenter.getLocalizedModelString(modelAssembly, "metadata.tips", metadata3.getTips()).toLowerCase(Locale.ENGLISH).contains(str2)) {
                return false;
            }
            return matchesAuthorSearch(modelAssembly, str2, metadata3);
        }
        return true;
    }

    public String getParentPath(String str) {
        if (str == null || str.isEmpty()) {
            return StringPool.EMPTY;
        }
        String strSubstring = str.endsWith("/") ? str.substring(0, str.length() - 1) : str;
        int iLastIndexOf = strSubstring.lastIndexOf(47);
        if (iLastIndexOf < 0) {
            return StringPool.EMPTY;
        }
        return strSubstring.substring(0, iLastIndexOf + 1);
    }

    private boolean matchesAuthorSearch(ModelAssembly modelAssembly, String str, Metadata metadata2) {
        int i = 0;
        Iterator<AuthorInfo> it = metadata2.getAuthors().iterator();
        while (it.hasNext()) {
            if (ModelMetadataPresenter.getLocalizedModelString(modelAssembly, "metadata.authors.%d.name".formatted(Integer.valueOf(i)), it.next().getName()).toLowerCase(Locale.ENGLISH).contains(str)) {
                return false;
            }
            i++;
        }
        return true;
    }

    public void init() {
        clearWidgets();
        refreshModelList();
        if (getCurrentPage() > this.maxPage) {
            resetCurrentPage();
        }
        this.guiLeft = (this.width - 420) / 2;
        this.guiTop = (this.height - 235) / 2;
        String value = StringPool.EMPTY;
        boolean zIsFocused = false;
        if (this.searchBox != null) {
            value = this.searchBox.getValue();
            zIsFocused = this.searchBox.isFocused();
        }
        this.searchBox = new EditBox(Minecraft.getInstance().font, this.guiLeft + 144, this.guiTop + 6, 140, 16, Component.literal("Search Box"));
        this.searchBox.setValue(value);
        this.searchBox.setTextColor(15986656);
        this.searchBox.setFocused(zIsFocused);
        this.searchBox.moveCursorToEnd(false);
        addWidget(this.searchBox);
        addRenderableWidget(new IconButton(this.guiLeft + 5, this.guiTop + 5, 20, 20, 80, 16, button -> {
            if (Minecraft.getInstance().player != null) {
                PlayerCapability.get(Minecraft.getInstance().player).ifPresent(cap -> {
                    ModelAssembly modelAssembly = cap.getModelAssembly();
                    if (modelAssembly.getModelData().getExtraInfo() != null) {
                        Minecraft.getInstance().setScreen(createModelInfoScreen(this, modelAssembly));
                    }
                });
            }
        })).setTooltipText("gui.sparkle_morpher.model.info");
        addRenderableWidget(new IconButton(this.guiLeft + 28, this.guiTop + 5, 79, 20, 32, 16, button2 -> {
            if (Minecraft.getInstance().player != null) {
                PlayerCapability.get(Minecraft.getInstance().player).ifPresent(cap -> {
                    Minecraft.getInstance().setScreen(createTextureScreen(this, cap.getModelId(), cap.getModelAssembly()));
                });
            }
        }).setTooltipText("gui.sparkle_morpher.model.texture"));
        addRenderableWidget(new ModIconButton(this.guiLeft + 110, this.guiTop + 5));
        if (StringUtils.isNotBlank(currentPath)) {
            addRenderableWidget(new IconButton(this.guiLeft + 110, this.guiTop + 27, 20, 20, 0, 32, button3 -> {
                navigateUp();
            }).setTooltipText("gui.back"));
        }
        addRenderableWidget(Checkbox.builder(Component.translatable("gui.sparkle_morpher.show_model_id_first"), font).pos(this.guiLeft + 5, this.guiTop - 18).maxWidth(125).selected(GeneralConfig.SHOW_MODEL_ID_FIRST.get()).onValueChange((c, v) -> {
            GeneralConfig.SHOW_MODEL_ID_FIRST.set(v);
            GeneralConfig.SHOW_MODEL_ID_FIRST.save();
        }).build());
        IconButton selectButton = new IconButton(this.guiLeft + 132, this.guiTop - 20, 18, 18, 0, 48, button -> {
            this.selectionMode = !this.selectionMode;
            if (!this.selectionMode) {
                this.selectedModelIds.clear();
            }
            init();
        });
        selectButton.setSelected(this.selectionMode);
        selectButton.setTooltipText("gui.sparkle_morpher.model_select.tooltip.select");
        addRenderableWidget(selectButton);
        addRenderableWidget(new IconButton(this.guiLeft + 324, this.guiTop + 5, 18, 18, 32, 0, button4 -> {
            if (this.category != Category.ALL) {
                this.category = Category.ALL;
                resetCurrentPage();
                init();
            }
        }).setTooltipText("gui.sparkle_morpher.all_models"));
        addRenderableWidget(new IconButton(this.guiLeft + 306, this.guiTop + 5, 18, 18, 48, 0, button5 -> {
            if (this.category != Category.AUTH) {
                this.category = Category.AUTH;
                resetCurrentPage();
                init();
            }
        }).setTooltipText("gui.sparkle_morpher.auth_models"));
        addRenderableWidget(new IconButton(this.guiLeft + 288, this.guiTop + 5, 18, 18, 0, 0, button6 -> {
            if (this.category != Category.STAR) {
                this.category = Category.STAR;
                resetCurrentPage();
                init();
            }
        }).setTooltipText("gui.sparkle_morpher.star_models"));
        addRenderableWidget(new IconButton(this.guiLeft + 396, this.guiTop + 5, 18, 18, 16, 16, button7 -> {
            Minecraft.getInstance().setScreen(new ExtraPlayerConfigScreen(this));
        }).setTooltipText("gui.sparkle_morpher.config"));
        IconButton importButton = new IconButton(this.guiLeft + 360, this.guiTop + 5, 18, 18, 0, 16, button8 -> {
            Minecraft.getInstance().setScreen(new ModelUploadScreen(this));
        });
        importButton.setTooltipLines(java.util.Collections.singletonList(getImportTooltip()));
        addRenderableWidget(importButton);
        IconButton customFolderUploadButton = new IconButton(this.guiLeft + 378, this.guiTop + 5, 18, 18, 96, 16, btn -> {
            Minecraft.getInstance().setScreen(new CustomFolderUploadScreen(this));
        });
        customFolderUploadButton.setTooltipLines(java.util.Collections.singletonList(getCustomFolderUploadTooltip()));
        addRenderableWidget(customFolderUploadButton);
        addRenderableWidget(new IconButton(this.guiLeft + 342, this.guiTop + 5, 18, 18, 80, 32, button9 -> {
            Minecraft.getInstance().setScreen(new ResourceStationScreen(this));
        }).setTooltipText("gui.sparkle_morpher.resource_station.tooltip"));
        if (this.selectionMode) {
            int selectionToolbarX = this.guiLeft + 153;
            int selectionToolbarY = this.guiTop - 19;
            addRenderableWidget(new IconButton(selectionToolbarX, selectionToolbarY, 16, 16, 16, 48, button -> runDeleteSelection()).setTooltipText("gui.sparkle_morpher.model_select.tooltip.delete"));
            addRenderableWidget(new IconButton(selectionToolbarX + 19, selectionToolbarY, 16, 16, 32, 48, button -> runMoveSelection()).setTooltipText("gui.sparkle_morpher.model_select.tooltip.move"));
            addRenderableWidget(new IconButton(selectionToolbarX + 38, selectionToolbarY, 16, 16, 48, 48, button -> runCreateCategory()).setTooltipText("gui.sparkle_morpher.model_select.tooltip.new_category"));
            addRenderableWidget(new IconButton(selectionToolbarX + 57, selectionToolbarY, 16, 16, 64, 48, button -> runRenameCategory()).setTooltipText("gui.sparkle_morpher.model_select.tooltip.rename_category"));
            addRenderableWidget(new IconButton(selectionToolbarX + 76, selectionToolbarY, 16, 16, 80, 48, button -> runDeleteCategory()).setTooltipText("gui.sparkle_morpher.model_select.tooltip.delete_category"));
            addRenderableWidget(new IconButton(selectionToolbarX + 95, selectionToolbarY, 16, 16, 96, 48, button -> selectAllFilteredModels()).setTooltipText("gui.sparkle_morpher.model_select.tooltip.select_all"));
            addRenderableWidget(new IconButton(selectionToolbarX + 114, selectionToolbarY, 16, 16, 112, 48, button -> {
                this.selectedModelIds.clear();
                this.selectionMode = false;
                init();
            }).setTooltipText("gui.sparkle_morpher.model_select.tooltip.cancel"));
        }
        addRenderableWidget(new FlatColorButton(this.guiLeft + 198, this.guiTop + 215, 52, 14, Component.translatable("gui.sparkle_morpher.pre_page"), button10 -> {
            int currentPage = getCurrentPage();
            if (currentPage > 0) {
                setCurrentPage(currentPage - 1);
                init();
            }
        }));
        addRenderableWidget(new FlatColorButton(this.guiLeft + 308, this.guiTop + 215, 52, 14, Component.translatable("gui.sparkle_morpher.next_page"), button11 -> {
            int currentPage = getCurrentPage();
            if (currentPage < this.maxPage) {
                setCurrentPage(currentPage + 1);
                init();
            }
        }));
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        Optional<AuthModelsCapability> capability = AuthModelsCapability.get(this.minecraft.player);
        for (int i = 0; i < 10; i++) {
            int slotIndex = i + (getCurrentPage() * 10);
            int slotX = this.guiLeft + 143 + (55 * (i % 5));
            int slotY = this.guiTop + 28 + (93 * (i / 5));
            if (slotIndex < this.sortedPackKeys.size()) {
                String str = this.sortedPackKeys.get(slotIndex);
                getPackData(str).ifPresent(value2 -> {
                    addRenderableWidget(new PackIconButton(slotX, slotY, 52, 90, value2, button12 -> {
                        currentPath = str;
                        resetCurrentPage();
                        init();
                    }));
                });
            }
            int size = slotIndex - this.sortedPackKeys.size();
            if (0 <= size && size < this.sortedModelKeys.size()) {
                String str2 = this.sortedModelKeys.get(size);
                PlayerPreviewEntity previewEntity = previewHolders[i];
                previewEntity.resetModel();
                capability.ifPresent(value3 -> {
                    ModelAssembly modelAssembly2 = this.filteredModels.get(str2);
                    boolean isAuthLocked = modelAssembly2.getTextureRegistry().isAuthModel() && !value3.getAuthModels().contains(str2);
                    previewEntity.initModelWithTexture(str2, modelAssembly2.getAnimationBundle().getDefaultTextureName());
                    previewEntity.getAnimationStateMachine().setCurrentAnimation(modelAssembly2.getModelData().getModelProperties().getPreviewAnimation());
                    if (this.selectionMode) {
                        addRenderableWidget(new SelectableModelButton(slotX, slotY, false, previewEntity, modelAssembly2, str2, () -> this.selectedModelIds.contains(str2), this::toggleModelSelection));
                    } else {
                        addRenderableWidget(createModelButton(slotX, slotY, isAuthLocked, previewEntity, modelAssembly2));
                    }
                });
            }
        }
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(guiGraphics);
        guiGraphics.fillGradient(this.guiLeft, this.guiTop, this.guiLeft + 135, this.guiTop + 235, -14540254, -14540254);
        guiGraphics.fillGradient(this.guiLeft + 138, this.guiTop, this.guiLeft + 420, this.guiTop + 235, -14540254, -14540254);
        guiGraphics.fillGradient(this.guiLeft + 351, this.guiTop + 7, this.guiLeft + 352, this.guiTop + 21, -790560, -790560);
        this.searchBox.render(guiGraphics, mouseX, mouseY, partialTick);
        renderModelPreview(guiGraphics, mouseX, mouseY, partialTick);
        if (this.searchBox.getValue().isEmpty() && !this.searchBox.isFocused()) {
            guiGraphics.drawString(this.font, Component.translatable("gui.sparkle_morpher.search").withStyle(ChatFormatting.ITALIC), this.guiLeft + 148, this.guiTop + 10, 7829367);
        }
        String str = String.format("%d/%d", getCurrentPage() + 1, Integer.valueOf(this.maxPage + 1));
        Font font = this.font;
        int iWidth = this.guiLeft + 138 + ((282 - this.font.width(str)) / 2);
        int pageY = this.guiTop + 223;
        Objects.requireNonNull(this.font);
        guiGraphics.drawString(font, str, iWidth, pageY - (9 / 2), 15986656);
        String renderer = (NativeLibLoader.isLoaded() && !GeneralConfig.USE_COMPATIBILITY_RENDERER.get()) ? "SIMD" : "Fallback";
        if(renderer.equals("SIMD") && GpuCapability.isAvailable() && GeneralConfig.USE_GPU_RENDERER.get()) {
            renderer = "GPU";
        }
        String strVersionString = Platform.getMod(YesSteveModel.MOD_ID).getVersion();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0f, 0.0f, 1000.0f);
        guiGraphics.drawString(this.font, strVersionString + " (" + renderer + ")", this.guiLeft + 2, this.guiTop + 226, ChatFormatting.DARK_GRAY.getColor().intValue());
        guiGraphics.pose().popPose();
        if (StringUtils.isNotBlank(currentPath)) {
            int lineIndex = 0;
            List listSplit = this.font.split(Component.literal("📂 " + currentPath).withStyle(ChatFormatting.GRAY), 270);
            Iterator it = listSplit.iterator();
            while (it.hasNext()) {
                guiGraphics.drawString(this.font, (FormattedCharSequence) it.next(), this.guiLeft + 142, this.guiTop + (((-(listSplit.size() - lineIndex)) * 10) - 2), 15986656);
                lineIndex++;
            }
        }
        renderSyncStatus(guiGraphics);
        renderSelectionStatus(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        ((ScreenAccessor) this).ysm$getRenderables().stream().filter(renderable -> {
            return renderable instanceof IconButton;
        }).forEach(renderable2 -> {
            ((IconButton) renderable2).renderTooltip(guiGraphics, this, mouseX, mouseY);
        });
        ((ScreenAccessor) this).ysm$getRenderables().stream().filter(renderable3 -> {
            return renderable3 instanceof ModelButton;
        }).forEach(renderable4 -> {
            ((ModelButton) renderable4).renderTooltip(guiGraphics, this, mouseX, mouseY);
        });
        ((ScreenAccessor) this).ysm$getRenderables().stream().filter(renderable5 -> {
            return renderable5 instanceof PackIconButton;
        }).forEach(renderable6 -> {
            ((PackIconButton) renderable6).renderDescription(guiGraphics, this, mouseX, mouseY);
        });
        if (this.searchBox.isHovered()) {
            MutableComponent mutableComponentWithStyle = Component.translatable("gui.sparkle_morpher.search.tip").withStyle(ChatFormatting.GRAY);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0f, 0.0f, 4000.0f);
            guiGraphics.renderTooltip(this.font, this.font.split(mutableComponentWithStyle, 320), mouseX, mouseY);
            guiGraphics.pose().popPose();
        }
    }

    private void renderSyncStatus(GuiGraphics guiGraphics) {
        MutableComponent mutableComponentLiteral;
        ClientModelManager.SyncStatus currentState = ClientModelManager.getSyncStatus();
        switch (currentState.getCurrentState()) {
            case WAITING:
                mutableComponentLiteral = Component.translatable("gui.sparkle_morpher.sync_hint.waiting");
                break;
            case LOADING:
                mutableComponentLiteral = Component.translatable("gui.sparkle_morpher.sync_hint.loading");
                break;
            case PREPARING:
                mutableComponentLiteral = Component.translatable("gui.sparkle_morpher.sync_hint.preparing");
                break;
            case SYNCING:
                if (currentState.getSyncedModels() == 0) {
                    mutableComponentLiteral = Component.translatable("gui.sparkle_morpher.sync_hint.syncing");
                    break;
                } else {
                    mutableComponentLiteral = Component.literal(String.format("%s/%s", currentState.getSyncedModels(), currentState.getTotalModels()));
                    break;
                }
            default:
                return;
        }
        int iWidth = (this.guiLeft + 414) - this.font.width(mutableComponentLiteral);
        int i = this.guiTop + 215;
        Objects.requireNonNull(this.font);
        guiGraphics.drawString(this.font, mutableComponentLiteral, iWidth, i + Math.round((14 - 9) / 2.0f), ChatFormatting.DARK_GRAY.getColor().intValue());
    }

    private void renderSelectionStatus(GuiGraphics guiGraphics) {
        boolean hasStatus = StringUtils.isNotBlank(this.selectionStatus.getString());
        if (!this.selectionMode && !hasStatus) {
            return;
        }
        Component text = hasStatus ? this.selectionStatus : Component.translatable("gui.sparkle_morpher.model_select.count", this.selectedModelIds.size());
        List<FormattedCharSequence> lines = this.font.split(text.copy().withStyle(ChatFormatting.GRAY), 126);
        if (!lines.isEmpty()) {
            guiGraphics.drawString(this.font, lines.get(0), this.guiLeft + 288, this.guiTop - 15, 0xFFF3F3E0);
        }
    }

    public void renderModelPreview(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            int previewLeft = this.guiLeft + 5;
            int previewTop = this.guiTop + 29;
            int previewRight = this.guiLeft + 130;
            int previewBottom = this.guiTop + 200;
            float customPreviewCenterX = (previewLeft + previewRight) * 0.5f;
            boolean renderedCustomPreview = PlayerCapability.get(localPlayer).map(cap -> {
                cap.tickModel();
                if (!cap.isModelReady()) {
                    return false;
                }
                guiGraphics.flush();
                double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
                RenderSystem.enableScissor(
                        (int) (previewLeft * guiScale),
                        (int) (Minecraft.getInstance().getWindow().getHeight() - (previewBottom * guiScale)),
                        (int) ((previewRight - previewLeft) * guiScale),
                        (int) ((previewBottom - previewTop) * guiScale)
                );
                try {
                    ModelPreviewRenderer.renderLivingEntityPreview(customPreviewCenterX, previewTop + 150.0f, 70.0f, partialTick, cap, RendererManager.getPlayerRenderer(), false, false, ModelPreviewRenderer.FRONT_FACING_YAW, previewLeft, previewTop, previewRight, previewBottom, mouseX, mouseY);
                } finally {
                    RenderSystem.disableScissor();
                }
                guiGraphics.flush();
                return true;
            }).orElse(false);
            if (!renderedCustomPreview) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0f, 0.0f, 100.0f);
            // 1.21.1 的签名为 (GuiGraphics, x1, y1, x2, y2, scale, yOffset, mouseX, mouseY, entity),
            // 其中 (x1,y1)-(x2,y2) 既决定模型居中位置, 也是该方法内部 enableScissor 的裁剪框.
            // 旧 (1.20.x) 调用方式参数错位会让 x2 < x1, 造成内部裁剪框为空, 模型完全不渲染.
            float previewCenterX = this.guiLeft + 67.5f;
            float previewCenterY = this.guiTop + 114.5f;
            renderFrontFacingInventoryEntity(guiGraphics, this.guiLeft + 5, this.guiTop + 29, this.guiLeft + 130, this.guiTop + 200, 70, 0.0625f, previewCenterX, previewCenterY, mouseX, mouseY, localPlayer);
            guiGraphics.pose().popPose();
            }
            PlayerCapability.get(localPlayer).ifPresent(cap -> {
                List<FormattedCharSequence> listSplit = this.font.split(FormattedText.of(ClientModelManager.getModelContext(cap.getModelId()).map(it -> {
                    Metadata metadata2 = it.getModelData().getExtraInfo();
                    if (metadata2 != null) {
                        return ModelMetadataPresenter.getLocalizedModelString(it, "metadata.name", metadata2.getName());
                    }
                    return StringPool.EMPTY;
                }).filter(charSequence -> {
                    return StringUtils.isNoneBlank(charSequence);
                }).orElse(FileTypeUtil.getNameWithoutArchiveExtension(cap.getModelId()))), 125);
                int lineY = this.guiTop + 205;
                for (FormattedCharSequence formattedCharSequence : listSplit) {
                    guiGraphics.drawString(this.font, formattedCharSequence, this.guiLeft + ((135 - this.font.width(formattedCharSequence)) / 2), lineY, 15986656);
                    lineY += 10;
                }
            });
        }
    }

    private static void renderFrontFacingInventoryEntity(GuiGraphics guiGraphics, int left, int top, int right, int bottom, int scale, float yOffset, float centerX, float centerY, int mouseX, int mouseY, LocalPlayer player) {
        guiGraphics.enableScissor(left, top, right, bottom);
        float yawMouse = (float) Math.atan((centerX - mouseX) / 40.0f);
        float pitchMouse = (float) Math.atan((centerY - mouseY) / 40.0f);
        Quaternionf modelRotation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf cameraRotation = new Quaternionf().rotateX(pitchMouse * 20.0f * ((float) Math.PI / 180.0f));
        modelRotation.mul(cameraRotation);

        float oldBodyRot = player.yBodyRot;
        float oldBodyRotO = player.yBodyRotO;
        float oldYRot = player.getYRot();
        float oldYRotO = player.yRotO;
        float oldXRot = player.getXRot();
        float oldXRotO = player.xRotO;
        float oldHeadRotO = player.yHeadRotO;
        float oldHeadRot = player.yHeadRot;
        try {
            ModelPreviewRenderer.setInventoryPreviewFrontFacing(true);
            player.yBodyRot = 180.0f + (yawMouse * 20.0f);
            player.yBodyRotO = player.yBodyRot;
            player.setYRot(180.0f + (yawMouse * 40.0f));
            player.yRotO = player.getYRot();
            player.setXRot(-pitchMouse * 20.0f);
            player.xRotO = player.getXRot();
            player.yHeadRot = player.getYRot();
            player.yHeadRotO = player.getYRot();
            float entityScale = player.getScale();
            Vector3f offset = new Vector3f(0.0f, (player.getBbHeight() / 2.0f) + (yOffset * entityScale), 0.0f);
            InventoryScreen.renderEntityInInventory(guiGraphics, centerX, centerY, scale / entityScale, offset, modelRotation, cameraRotation, player);
        } finally {
            ModelPreviewRenderer.setInventoryPreviewFrontFacing(false);
            player.yBodyRot = oldBodyRot;
            player.yBodyRotO = oldBodyRotO;
            player.setYRot(oldYRot);
            player.yRotO = oldYRotO;
            player.setXRot(oldXRot);
            player.xRotO = oldXRotO;
            player.yHeadRotO = oldHeadRotO;
            player.yHeadRot = oldHeadRot;
            guiGraphics.disableScissor();
        }
    }

    public void resize(Minecraft minecraft, int width, int height) {
        String value = this.searchBox.getValue();
        super.resize(minecraft, width, height);
        this.searchBox.setValue(value);
    }

    public void tick() {
    }

    private void openCategoryManager() {
        if (!ModelPanelFileActions.canWriteServerModelDirectory()) {
            showCategoryActionMessage(Component.translatable("gui.sparkle_morpher.model_select.error.read_only"));
            return;
        }
        List<String> categories = ModelPanelFileActions.listCategories();
        if (StringUtils.isNotBlank(currentPath)) {
            String currentCategory = currentPath.endsWith("/") ? currentPath.substring(0, currentPath.length() - 1) : currentPath;
            Minecraft.getInstance().setScreen(new CategorySelectScreen(this,
                    Component.translatable("gui.sparkle_morpher.model_select.category_manage"),
                    Lists.newArrayList(
                            Component.translatable("gui.sparkle_morpher.model_select.rename_category").getString(),
                            Component.translatable("gui.sparkle_morpher.model_select.delete_category").getString(),
                            Component.translatable("gui.sparkle_morpher.model_select.new_category").getString()
                    ),
                    action -> handleCurrentCategoryAction(currentCategory, action),
                    () -> openCreateCategoryDialog(null)));
        } else {
            Minecraft.getInstance().setScreen(new CategorySelectScreen(this,
                    Component.translatable("gui.sparkle_morpher.model_select.select_category"),
                    categories,
                    category -> {
                        currentPath = category.endsWith("/") ? category : category + "/";
                        resetCurrentPage();
                        init();
                    },
                    () -> openCreateCategoryDialog(null)));
        }
    }

    private void handleCurrentCategoryAction(String category, String action) {
        String rename = Component.translatable("gui.sparkle_morpher.model_select.rename_category").getString();
        String delete = Component.translatable("gui.sparkle_morpher.model_select.delete_category").getString();
        if (action.equals(rename)) {
            Minecraft.getInstance().setScreen(new CategoryNameDialogScreen(this, Component.translatable("gui.sparkle_morpher.model_select.rename_category"), category, value -> {
                Component result = ModelPanelFileActions.renameCategory(category, value);
                String normalized = ModelPanelFileActions.normalizeCategory(value);
                if (!normalized.isBlank()) {
                    currentPath = normalized + "/";
                }
                showCategoryActionMessage(result);
            }));
        } else if (action.equals(delete)) {
            Minecraft.getInstance().setScreen(new CategoryDeleteConfirmScreen(this, category, deleteModels -> {
                Component result = ModelPanelFileActions.deleteCategory(category, deleteModels);
                currentPath = getParentPath(category + "/");
                showCategoryActionMessage(result);
            }));
        } else {
            openCreateCategoryDialog(category);
        }
    }

    private void openCreateCategoryDialog(@Nullable String parentCategory) {
        String prefix = StringUtils.isBlank(parentCategory) ? StringPool.EMPTY : parentCategory + "/";
        Minecraft.getInstance().setScreen(new CategoryNameDialogScreen(this, Component.translatable("gui.sparkle_morpher.model_select.new_category"), prefix, value -> {
            Component result = ModelPanelFileActions.createCategory(value);
            String normalized = ModelPanelFileActions.normalizeCategory(value);
            if (!normalized.isBlank()) {
                currentPath = normalized + "/";
            }
            showCategoryActionMessage(result);
        }));
    }

    private void showCategoryActionMessage(Component message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(message, false);
        }
        this.modelPackMap.clear();
        this.modelPackMap.putAll(ClientModelManager.getModelPackMap());
        resetCurrentPage();
        init();
    }

    private void toggleModelSelection(String modelId) {
        if (!this.selectedModelIds.add(modelId)) {
            this.selectedModelIds.remove(modelId);
        }
        this.selectionStatus = Component.translatable("gui.sparkle_morpher.model_select.count", this.selectedModelIds.size());
        init();
    }

    private void selectAllFilteredModels() {
        this.selectedModelIds.addAll(this.sortedModelKeys);
        this.selectionStatus = Component.translatable("gui.sparkle_morpher.model_select.count", this.selectedModelIds.size());
        init();
    }

    private void runDeleteSelection() {
        if (this.selectedModelIds.isEmpty()) {
            this.selectionStatus = Component.translatable("gui.sparkle_morpher.model_select.count", 0);
            init();
            return;
        }
        Component result = ModelPanelFileActions.deleteModels(new HashSet<>(this.selectedModelIds));
        this.selectedModelIds.clear();
        this.selectionMode = false;
        showSelectionActionMessage(result);
    }

    private void runMoveSelection() {
        if (this.selectedModelIds.isEmpty()) {
            this.selectionStatus = Component.translatable("gui.sparkle_morpher.model_select.count", 0);
            init();
            return;
        }
        if (!ModelPanelFileActions.canWriteServerModelDirectory()) {
            showSelectionActionMessage(Component.translatable("gui.sparkle_morpher.model_select.error.read_only"));
            return;
        }
        Minecraft.getInstance().setScreen(new CategorySelectScreen(this,
                Component.translatable("gui.sparkle_morpher.model_select.choose_category"),
                ModelPanelFileActions.listCategories(),
                category -> {
                    Component result = ModelPanelFileActions.moveModels(new HashSet<>(this.selectedModelIds), category);
                    this.selectedModelIds.clear();
                    this.selectionMode = false;
                    showSelectionActionMessage(result);
                },
                () -> Minecraft.getInstance().setScreen(new CategoryNameDialogScreen(this, Component.translatable("gui.sparkle_morpher.model_select.new_category_prompt"), StringPool.EMPTY, category -> {
                    ModelPanelFileActions.createCategory(category);
                    Component result = ModelPanelFileActions.moveModels(new HashSet<>(this.selectedModelIds), category);
                    this.selectedModelIds.clear();
                    this.selectionMode = false;
                    showSelectionActionMessage(result);
                }))));
    }

    private void runCreateCategory() {
        if (!ModelPanelFileActions.canWriteServerModelDirectory()) {
            showSelectionActionMessage(Component.translatable("gui.sparkle_morpher.model_select.error.read_only"));
            return;
        }
        Minecraft.getInstance().setScreen(new CategoryNameDialogScreen(this, Component.translatable("gui.sparkle_morpher.model_select.new_category_prompt"), StringPool.EMPTY, category -> {
            String normalized = ModelPanelFileActions.normalizeCategory(category);
            this.selectionStatus = ModelPanelFileActions.createCategory(normalized);
            if (StringUtils.isNotBlank(normalized)) {
                String path = normalized + "/";
                this.modelPackMap.putIfAbsent(path, new ModelPackData(path, FileTypeUtil.getFinalPathSegment(path), StringPool.EMPTY, null, null));
            }
            init();
        }));
    }

    private void runRenameCategory() {
        if (!ModelPanelFileActions.canWriteServerModelDirectory()) {
            showSelectionActionMessage(Component.translatable("gui.sparkle_morpher.model_select.error.read_only"));
            return;
        }
        Minecraft.getInstance().setScreen(new CategorySelectScreen(this, Component.translatable("gui.sparkle_morpher.model_select.rename_category"), getKnownCategories(), oldCategory -> {
            Minecraft.getInstance().setScreen(new CategoryNameDialogScreen(this, Component.translatable("gui.sparkle_morpher.model_select.rename_category_prompt"), oldCategory, newCategory -> {
                this.selectionStatus = ModelPanelFileActions.renameCategory(oldCategory, newCategory);
                String oldPath = ModelPanelFileActions.normalizeCategory(oldCategory) + "/";
                String newPath = ModelPanelFileActions.normalizeCategory(newCategory) + "/";
                renameCachedCategory(oldPath, newPath);
                if (currentPath.equals(oldPath)) {
                    currentPath = newPath;
                } else if (currentPath.startsWith(oldPath)) {
                    currentPath = newPath + currentPath.substring(oldPath.length());
                }
                init();
            }));
        }, null));
    }

    private void runDeleteCategory() {
        if (!ModelPanelFileActions.canWriteServerModelDirectory()) {
            showSelectionActionMessage(Component.translatable("gui.sparkle_morpher.model_select.error.read_only"));
            return;
        }
        Minecraft.getInstance().setScreen(new CategorySelectScreen(this,
                Component.translatable("gui.sparkle_morpher.model_select.delete_category"),
                getKnownCategories(),
                category -> Minecraft.getInstance().setScreen(new CategoryDeleteConfirmScreen(this, category, deleteModels -> {
                    Component result = ModelPanelFileActions.deleteCategory(category, deleteModels);
                    String path = category.endsWith("/") ? category : category + "/";
                    removeCachedCategory(ModelPanelFileActions.normalizeCategory(category) + "/");
                    if (currentPath.equals(path) || currentPath.startsWith(path)) {
                        currentPath = getParentPath(path);
                    }
                    this.selectionMode = false;
                    showSelectionActionMessage(result);
                })),
                null));
    }

    private void renameCachedCategory(String oldPath, String newPath) {
        Object2ReferenceOpenHashMap<String, ModelPackData> updated = new Object2ReferenceOpenHashMap<>();
        this.modelPackMap.forEach((path, packData) -> {
            if (path.equals(oldPath) || path.startsWith(oldPath)) {
                String renamedPath = newPath + path.substring(oldPath.length());
                updated.put(renamedPath, new ModelPackData(renamedPath, FileTypeUtil.getFinalPathSegment(renamedPath), packData.getDescription(), packData.getTexture(), packData.getTranslations()));
            } else {
                updated.put(path, packData);
            }
        });
        this.modelPackMap.clear();
        this.modelPackMap.putAll(updated);
        pageIndexMap.removeInt(oldPath);
    }

    private void removeCachedCategory(String path) {
        this.modelPackMap.keySet().removeIf(packPath -> packPath.equals(path) || packPath.startsWith(path));
        pageIndexMap.keySet().removeIf(packPath -> packPath.equals(path) || packPath.startsWith(path));
        if (currentPath.equals(path) || currentPath.startsWith(path)) {
            currentPath = StringPool.EMPTY;
        }
    }

    private List<String> getKnownCategories() {
        TreeSet<String> categories = new TreeSet<>();
        this.modelPackMap.keySet().forEach(path -> {
            String normalized = ModelPanelFileActions.normalizeCategory(path);
            if (StringUtils.isNotBlank(normalized)) {
                categories.add(normalized);
            }
        });
        this.sortedPackKeys.forEach(path -> {
            String normalized = ModelPanelFileActions.normalizeCategory(path);
            if (StringUtils.isNotBlank(normalized)) {
                categories.add(normalized);
            }
        });
        return Lists.newArrayList(categories);
    }

    private void showSelectionActionMessage(Component message) {
        this.selectionStatus = message;
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(message, false);
        }
        this.modelPackMap.clear();
        this.modelPackMap.putAll(ClientModelManager.getModelPackMap());
        resetCurrentPage();
        init();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.searchBox.mouseClicked(mouseX, mouseY, button)) {
            setFocused(this.searchBox);
            return true;
        }
        if (this.searchBox.isFocused()) {
            this.searchBox.setFocused(false);
        }
        boolean zMouseClicked = super.mouseClicked(mouseX, mouseY, button);
        if (!zMouseClicked && button == 1 && StringUtils.isNotBlank(currentPath)) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            navigateUp();
            zMouseClicked = true;
        }
        return zMouseClicked;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (this.searchBox == null) {
            return false;
        }
        String value = this.searchBox.getValue();
        if (this.searchBox.charTyped(codePoint, modifiers)) {
            if (!Objects.equals(value, this.searchBox.getValue())) {
                resetCurrentPage();
                init();
                return true;
            }
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (handleToggleKey(keyCode, scanCode, modifiers)) {
            return true;
        }
        boolean zIsPresent = InputConstants.getKey(keyCode, scanCode).getNumericKeyValue().isPresent();
        String value = this.searchBox.getValue();
        if (zIsPresent) {
            return true;
        }
        if (!this.searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            return (this.searchBox.isFocused() && this.searchBox.isVisible() && keyCode != 256) || super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (!Objects.equals(value, this.searchBox.getValue())) {
            resetCurrentPage();
            init();
            return true;
        }
        return true;
    }

    private Component getImportTooltip() {
        if (ClientModelManager.isAllowUpload() && ClientModelManager.isOysmServer()) {
            return Component.translatable("gui.sparkle_morpher.import.tooltip");
        }
        if (!ClientModelManager.isOysmServer()) {
            return Component.translatable("gui.sparkle_morpher.import.tooltip.waiting");
        }
        return Component.translatable("gui.sparkle_morpher.import.tooltip.disabled");
    }

    private Component getCustomFolderUploadTooltip() {
        if (ClientModelManager.isAllowUpload() && ClientModelManager.isOysmServer()) {
            return Component.translatable("gui.sparkle_morpher.upload_custom_folder.tooltip");
        }
        if (!ClientModelManager.isOysmServer()) {
            return Component.translatable("gui.sparkle_morpher.upload_custom_folder.tooltip.waiting");
        }
        return Component.translatable("gui.sparkle_morpher.upload_custom_folder.tooltip.disabled");
    }

    private boolean handleToggleKey(int keyCode, int scanCode, int modifiers) {
        if (PlayerModelToggleKey.KEY_MAPPING.matches(keyCode, scanCode) && !this.searchBox.isFocused()) {
            onClose();
            return true;
        }
        return false;
    }

    public void insertText(String text, boolean overwrite) {
        if (overwrite) {
            this.searchBox.setValue(text);
        } else {
            this.searchBox.insertText(text);
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.minecraft == null) {
            return false;
        }
        if (delta != 0.0d && isInModelArea(mouseX, mouseY)) {
            return handleScrollPage(delta);
        }
        return super.mouseScrolled(mouseX, mouseY, delta, delta);
    }

    private boolean isInModelArea(double mouseX, double mouseY) {
        return ((((double) (this.guiLeft + 143)) > mouseX ? 1 : (((double) (this.guiLeft + 143)) == mouseX ? 0 : -1)) < 0 && (mouseX > ((double) (this.guiLeft + 430)) ? 1 : (mouseX == ((double) (this.guiLeft + 430)) ? 0 : -1)) < 0) && ((((double) (this.guiTop + 25)) > mouseY ? 1 : (((double) (this.guiTop + 25)) == mouseY ? 0 : -1)) < 0 && (mouseY > ((double) (this.guiTop + 235)) ? 1 : (mouseY == ((double) (this.guiTop + 235)) ? 0 : -1)) < 0);
    }

    private void navigateUp() {
        String str2 = getParentPath(currentPath);
        if (!currentPath.equals(str2)) {
            String str = currentPath;
            currentPath = str2;
            pageIndexMap.removeInt(str);
            init();
        }
    }

    private boolean handleScrollPage(double delta) {
        int currentPage = getCurrentPage();
        if (delta > 0.0d && currentPage > 0) {
            setCurrentPage(currentPage - 1);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            init();
        }
        if (delta < 0.0d && currentPage < this.maxPage) {
            setCurrentPage(currentPage + 1);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            init();
            return true;
        }
        return true;
    }

    public int getCurrentPage() {
        return pageIndexMap.getOrDefault(currentPath, 0);
    }

    public void setCurrentPage(int i) {
        pageIndexMap.put(currentPath, i);
    }

    public void resetCurrentPage() {
        pageIndexMap.put(currentPath, 0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onModelsLoaded(Map<String, ModelAssembly> map) {
        init();
    }

    @Override
    public void onModelsUpdated(Map<String, ModelAssembly> map) {
        init();
    }

    private Optional<ModelPackData> getPackData(String str) {
        return Optional.ofNullable(this.modelPackMap.get(str));
    }

    private enum Category {
        ALL,
        AUTH,
        STAR
    }
}
