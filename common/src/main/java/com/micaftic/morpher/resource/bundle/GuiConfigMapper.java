package com.micaftic.morpher.resource.bundle;

import com.micaftic.morpher.client.gui.custom.AbstractConfig;
import com.micaftic.morpher.client.gui.custom.ExtraAnimationButtons;
import com.micaftic.morpher.client.gui.custom.configs.CheckboxConfig;
import com.micaftic.morpher.client.gui.custom.configs.RadioConfig;
import com.micaftic.morpher.client.gui.custom.configs.RangeConfig;
import com.micaftic.morpher.model.format.ServerModelInfo;
import com.micaftic.morpher.resource.models.AuthorInfo;
import com.micaftic.morpher.resource.models.MainModelInfo;
import com.micaftic.morpher.resource.models.Metadata;
import com.micaftic.morpher.resource.models.ModelProperties;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import com.micaftic.morpher.util.data.OrderedStringMap;
import com.micaftic.morpher.util.data.StringMapPair;
import com.micaftic.morpher.util.data.StringPair;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.2.7 §24.1：从 {@code YSMClientMapper} 外提的元数据 / GUI 配置映射职责（行为等价，纯搬运）。
 *
 * 将 raw YSM 的 metadata、properties、footer 映射为运行时的 {@link ServerModelInfo}
 * （作者、许可、额外动画按钮表单、分类、模型统计等）。不含几何或动画装配。
 */
public final class GuiConfigMapper {

    private GuiConfigMapper() {
    }

    public static ServerModelInfo buildModelInfo(RawYsmModel raw) {
        RawYsmModel.RawMetadata rm = raw.metadata;
        List<AuthorInfo> authors = new ArrayList<>();
        for (RawYsmModel.RawMetadata.Author a : rm.authors) {
            authors.add(new AuthorInfo(a.name, a.role, new OrderedStringMap<>(new Object2ObjectArrayMap<>(a.contacts)), a.comment));
        }

        Metadata extraInfo = new Metadata(rm.name, rm.tips, new StringPair(rm.licenseType, rm.licenseDescription), authors.toArray(new AuthorInfo[0]), new OrderedStringMap<>(new Object2ObjectArrayMap<>(rm.links)));

        RawYsmModel.RawProperties rp = raw.properties;
        List<StringMapPair> classifyList = new ArrayList<>();
        for (RawYsmModel.ExtraAnimationClassify rCls : rp.extraAnimationClassifies) {
            classifyList.add(new StringMapPair(rCls.id, new OrderedStringMap<>(new Object2ObjectArrayMap<>(rCls.extras))));
        }

        List<ExtraAnimationButtons> buttonsList = new ArrayList<>();
        for (RawYsmModel.ExtraAnimationButton rBtn : rp.extraAnimationButtons) {
            List<AbstractConfig> metaList = new ArrayList<>();
            for (RawYsmModel.ConfigForm form : rBtn.forms) {
                if ("checkbox".equals(form.type)) {
                    metaList.add(new CheckboxConfig(form.title, form.description, form.defaultValue));
                } else if ("radio".equals(form.type)) {
                    metaList.add(new RadioConfig(form.title, form.description, form.defaultValue, new OrderedStringMap<>(new Object2ObjectArrayMap<>(form.labels))));
                } else if ("range".equals(form.type)) {
                    metaList.add(new RangeConfig(form.title, form.description, form.defaultValue, form.step, form.min, form.max));
                }
            }
            buttonsList.add(new ExtraAnimationButtons(rBtn.id, rBtn.name, rBtn.description, metaList.toArray(new AbstractConfig[0])));
        }
        String defaultTexture = resolveDefaultTexture(raw);
        ModelProperties properties = new ModelProperties(rp.heightScale, rp.widthScale, defaultTexture, rp.previewAnimation, new OrderedStringMap<>(new Object2ObjectArrayMap<>(rp.extraAnimations)), buttonsList.toArray(new ExtraAnimationButtons[0]), classifyList.toArray(new StringMapPair[0]), rp.isFree, rp.renderLayersFirst, rp.disablePreviewRotation);

        int bones = 0;
        int cubes = 0;
        int faces = 0;
        if (raw.mainEntity.mainModel != null) {
            bones = raw.mainEntity.mainModel.bones.size();
            for (RawYsmModel.RawBone bone : raw.mainEntity.mainModel.bones) {
                cubes += bone.cubes.size();
                for (RawYsmModel.RawCube cube : bone.cubes) {
                    faces += cube.faces.size();
                }
            }
        }
        MainModelInfo stats = new MainModelInfo(bones, cubes, faces);

        RawYsmModel.RawFooter footer = raw.footer;
        return new ServerModelInfo(extraInfo,
                properties,
                stats,
                footer.version,
                rp.sha256 != null ? rp.sha256 : "",
                footer.extra, footer.time, footer.rand);
    }

    public static String resolveDefaultTexture(RawYsmModel raw) {
        String defaultTexture = raw.properties.defaultTexture;
        if (raw.mainEntity.textures.isEmpty()) {
            return defaultTexture;
        }
        return defaultTexture != null && !defaultTexture.isBlank() && raw.mainEntity.textures.containsKey(defaultTexture)
                ? defaultTexture
                : raw.mainEntity.textures.keySet().iterator().next();
    }
}
