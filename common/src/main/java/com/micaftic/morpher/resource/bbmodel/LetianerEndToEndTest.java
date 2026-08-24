package com.micaftic.morpher.resource.bbmodel;

import com.micaftic.morpher.resource.pojo.RawYsmModel;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 真实 Figura zip 端到端测试 — 用 D:\Games\MCTEST\乐天儿Java版4d皮肤.zip 验证。
 *
 * <p>在 main 函数里直接 hardcode 路径。本类仅供本地开发跑，不参与发布。</p>
 */
public class LetianerEndToEndTest {

    public static void main(String[] args) throws Exception {
        Path zip = Path.of("D:\\Games\\MCTEST\\乐天儿Java版4d皮肤.zip");
        if (!Files.exists(zip)) {
            System.err.println("Sample zip not found: " + zip);
            System.exit(2);
        }

        byte[] data = Files.readAllBytes(zip);
        long t0 = System.nanoTime();
        ZipModelSniffer sniff = ZipModelSniffer.sniff(data, 0);
        long t1 = System.nanoTime();
        System.out.printf("sniff: %.1f ms, kind=%s, bbmodel=%s, sideTextures=%d%n",
                (t1 - t0) / 1e6, sniff.kind, sniff.bbmodelPath, sniff.sideTextures.size());

        if (sniff.kind != ZipModelSniffer.Kind.FIGURA_AVATAR) {
            System.err.println("Expected FIGURA_AVATAR, got " + sniff.kind);
            System.exit(1);
        }

        String avatarName = ZipModelSniffer.parseAvatarName(sniff.avatarJsonBytes);
        System.out.println("avatar.name = " + avatarName);
        System.out.println("textures: " + sniff.sideTextures.keySet());

        long t2 = System.nanoTime();
        String json = new String(sniff.bbmodelBytes, java.nio.charset.StandardCharsets.UTF_8);
        BBModelFile bbmodel = BBModelParser.parse(json);
        long t3 = System.nanoTime();
        System.out.printf("parse bbmodel: %.1f ms%n", (t3 - t2) / 1e6);

        System.out.println("bbmodel.name = " + bbmodel.name);
        System.out.println("bbmodel.format_version = " + bbmodel.meta.format_version);
        System.out.println("bbmodel.resolution = " + bbmodel.resolution.width + "x" + bbmodel.resolution.height);
        System.out.println("# elements = " + bbmodel.elements.size());
        long meshCount = bbmodel.elements.stream().filter(e -> "mesh".equals(e.type)).count();
        System.out.println("# mesh elements = " + meshCount);
        System.out.println("# outliner roots = " + bbmodel.outliner.size());
        System.out.println("# textures = " + bbmodel.textures.size());
        System.out.println("# animations = " + bbmodel.animations.size());

        long t4 = System.nanoTime();
        RawYsmModel raw = BBToRawConverter.convert(bbmodel, sniff.sideTextures);
        long t5 = System.nanoTime();
        System.out.printf("convert: %.1f ms%n", (t5 - t4) / 1e6);

        System.out.println();
        System.out.println("=== conversion result ===");
        System.out.println("modelId = " + raw.modelId);
        System.out.println("mainModel != null: " + (raw.mainEntity.mainModel != null));
        System.out.println("# bones = " + raw.mainEntity.mainModel.bones.size());
        int totalCubes = 0, totalFaces = 0;
        for (RawYsmModel.RawBone bone : raw.mainEntity.mainModel.bones) {
            totalCubes += bone.cubes.size();
            for (RawYsmModel.RawCube c : bone.cubes) totalFaces += c.faces.size();
            System.out.printf("  bone '%s' parent='%s' cubes=%d pivot=[%.2f, %.2f, %.2f]%n",
                    bone.name, bone.parentName, bone.cubes.size(),
                    bone.pivot[0], bone.pivot[1], bone.pivot[2]);
        }
        System.out.println("total cubes = " + totalCubes);
        System.out.println("total faces = " + totalFaces);
        System.out.println("# textures = " + raw.mainEntity.textures.size());
        for (RawYsmModel.RawTexture t : raw.mainEntity.textures.values()) {
            System.out.printf("  texture name='%s' w=%d h=%d dataBytes=%d%n",
                    t.name, t.width, t.height, t.data == null ? 0 : t.data.length);
        }
        System.out.println("# animationFiles = " + raw.mainEntity.animationFiles.size());
        for (var af : raw.mainEntity.animationFiles.values()) {
            for (var anim : af.animations.values()) {
                System.out.printf("  anim '%s' loopMode=%d length=%.3f #boneAnim=%d%n",
                        anim.name, anim.loopMode, anim.length, anim.boneAnimations.size());
            }
        }

        // 校验关键期望
        boolean ok = true;
        if (raw.mainEntity.mainModel.bones.size() < 6) {
            System.err.println("FAIL: expected ≥6 bones (from 6 outliner roots)");
            ok = false;
        }
        if (totalFaces < 6000) {
            // 期望 6654 三角面，加上 quad 合并后大致 6000+
            System.err.println("FAIL: expected ≥6000 faces (mesh triangulation), got " + totalFaces);
            ok = false;
        }
        if (raw.mainEntity.textures.isEmpty()) {
            System.err.println("FAIL: no texture extracted");
            ok = false;
        } else {
            var tex = raw.mainEntity.textures.values().iterator().next();
            if (tex.data == null || tex.data.length == 0) {
                System.err.println("FAIL: texture has no data");
                ok = false;
            }
            if (tex.width <= 0 || tex.height <= 0) {
                System.err.println("FAIL: texture has zero dimensions");
                ok = false;
            }
        }
        if (raw.mainEntity.animationFiles.isEmpty()) {
            System.err.println("FAIL: no animation file (expected walk + walkback)");
            ok = false;
        }

        System.out.println();
        System.out.println(ok ? "🎉 Letianer end-to-end OK" : "❌ Letianer end-to-end FAILED");
        System.exit(ok ? 0 : 1);
    }
}
