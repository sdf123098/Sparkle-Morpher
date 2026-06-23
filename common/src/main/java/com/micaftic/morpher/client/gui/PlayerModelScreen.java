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
import com.micaftic.morpher.core.architectury.platform.Platform;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.CharacterEvent;
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
import org.apache.commons.lang3.StringUtils;
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

    private static int opaque(ChatFormatting formatting) {
        Integer color = formatting.getColor();
        return color == null ? 0xFFFFFFFF : 0xFF000000 | color.intValue();
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
        this.searchBox.setTextColor(0xFFF3F3E0);
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        GuiGraphicsExtractor guiGraphics = extractor;
        extractTransparentBackground(extractor);
        guiGraphics.fillGradient(this.guiLeft, this.guiTop, this.guiLeft + 135, this.guiTop + 235, -14540254, -14540254);
        guiGraphics.fillGradient(this.guiLeft + 138, this.guiTop, this.guiLeft + 420, this.guiTop + 235, -14540254, -14540254);
        guiGraphics.fillGradient(this.guiLeft + 351, this.guiTop + 7, this.guiLeft + 352, this.guiTop + 21, -790560, -790560);
        this.searchBox.extractWidgetRenderState(extractor, mouseX, mouseY, partialTick);
        renderModelPreview(extractor, mouseX, mouseY, partialTick);
        if (this.searchBox.getValue().isEmpty() && !this.searchBox.isFocused()) {
            guiGraphics.text(this.font, Component.translatable("gui.sparkle_morpher.search").withStyle(ChatFormatting.ITALIC), this.guiLeft + 148, this.guiTop + 10, 0xFF777777);
        }
        String str = String.format("%d/%d", getCurrentPage() + 1, Integer.valueOf(this.maxPage + 1));
        Font font = this.font;
        int iWidth = this.guiLeft + 138 + ((282 - this.font.width(str)) / 2);
        int pageY = this.guiTop + 223;
        Objects.requireNonNull(this.font);
        guiGraphics.text(font, str, iWidth, pageY - (9 / 2), 0xFFF3F3E0);
        String renderer = (NativeLibLoader.isLoaded() && !GeneralConfig.USE_COMPATIBILITY_RENDERER.get()) ? "SIMD" : "Fallback";
        if(renderer.equals("SIMD") && GpuCapability.isAvailable() && GeneralConfig.USE_GPU_RENDERER.get()) {
            renderer = "GPU";
        }
        String strVersionString = Platform.getMod(YesSteveModel.MOD_ID).getVersion();
        guiGraphics.text(this.font, strVersionString + " (" + renderer + ")", this.guiLeft + 2, this.guiTop + 226, opaque(ChatFormatting.DARK_GRAY));
        if (StringUtils.isNotBlank(currentPath)) {
            int lineIndex = 0;
            List listSplit = this.font.split(Component.literal("📂 " + currentPath).withStyle(ChatFormatting.GRAY), 270);
            Iterator it = listSplit.iterator();
            while (it.hasNext()) {
                guiGraphics.text(this.font, (FormattedCharSequence) it.next(), this.guiLeft + 142, this.guiTop + (((-(listSplit.size() - lineIndex)) * 10) - 2), 0xFFF3F3E0);
                lineIndex++;
            }
        }
        renderSyncStatus(guiGraphics);
        renderSelectionStatus(guiGraphics);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        ((ScreenAccessor) this).ysm$getRenderables().stream().filter(renderable -> {
            return renderable instanceof IconButton;
        }).forEach(renderable2 -> {
            ((IconButton) renderable2).renderTooltip(guiGraphics, this, mouseX, mouseY);
/*             ((IconButton) renderable2).renderTooltip(GuiGraphicsExtractor, this, mouseX, mouseY);
 */
        });
        ((ScreenAccessor) this).ysm$getRenderables().stream().filter(renderable3 -> {
            return renderable3 instanceof ModelButton;
        }).forEach(renderable4 -> {
            ((ModelButton) renderable4).renderTooltip(guiGraphics, this, mouseX, mouseY);
/*             ((ModelButton) renderable4).renderTooltip(GuiGraphicsExtractor, this, mouseX, mouseY);
 */
        });
        ((ScreenAccessor) this).ysm$getRenderables().stream().filter(renderable5 -> {
            return renderable5 instanceof PackIconButton;
        }).forEach(renderable6 -> {
            ((PackIconButton) renderable6).renderDescription(guiGraphics, this, mouseX, mouseY);
        });
        if (this.searchBox.isHovered()) {
            MutableComponent mutableComponentWithStyle = Component.translatable("gui.sparkle_morpher.search.tip").withStyle(ChatFormatting.GRAY);
            guiGraphics.setTooltipForNextFrame(this.font, this.font.split(mutableComponentWithStyle, 320), mouseX, mouseY);
        }
    }

    private void renderSelectionStatus(GuiGraphicsExtractor guiGraphics) {
        if (!this.selectionMode && this.selectionStatus.getString().isBlank()) {
            return;
        }
        Component text = this.selectionStatus.getString().isBlank()
                ? Component.translatable("gui.sparkle_morpher.model_select.count", this.selectedModelIds.size())
                : this.selectionStatus;
        guiGraphics.text(this.font, this.font.split(text.copy().withStyle(ChatFormatting.GRAY), 126).get(0), this.guiLeft + 288, this.guiTop - 15, 0xFFF3F3E0);
    }

    private void renderSyncStatus(GuiGraphicsExtractor guiGraphics) {
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
        guiGraphics.text(this.font, mutableComponentLiteral, iWidth, i + Math.round((14 - 9) / 2.0f), opaque(ChatFormatting.DARK_GRAY));
    }

    public void renderModelPreview(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        GuiGraphicsExtractor guiGraphics = extractor;
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            int previewLeft = this.guiLeft + 5;
            int previewTop = this.guiTop + 29;
            int previewRight = this.guiLeft + 130;
            int previewBottom = this.guiTop + 200;
            if (!ModelPreviewRenderer.renderCustomLocalPlayerPreview(extractor, localPlayer, previewLeft, previewTop, previewRight, previewBottom, (previewLeft + previewRight) * 0.5f, previewTop + 150.0f, 70.0f, 180.0f, partialTick, false, mouseX, mouseY)) {
                guiGraphics.enableScissor(previewLeft, previewTop, previewRight, previewBottom);
                try {
                    InventoryScreen.extractEntityInInventoryFollowsMouse(extractor, previewLeft, previewTop, previewRight, previewBottom, 70, mouseX, mouseY, 1.0f, localPlayer);
                } finally {
                    guiGraphics.disableScissor();
                }
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
                    guiGraphics.text(this.font, formattedCharSequence, this.guiLeft + ((135 - this.font.width(formattedCharSequence)) / 2), lineY, 0xFFF3F3E0);
                    lineY += 10;
                }
            });
        }
    }

    @Override
    public void resize(int width, int height) {
        String value = this.searchBox.getValue();
        super.resize(width, height);
        this.searchBox.setValue(value);
    }

    public void tick() {
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean flag) {
        if (this.searchBox.mouseClicked(event, flag)) {
            setFocused(this.searchBox);
            return true;
        }
        if (this.searchBox.isFocused()) {
            this.searchBox.setFocused(false);
        }
        boolean zMouseClicked = super.mouseClicked(event, flag);
        if (!zMouseClicked && event.button() == 1 && StringUtils.isNotBlank(currentPath)) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            navigateUp();
            zMouseClicked = true;
        }
        return zMouseClicked;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.searchBox == null) {
            return false;
        }
        String value = this.searchBox.getValue();
        if (this.searchBox.charTyped(event)) {
            if (!Objects.equals(value, this.searchBox.getValue())) {
                resetCurrentPage();
                init();
                return true;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (handleToggleKey(event)) {
            return true;
        }
        boolean zIsPresent = InputConstants.getKey(event).getNumericKeyValue().isPresent();
        String value = this.searchBox.getValue();
        if (zIsPresent) {
            return true;
        }
        if (!this.searchBox.keyPressed(event)) {
            return (this.searchBox.isFocused() && this.searchBox.isVisible() && event.key() != 256) || super.keyPressed(event);
        }
        if (!Objects.equals(value, this.searchBox.getValue())) {
            resetCurrentPage();
            init();
            return true;
        }
        return true;
    }

    public boolean shouldCloseWithToggleKey() {
        return this.searchBox == null || !this.searchBox.isFocused();
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

    private boolean handleToggleKey(KeyEvent event) {
        if (PlayerModelToggleKey.KEY_MAPPING.matches(event) && shouldCloseWithToggleKey()) {
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
        this.selectionStatus = ModelPanelFileActions.deleteModels(this.selectedModelIds);
        this.selectedModelIds.clear();
        init();
    }

    private void runMoveSelection() {
        List<String> categories = getKnownCategories();
        Minecraft.getInstance().setScreen(new CategorySelectScreen(this, Component.translatable("gui.sparkle_morpher.model_select.choose_category"), categories, category -> {
            this.selectionStatus = ModelPanelFileActions.moveModels(this.selectedModelIds, category);
            this.selectedModelIds.clear();
            init();
        }, () -> Minecraft.getInstance().setScreen(new CategoryNameDialogScreen(this, Component.translatable("gui.sparkle_morpher.model_select.new_category_prompt"), StringPool.EMPTY, category -> {
            this.selectionStatus = ModelPanelFileActions.createCategory(category);
            this.selectionStatus = ModelPanelFileActions.moveModels(this.selectedModelIds, category);
            this.selectedModelIds.clear();
            init();
        }))));
    }

    private void runCreateCategory() {
        Minecraft.getInstance().setScreen(new CategoryNameDialogScreen(this, Component.translatable("gui.sparkle_morpher.model_select.new_category_prompt"), StringPool.EMPTY, category -> {
            String normalized = ModelPanelFileActions.normalizeCategory(category);
            this.selectionStatus = ModelPanelFileActions.createCategory(normalized);
            if (StringUtils.isNotBlank(normalized)) {
                this.modelPackMap.putIfAbsent(normalized + "/", new ModelPackData(normalized + "/", normalized, StringPool.EMPTY, null, null));
            }
            init();
        }));
    }

    private void runRenameCategory() {
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
        Minecraft.getInstance().setScreen(new CategorySelectScreen(this, Component.translatable("gui.sparkle_morpher.model_select.delete_category"), getKnownCategories(), category -> {
            Minecraft.getInstance().setScreen(new CategoryDeleteConfirmScreen(this, category, deleteModels -> {
                this.selectionStatus = ModelPanelFileActions.deleteCategory(category, deleteModels);
                removeCachedCategory(ModelPanelFileActions.normalizeCategory(category) + "/");
                init();
            }));
        }, null));
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.minecraft == null) {
            return false;
        }
        if (scrollY != 0.0d && isInModelArea(mouseX, mouseY)) {
            return handleScrollPage(scrollY);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
