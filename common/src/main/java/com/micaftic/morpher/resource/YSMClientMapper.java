package com.micaftic.morpher.resource;

import com.micaftic.morpher.client.ClientModelInfo;
import com.micaftic.morpher.client.model.SpecialHandLocatorProfile;
import com.micaftic.morpher.client.texture.OuterFileTexture;
import com.micaftic.morpher.geckolib3.core.builder.Animation;
import com.micaftic.morpher.geckolib3.core.builder.AnimationController;
import com.micaftic.morpher.geckolib3.core.molang.value.IValue;
import com.micaftic.morpher.geckolib3.geo.render.built.GeoBone;
import com.micaftic.morpher.model.format.ServerModelInfo;
import com.micaftic.morpher.resource.bundle.AnimationMapper;
import com.micaftic.morpher.resource.bundle.ClientModelBundleAssembler;
import com.micaftic.morpher.resource.bundle.GeometryBaker;
import com.micaftic.morpher.resource.bundle.GuiConfigMapper;
import com.micaftic.morpher.resource.bundle.TextureAlphaAnalyzer;
import com.micaftic.morpher.resource.bundle.TextureDecoder;
import com.micaftic.morpher.resource.models.GeometryDescription;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import com.micaftic.morpher.util.data.OrderedStringMap;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

/**
 * YSM 客户端模型映射入口。
 *
 * <p><b>1.2.7 §24.1</b>：本类已拆分为 {@code resource.bundle} 下的专用组件，本类保留为
 * 兼容 facade（Facade First），调用点迁移完成后于 1.2.8 删除：
 * <ul>
 *   <li>{@link ClientModelBundleAssembler} — 装配编排</li>
 *   <li>{@link TextureDecoder} — 贴图解码 / PNG 归一</li>
 *   <li>{@link TextureAlphaAnalyzer} — 透明度分析</li>
 *   <li>{@link GeometryBaker} — 几何烘焙</li>
 *   <li>{@link AnimationMapper} — 动画 / 控制器映射</li>
 *   <li>{@code AudioResourceMapper} — 音频资源映射</li>
 *   <li>{@link GuiConfigMapper} — 元数据 / GUI 配置映射</li>
 * </ul>
 *
 * @deprecated 1.2.7：请直接使用 {@code com.micaftic.morpher.resource.bundle} 下的对应组件。删除留到 1.2.8。
 */
@Deprecated
public class YSMClientMapper {
    /**
     * @deprecated 1.2.7 §24.1：透明度分析已外提为 {@link TextureAlphaAnalyzer}。
     * 本类别名仅为兼容既有引用保留（Facade First），删除留到 1.2.8。
     */
    @Deprecated
    public static class TranslucencyScanner extends TextureAlphaAnalyzer {
        public TranslucencyScanner(BufferedImage[] images, int expectedCount) {
            super(images, expectedCount);
        }
    }

    public static byte[] toPng(byte[] data, int imageFormat, int width, int height) {
        return TextureDecoder.toPng(data, imageFormat, width, height);
    }

    public static ClientModelInfo buildParsedBundle(RawYsmModel raw, String modelId) {
        return ClientModelBundleAssembler.buildParsedBundle(raw, modelId);
    }

    public static Map<String, Animation> buildAnimations(RawYsmModel.RawAnimationFile animFile, boolean mergeMultilineExpr) {
        return AnimationMapper.buildAnimations(animFile, mergeMultilineExpr);
    }

    public static ServerModelInfo buildModelInfo(RawYsmModel raw/*, String modelId*/) {
        return GuiConfigMapper.buildModelInfo(raw);
    }

    public static List<IValue> parse(List<String> array, boolean mergeMultilineExpr) {
        return AnimationMapper.parse(array, mergeMultilineExpr);
    }

    public static IValue parse(String str) {
        return AnimationMapper.parse(str);
    }

    public static GeoModel buildMesh(GeoBone[] bones, Map<String, String> parentMap, GeometryDescription context, boolean[] translucencyArray) {
        return GeometryBaker.buildMesh(bones, parentMap, context, translucencyArray);
    }

    public static GeoModel buildMesh(GeoBone[] bones, Map<String, String> parentMap, GeometryDescription context, boolean[] translucencyArray, SpecialHandLocatorProfile specialHandLocatorProfile) {
        return GeometryBaker.buildMesh(bones, parentMap, context, translucencyArray, specialHandLocatorProfile);
    }

    public static OrderedStringMap<String, OuterFileTexture> buildTextureMap(Map<String, OuterFileTexture> textures) {
        return GeometryBaker.buildTextureMap(textures);
    }

    public static GeometryDescription buildContext(RawYsmModel.RawGeometry model) {
        return GeometryBaker.buildContext(model);
    }
}
