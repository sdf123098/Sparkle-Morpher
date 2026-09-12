package com.micaftic.morpher.resource.bundle;

import com.micaftic.morpher.resource.YSMClientMapper;
import com.micaftic.morpher.resource.models.GeometryDescription;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1.2.7 §24.1 契约测试（§4.3 Characterization Before Refactor / §10.5 ImportPipelineGoldenTest）。
 *
 * 锁定 {@code YSMClientMapper} 拆分后的行为等价与 facade 委派关系：
 *  - facade 与新的 {@code resource.bundle} 组件必须产出相同结果；
 *  - 贴图解码对非法/降级输入的既有行为（透明占位 PNG）必须保持；
 *  - 透明度分析对空贴图/不透明/半透明的判定语义必须保持。
 * 无第三方版权资产：全部样本现造。
 */
class ImportPipelineContractTest {

    private static final byte[] PNG_1X1_OPAQUE = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private static byte[] pngOf(int argb) throws Exception {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, argb);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    /** facade 必须把贴图解码委派给 TextureDecoder，且对合法 PNG 返回同一字节。 */
    @Test
    void facadeDelegatesTextureDecoding() throws Exception {
        byte[] src = pngOf(0xFF3366CC);
        byte[] viaFacade = YSMClientMapper.toPng(src, 2, 1, 1);
        byte[] viaComponent = TextureDecoder.toPng(src, 2, 1, 1);

        assertArrayEquals(viaComponent, viaFacade, "facade 与 TextureDecoder 必须一致");

        BufferedImage decoded = TextureDecoder.decodeToImage(src, 2, 1, 1);
        assertNotNull(decoded, "合法 PNG 必须能解码");
        assertEquals(1, decoded.getWidth());
        assertEquals(1, decoded.getHeight());
    }

    /** PNG 输入在 toPng 中按原样透传（format==2 快路径），不得被重新编码。 */
    @Test
    void pngFormatIsPassedThroughUnchanged() throws Exception {
        byte[] src = pngOf(0x80FF0000);
        assertSame(src, TextureDecoder.toPng(src, 2, 1, 1), "format==2 必须原样返回输入数组");
    }

    /** 非法输入回退为透明占位 PNG（既有降级行为必须保持）。 */
    @Test
    void invalidInputFallsBackToTransparentPng() {
        byte[] garbage = new byte[]{1, 2, 3, 4};
        byte[] out = TextureDecoder.toPng(garbage, 3, 1, 1);
        assertNotNull(out);
        assertTrue(out.length > 0, "降级输出必须是合法 PNG 字节");
        assertTrue(TextureDecoder.isPng(out), "降级输出必须识别为 PNG");
    }

    /** isPng 必须与 PNG 魔数语义一致。 */
    @Test
    void isPngDetectsMagicBytes() {
        assertTrue(TextureDecoder.isPng(PNG_1X1_OPAQUE), "标准 1x1 PNG 必须被识别");
        assertTrue(!TextureDecoder.isPng(new byte[]{0, 1, 2, 3}), "短字节不得误判为 PNG");
        assertTrue(!TextureDecoder.isPng(null), "null 不得被判为 PNG");
    }

    /** 无贴图时按 OPAQUE 处理（既有语义：无有效图像即不透明）。 */
    @Test
    void analyzerTreatsMissingImageAsOpaque() {
        TextureAlphaAnalyzer analyzer = new TextureAlphaAnalyzer(new BufferedImage[]{null}, 1);
        assertEquals(TextureAlphaAnalyzer.STATE_OPAQUE, analyzer.scan(face(0f, 1f)), "无有效图像应为 OPAQUE");
        assertArrayEquals(new boolean[]{false}, analyzer.getResults(), "无有效图像不得标记 translucency");
    }

    /** 全透明贴图必须判定为 INVISIBLE（面被剔除）。 */
    @Test
    void analyzerDetectsInvisibleFace() throws Exception {
        BufferedImage transparent = TextureDecoder.decodeToImage(pngOf(0x00000000), 2, 1, 1);
        TextureAlphaAnalyzer analyzer = new TextureAlphaAnalyzer(new BufferedImage[]{transparent}, 1);
        assertEquals(TextureAlphaAnalyzer.STATE_INVISIBLE, analyzer.scan(face(0f, 1f)), "全透明贴图应为 INVISIBLE");
    }

    /** 不透明贴图必须判定为 OPAQUE。 */
    @Test
    void analyzerDetectsOpaqueFace() throws Exception {
        BufferedImage opaque = TextureDecoder.decodeToImage(pngOf(0xFFFF0000), 2, 1, 1);
        TextureAlphaAnalyzer analyzer = new TextureAlphaAnalyzer(new BufferedImage[]{opaque}, 1);
        assertEquals(TextureAlphaAnalyzer.STATE_OPAQUE, analyzer.scan(face(0f, 1f)), "不透明贴图应为 OPAQUE");
    }

    /** 半透明彩色像素必须判定为 TRANSLUCENT 并记录进 results。 */
    @Test
    void analyzerDetectsTranslucentFaceAndRecordsResult() throws Exception {
        BufferedImage halfAlpha = TextureDecoder.decodeToImage(pngOf(0x80FF0000), 2, 1, 1);
        TextureAlphaAnalyzer analyzer = new TextureAlphaAnalyzer(new BufferedImage[]{halfAlpha}, 1);
        assertEquals(TextureAlphaAnalyzer.STATE_TRANSLUCENT, analyzer.scan(face(0f, 1f)), "半透明彩色像素应为 TRANSLUCENT");
        assertArrayEquals(new boolean[]{true}, analyzer.getResults(), "半透明彩色贴图必须记录进 results");
    }

    /** 旧嵌套别名 TranslucencyScanner 必须与 TextureAlphaAnalyzer 行为一致（facade 委派）。 */
    @Test
    void deprecatedScannerAliasBehavesIdentically() throws Exception {
        BufferedImage opaque = TextureDecoder.decodeToImage(pngOf(0xFFFFFFFF), 2, 1, 1);
        BufferedImage[] images = {opaque};

        TextureAlphaAnalyzer component = new TextureAlphaAnalyzer(images, 1);
        YSMClientMapper.TranslucencyScanner alias = new YSMClientMapper.TranslucencyScanner(images, 1);

        assertNotSame(component, alias, "别名应为独立实例");
        assertEquals(component.scan(face(0f, 1f)), alias.scan(face(0f, 1f)), "别名判定必须与组件一致");
        assertArrayEquals(component.getResults(), alias.getResults(), "别名 results 必须与组件一致");
    }

    /** facade 的几何构建必须与 GeometryBaker 委派一致。 */
    @Test
    void facadeDelegatesGeometryConstruction() {
        RawYsmModel.RawGeometry geo = new RawYsmModel.RawGeometry();
        geo.identifier = "geometry.contract";
        geo.textureWidth = 64;
        geo.textureHeight = 64;

        GeometryDescription viaFacade = YSMClientMapper.buildContext(geo);
        GeometryDescription viaComponent = GeometryBaker.buildContext(geo);

        assertNotNull(viaFacade);
        assertEquals(viaComponent.getIdentifier(), viaFacade.getIdentifier(), "facade buildContext 必须委派 GeometryBaker");
    }

    /** 骨名归一化必须与既有语义一致（大小写/非字母数字忽略）。 */
    @Test
    void boneNameNormalizationIsStable() {
        assertEquals("lefthandlocator", GeometryBaker.normalizeBoneName("LeftHandLocator"));
        assertEquals("lefthandlocator", GeometryBaker.normalizeBoneName("left_hand-locator"));
        assertEquals("", GeometryBaker.normalizeBoneName(null));
    }

    /** 面样本：UV 覆盖整张 1x1 贴图。 */
    private static RawYsmModel.RawFace face(float min, float max) {
        RawYsmModel.RawFace f = new RawYsmModel.RawFace();
        f.u = new float[]{min, max, max, min};
        f.v = new float[]{min, max, max, min};
        return f;
    }
}
