package com.micaftic.morpher.resource;

import com.micaftic.morpher.RuntimeAccelerationLoader;
import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.audio.AudioCodec;
import com.micaftic.morpher.audio.AudioTrackData;
import com.micaftic.morpher.client.ClientModelInfo;
import com.micaftic.morpher.core.compat.oculus.ShadersTextureType;
import com.micaftic.morpher.client.gui.custom.AbstractConfig;
import com.micaftic.morpher.client.gui.custom.ExtraAnimationButtons;
import com.micaftic.morpher.client.gui.custom.configs.CheckboxConfig;
import com.micaftic.morpher.client.gui.custom.configs.RadioConfig;
import com.micaftic.morpher.client.gui.custom.configs.RangeConfig;
import com.micaftic.morpher.client.model.MainModelData;
import com.micaftic.morpher.client.model.SpecialHandLocatorProfile;
import com.micaftic.morpher.client.texture.OuterFileTexture;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.geckolib3.core.builder.Animation;
import com.micaftic.morpher.geckolib3.core.builder.AnimationController;
import com.micaftic.morpher.geckolib3.core.builder.AnimationState;
import com.micaftic.morpher.geckolib3.core.builder.ILoopType;
import com.micaftic.morpher.geckolib3.core.event.ParticleEventKeyFrame;
import com.micaftic.morpher.geckolib3.core.keyframe.BoneAnimation;
import com.micaftic.morpher.geckolib3.core.keyframe.bone.BoneKeyFrame;
import com.micaftic.morpher.geckolib3.core.keyframe.bone.BoneKeyFrameProcessor;
import com.micaftic.morpher.geckolib3.core.keyframe.bone.EasingType;
import com.micaftic.morpher.geckolib3.core.keyframe.bone.RawBoneKeyFrame;
import com.micaftic.morpher.geckolib3.core.keyframe.event.EventKeyFrame;
import com.micaftic.morpher.geckolib3.core.molang.value.FloatValue;
import com.micaftic.morpher.geckolib3.core.molang.value.IValue;
import com.micaftic.morpher.geckolib3.file.*;
import com.micaftic.morpher.geckolib3.resource.GeckoLibCache;
import com.micaftic.morpher.geckolib3.util.IInterpolable;
import com.micaftic.morpher.geckolib3.util.LinearKeyframeInterpolator;
import com.micaftic.morpher.geckolib3.util.TicksInterpolator;
import com.micaftic.morpher.model.format.ServerModelInfo;
import com.micaftic.morpher.resource.models.*;
import com.micaftic.morpher.geckolib3.geo.render.built.GeoBone;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import com.micaftic.morpher.util.data.OrderedStringMap;
import com.micaftic.morpher.util.data.StringMapPair;
import com.micaftic.morpher.util.data.StringPair;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import org.apache.commons.lang3.tuple.Pair;
import org.gagravarr.ogg.OggFile;
import org.gagravarr.ogg.OggPacketReader;
import org.gagravarr.opus.OpusFile;
import org.gagravarr.vorbis.VorbisFile;
import org.joml.Vector2f;
import org.joml.Vector3f;
import rip.ysm.imagestream.avif.AvifDecoder;
import rip.ysm.imagestream.webp.WebpDecoder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.IntStream;

public class YSMClientMapper {

    public static class TranslucencyScanner {
        private static final int TILE_SIZE = 8;

        private final BufferedImage[] images;
        private final boolean[] results;
        private final AlphaTileSummary[] alphaSummaries;
//        private int remaining;

        public static final int STATE_INVISIBLE = 0;
        public static final int STATE_OPAQUE = 1;
        public static final int STATE_TRANSLUCENT = 2;

        public TranslucencyScanner(BufferedImage[] images, int expectedCount) {
            this.images = images;
            this.results = new boolean[Math.max(expectedCount, images.length)];
            this.alphaSummaries = new AlphaTileSummary[images.length];
            for (int i = 0; i < images.length; i++) {
                if (images[i] != null) {
                    alphaSummaries[i] = new AlphaTileSummary(images[i]);
                }
            }
//            this.remaining = images.length;
//
//            for (BufferedImage image : images) {
//                if (image == null) {
//                    remaining--;
//                }
//            }
        }

//        public boolean isFinished() {
//            return remaining <= 0;
//        }

        public boolean[] getResults() {
            return results;
        }

        public int scan(RawYsmModel.RawFace face) {
            float minU = face.u[0], maxU = face.u[0];
            float minV = face.v[0], maxV = face.v[0];
            for (int i = 1; i < 4; i++) {
                minU = Math.min(minU, face.u[i]);
                maxU = Math.max(maxU, face.u[i]);
                minV = Math.min(minV, face.v[i]);
                maxV = Math.max(maxV, face.v[i]);
            }

            boolean hasValidImage = false;
            boolean faceHasVisiblePixel = false;
            boolean faceHasTransparentPixel = false;

            for (int i = 0; i < images.length; i++) {
                if (images[i] == null) continue;
                hasValidImage = true;

                BufferedImage img = images[i];
                int imgW = img.getWidth();
                int imgH = img.getHeight();

                int startX = (int) Math.floor(minU * imgW + 0.01f);
                int endX = (int) Math.floor(maxU * imgW - 0.01f);
                if (endX < startX) endX = startX;

                int startY = (int) Math.floor(minV * imgH + 0.01f);
                int endY = (int) Math.floor(maxV * imgH - 0.01f);
                if (endY < startY) endY = startY;

                startX = Math.max(0, Math.min(startX, imgW - 1));
                endX = Math.max(0, Math.min(endX, imgW - 1));
                startY = Math.max(0, Math.min(startY, imgH - 1));
                endY = Math.max(0, Math.min(endY, imgH - 1));

                AlphaState alphaState = alphaSummaries[i] != null
                        ? alphaSummaries[i].scan(startX, startY, endX, endY)
                        : scanPixels(img, startX, startY, endX, endY);
                boolean imageHasVisiblePixel = alphaState.hasVisiblePixel;
                boolean imageHasTransparentPixel = alphaState.hasTransparentPixel;
                boolean imageHasColoredTranslucentPixel = alphaState.hasColoredTranslucentPixel;

                if (imageHasVisiblePixel) {
                    faceHasVisiblePixel = true;

                    if (imageHasTransparentPixel) {
                        faceHasTransparentPixel = true;
                    }

                    if (imageHasColoredTranslucentPixel) {
                        results[i] = true;
                    }
                }
            }

            if (!hasValidImage) return STATE_OPAQUE;
            if (!faceHasVisiblePixel) return STATE_INVISIBLE;
            if (faceHasTransparentPixel) return STATE_TRANSLUCENT;
            return STATE_OPAQUE;
        }

        private static AlphaState scanPixels(BufferedImage img, int startX, int startY, int endX, int endY) {
            boolean hasVisiblePixel = false;
            boolean hasTransparentPixel = false;
            boolean hasColoredTranslucentPixel = false;

            for (int x = startX; x <= endX; x++) {
                for (int y = startY; y <= endY; y++) {
                    int alpha = (img.getRGB(x, y) >>> 24) & 0xFF;

                    if (alpha > 0) {
                        hasVisiblePixel = true;

                        if (alpha < 255) {
                            hasColoredTranslucentPixel = true;
                        }
                    }

                    if (alpha < 255) {
                        hasTransparentPixel = true;
                    }

                    if (hasVisiblePixel && hasTransparentPixel && hasColoredTranslucentPixel) {
                        return new AlphaState(true, true, true);
                    }
                }
            }

            return new AlphaState(hasVisiblePixel, hasTransparentPixel, hasColoredTranslucentPixel);
        }

        private record AlphaState(boolean hasVisiblePixel, boolean hasTransparentPixel, boolean hasColoredTranslucentPixel) {
        }

        private static final class AlphaTileSummary {
            private final BufferedImage image;
            private final int tileColumns;
            private final int tileRows;
            private final byte[] flags;

            AlphaTileSummary(BufferedImage image) {
                this.image = image;
                this.tileColumns = Math.max(1, (image.getWidth() + TILE_SIZE - 1) / TILE_SIZE);
                this.tileRows = Math.max(1, (image.getHeight() + TILE_SIZE - 1) / TILE_SIZE);
                this.flags = new byte[tileColumns * tileRows];
                build();
            }

            AlphaState scan(int startX, int startY, int endX, int endY) {
                int fullStartTileX = (startX + TILE_SIZE - 1) / TILE_SIZE;
                int fullEndTileX = (endX + 1) / TILE_SIZE - 1;
                int fullStartTileY = (startY + TILE_SIZE - 1) / TILE_SIZE;
                int fullEndTileY = (endY + 1) / TILE_SIZE - 1;
                int fullTileColumns = fullEndTileX - fullStartTileX + 1;
                int fullTileRows = fullEndTileY - fullStartTileY + 1;
                if (fullTileColumns <= 0 || fullTileRows <= 0 || fullTileColumns * fullTileRows <= 2) {
                    return scanPixels(image, startX, startY, endX, endY);
                }

                boolean hasVisiblePixel = false;
                boolean hasTransparentPixel = false;
                boolean hasColoredTranslucentPixel = false;
                for (int ty = fullStartTileY; ty <= fullEndTileY; ty++) {
                    int row = ty * tileColumns;
                    for (int tx = fullStartTileX; tx <= fullEndTileX; tx++) {
                        byte flag = flags[row + tx];
                        hasVisiblePixel |= (flag & 1) != 0;
                        hasTransparentPixel |= (flag & 2) != 0;
                        hasColoredTranslucentPixel |= (flag & 4) != 0;
                        if (hasVisiblePixel && hasTransparentPixel && hasColoredTranslucentPixel) {
                            return new AlphaState(true, true, true);
                        }
                    }
                }

                int fullStartX = fullStartTileX * TILE_SIZE;
                int fullEndX = Math.min(image.getWidth() - 1, (fullEndTileX + 1) * TILE_SIZE - 1);
                int fullStartY = fullStartTileY * TILE_SIZE;
                int fullEndY = Math.min(image.getHeight() - 1, (fullEndTileY + 1) * TILE_SIZE - 1);

                if (startY < fullStartY) {
                    AlphaState edge = scanPixels(image, startX, startY, endX, fullStartY - 1);
                    hasVisiblePixel |= edge.hasVisiblePixel;
                    hasTransparentPixel |= edge.hasTransparentPixel;
                    hasColoredTranslucentPixel |= edge.hasColoredTranslucentPixel;
                }
                if (endY > fullEndY) {
                    AlphaState edge = scanPixels(image, startX, fullEndY + 1, endX, endY);
                    hasVisiblePixel |= edge.hasVisiblePixel;
                    hasTransparentPixel |= edge.hasTransparentPixel;
                    hasColoredTranslucentPixel |= edge.hasColoredTranslucentPixel;
                }
                if (startX < fullStartX) {
                    AlphaState edge = scanPixels(image, startX, fullStartY, fullStartX - 1, fullEndY);
                    hasVisiblePixel |= edge.hasVisiblePixel;
                    hasTransparentPixel |= edge.hasTransparentPixel;
                    hasColoredTranslucentPixel |= edge.hasColoredTranslucentPixel;
                }
                if (endX > fullEndX) {
                    AlphaState edge = scanPixels(image, fullEndX + 1, fullStartY, endX, fullEndY);
                    hasVisiblePixel |= edge.hasVisiblePixel;
                    hasTransparentPixel |= edge.hasTransparentPixel;
                    hasColoredTranslucentPixel |= edge.hasColoredTranslucentPixel;
                }
                return new AlphaState(hasVisiblePixel, hasTransparentPixel, hasColoredTranslucentPixel);
            }

            private void build() {
                int width = image.getWidth();
                int height = image.getHeight();
                for (int ty = 0; ty < tileRows; ty++) {
                    int startY = ty * TILE_SIZE;
                    int endY = Math.min(height, startY + TILE_SIZE);
                    for (int tx = 0; tx < tileColumns; tx++) {
                        int startX = tx * TILE_SIZE;
                        int endX = Math.min(width, startX + TILE_SIZE);
                        byte flag = 0;
                        for (int y = startY; y < endY; y++) {
                            for (int x = startX; x < endX; x++) {
                                int alpha = (image.getRGB(x, y) >>> 24) & 0xFF;
                                if (alpha > 0) {
                                    flag |= 1;
                                    if (alpha < 255) {
                                        flag |= 4;
                                    }
                                }
                                if (alpha < 255) {
                                    flag |= 2;
                                }
                                if ((flag & 7) == 7) {
                                    break;
                                }
                            }
                            if ((flag & 7) == 7) {
                                break;
                            }
                        }
                        flags[ty * tileColumns + tx] = flag;
                    }
                }
            }
        }
    }

    private static BufferedImage decodeToImage(byte[] data, int imageFormat, int width, int height) {
        if (data == null || data.length == 0) {
            return null;
        }

        imageFormat = resolveImageFormat(data, imageFormat);

        try {
            if (imageFormat == -1) {
                if (width > 0 && height > 0 && data.length >= width * height * 4) {
                    BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                    int[] pixels = new int[width * height];
                    for (int i = 0; i < pixels.length; i++) {
                        int r = data[i * 4] & 0xFF;
                        int g = data[i * 4 + 1] & 0xFF;
                        int b = data[i * 4 + 2] & 0xFF;
                        int a = data[i * 4 + 3] & 0xFF;
                        pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
                    }
                    img.setRGB(0, 0, width, height, pixels, 0, width);
                    return img;
                } else throw new RuntimeException("Invalid RGBA texture");
            } else {
                switch (imageFormat) {
                    case 1:
                    case 2:
                    case 3:
                        return ImageIO.read(new ByteArrayInputStream(data));
                    case 4:
                        return new WebpDecoder().read(data);
                    case 5:
                        return new AvifDecoder().read(data);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static final byte[] TRANSPARENT_PIXEL_PNG = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+P+/HgAEtAJJXIDTjwAAAABJRU5ErkJggg==");

    private static boolean isPng(byte[] data) {
        return data != null
                && data.length >= 8
                && (data[0] & 0xFF) == 0x89
                && data[1] == 0x50
                && data[2] == 0x4E
                && data[3] == 0x47
                && data[4] == 0x0D
                && data[5] == 0x0A
                && data[6] == 0x1A
                && data[7] == 0x0A;
    }

    private static byte[] encodeToPng(BufferedImage img, byte[] fallbackData) {
        if (img != null) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                if (ImageIO.write(img, "png", baos)) {
                    return baos.toByteArray();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (isPng(fallbackData)) {
            return fallbackData;
        }
        System.err.println("[SM] Warning: Texture decode failed; using transparent fallback PNG.");
        return TRANSPARENT_PIXEL_PNG;
    }

    private static int resolveImageFormat(byte[] data, int imageFormat) {
        if (imageFormat == -1 || data == null || data.length == 0) {
            return imageFormat;
        }
        int detectedFormat = YSMFolderDeserializer.detectFormat(data);
        if (detectedFormat != 0) {
            return detectedFormat;
        }
        return imageFormat == 0 ? 1 : imageFormat;
    }

    public static byte[] toPng(byte[] data, int imageFormat, int width, int height) {
        imageFormat = resolveImageFormat(data, imageFormat);
        if (imageFormat == 2) {
            return data;
        }
        BufferedImage img = decodeToImage(data, imageFormat, width, height);
        return encodeToPng(img, data);
    }

    public static ClientModelInfo buildParsedBundle(RawYsmModel raw, String modelId) {
        Map<String, OuterFileTexture> mainTextures = new LinkedHashMap<>();
        int textureCount = Math.max(1, raw.mainEntity.textures.size());

        List<BufferedImage> imagesList = new ArrayList<>();

        for (RawYsmModel.RawTexture rt : raw.mainEntity.textures.values()) {
            BufferedImage img = decodeToImage(rt.data, rt.imageFormat, rt.width, rt.height);
            imagesList.add(img);

            byte[] processedData = toPng(rt.data, rt.imageFormat, rt.width, rt.height);
            OuterFileTexture tex = new OuterFileTexture(processedData, modelId);

            Map<ShadersTextureType, OuterFileTexture> suffixTextures = new LinkedHashMap<>();
            for (RawYsmModel.RawTexture.SubTexture sub : rt.subTextures) {
                if (sub.data == null) continue;
                byte[] processedSubData = toPng(sub.data, sub.imageFormat, sub.width, sub.height);
                if (sub.specularType == 1) {
                    suffixTextures.put(ShadersTextureType.NORMAL, new OuterFileTexture(processedSubData, modelId));
                } else if (sub.specularType == 2) {
                    suffixTextures.put(ShadersTextureType.SPECULAR, new OuterFileTexture(processedSubData, modelId));
                }
            }
            tex.setSuffixTextures(suffixTextures);
            mainTextures.put(rt.name, tex);
        }
        Map<String, OuterFileTexture> avatarTextures = new LinkedHashMap<>();
        for (RawYsmModel.RawMetadata.Author author : raw.metadata.authors) {
            if (author.avatarImage == null) continue;
            byte[] processedAvatarData = toPng(author.avatarImage.data, author.avatarImage.format, author.avatarImage.width, author.avatarImage.height);
            OuterFileTexture tex = new OuterFileTexture(processedAvatarData, modelId);
            avatarTextures.put(author.avatarImage.name, tex);
        }
        OrderedStringMap<String, OuterFileTexture> textureMap = buildTextureMap(mainTextures);

        GeometryDescription context = buildContext(raw.mainEntity.mainModel);

        BufferedImage[] imagesArray = imagesList.toArray(new BufferedImage[0]);
        TranslucencyScanner mainScanner = raw.mainEntity.mainModel != null ?
                new TranslucencyScanner(imagesArray, textureCount) : null;
        TranslucencyScanner armScanner = raw.mainEntity.armModel != null ?
                new TranslucencyScanner(imagesArray, textureCount) : null;

        Map<String, String> mainParentMap = buildParentMap(raw.mainEntity.mainModel);
        SpecialHandLocatorProfile specialHandLocatorProfile = detectSpecialHandLocatorProfile(raw, mainParentMap);

        GeoModel mainMesh = buildMesh(raw.mainEntity.mainModel, context, textureCount, mainScanner, raw.properties.allCutout, specialHandLocatorProfile);
        GeoModel armMesh = raw.mainEntity.armModel != null ? buildMesh(raw.mainEntity.armModel, context, textureCount, armScanner, raw.properties.allCutout) : mainMesh;

//        System.out.println(modelId + Arrays.toString(mainMesh.translucentTexture));

        GeoModel[] meshes = new GeoModel[]{mainMesh, armMesh};

        Map<String, AnimationFile> animations = new LinkedHashMap<>();
        for (Map.Entry<String, RawYsmModel.RawAnimationFile> entry : raw.mainEntity.animationFiles.entrySet()) {
            animations.put(entry.getKey(), new AnimationFile(buildAnimations(entry.getValue(), raw.properties.mergeMultilineExpr)));
        }

        List<AnimationControllerFile> controllersList = new ArrayList<>();
        if (raw.mainEntity.animationControllerFiles != null) {
            for (RawYsmModel.RawAnimationControllerFile file : raw.mainEntity.animationControllerFiles) {
                Map<String, AnimationController> controllerMap = buildControllers(file.controllers, raw.properties.mergeMultilineExpr);
                if (!controllerMap.isEmpty()) {
                    controllersList.add(new AnimationControllerFile(controllerMap));
                }
            }
        }

        MainModelData mainModelData = new MainModelData(meshes, animations, controllersList.toArray(new AnimationControllerFile[0]), textureMap, specialHandLocatorProfile);

        ServerModelInfo modelInfo = buildModelInfo(raw);
        ModelExtraResourcesFile extraResources = buildExtraResources(raw);
        ProjectileModelFiles[] extraItemModels = buildExtraItemModels(raw, context, raw.properties.mergeMultilineExpr);
        VehicleModelFiles[] extraEntityModels = buildExtraEntityModels(raw, context, raw.properties.mergeMultilineExpr);
        Map<String, OuterFileTexture> extraTextures = buildExtraTextures(raw);

        return new ClientModelInfo(mainModelData, extraItemModels, extraEntityModels, extraResources, modelInfo, avatarTextures, extraTextures);
    }

    private static GeoModel buildMesh(RawYsmModel.RawGeometry rawGeo, GeometryDescription context, int textureCount, TranslucencyScanner scanner, boolean allCutout) {
        return buildMesh(rawGeo, context, textureCount, scanner, allCutout, SpecialHandLocatorProfile.NONE);
    }

    private static GeoModel buildMesh(RawYsmModel.RawGeometry rawGeo, GeometryDescription context, int textureCount, TranslucencyScanner scanner, boolean allCutout, SpecialHandLocatorProfile specialHandLocatorProfile) {
        long bakeStart = System.nanoTime();
        ModelOptimizationStats stats = new ModelOptimizationStats();
        stats.textures = textureCount;
        if (rawGeo == null || rawGeo.bones.isEmpty()) {
            boolean[] fallbackArray = scanner != null ? scanner.getResults() : new boolean[Math.max(1, textureCount)];
            GeoModel mesh = buildMesh(new GeoBone[0], new HashMap<>(), context, fallbackArray);
            stats.importBakeMillis = (System.nanoTime() - bakeStart) / 1_000_000L;
            mesh.optimizationStats = stats;
            logModelOptimizationStats(stats);
            return mesh;
        }

        List<GeoBone> geoBones = new ArrayList<>();
        List<GeoModel.BakedBone> bakedBones = new ArrayList<>();
        Map<String, String> parentMap = new HashMap<>();

        for (RawYsmModel.RawBone rb : rawGeo.bones) {
            stats.bones++;
            parentMap.put(rb.name, rb.parentName);
            geoBones.add(new GeoBone(rb.name, false, false, false, rb.pivot[0], rb.pivot[1], rb.pivot[2], rb.rotation[0], rb.rotation[1], rb.rotation[2]));

            GeoModel.BakedBone bb = new GeoModel.BakedBone();
            bb.name = rb.name;
            if (rb.name.startsWith("ysmGlow")) bb.glow = true;
            bb.pivotX = rb.pivot[0];
            bb.pivotY = rb.pivot[1];
            bb.pivotZ = rb.pivot[2];
            bb.rotX = rb.rotation[0];
            bb.rotY = rb.rotation[1];
            bb.rotZ = rb.rotation[2];
            bb.parentIdx = -1;

            // TODO: 优化算法

            boolean forceCull = allCutout;

            for (RawYsmModel.RawCube rc : rb.cubes) {
                stats.cubes++;
                GeoModel.BakedCube bc = new GeoModel.BakedCube();

                int validFaceCount = 0;
                boolean hasTranslucentFace = false;

                for (RawYsmModel.RawFace rf : rc.faces) {
                    stats.quadsBefore++;
                    int faceState = scanner != null ? scanner.scan(rf) : TranslucencyScanner.STATE_OPAQUE;

                    if (faceState == TranslucencyScanner.STATE_INVISIBLE) {
                        stats.prunedInvisibleFaces++;
                        continue;
                    }

                    if (faceState == TranslucencyScanner.STATE_TRANSLUCENT) {
                        hasTranslucentFace = true;
                        stats.translucentFaces++;
                    } else {
                        stats.opaqueFaces++;
                    }

                    if (!forceCull && isNegativeSizedFace(rf)) {
                        forceCull = true;
                    }
                    if (forceCull) {
                        stats.cutoutFaces++;
                    }
                    if (bb.glow) {
                        stats.glowFaces++;
                    }

                    GeoModel.BakedQuad bq = new GeoModel.BakedQuad();
                    bq.setNormal(rf.normal[0], rf.normal[1], rf.normal[2]);
                    for (int i = 0; i < 4; i++) {
                        float px = rf.positions[i][0];
                        float py = rf.positions[i][1];
                        float pz = rf.positions[i][2];

                        bq.setVertex(i, px, py, pz, rf.u[i], rf.v[i]);
                    }
                    bc.quads.add(bq);
                    stats.quadsAfter++;
                    validFaceCount++;
                }

                boolean isZeroThickness = true;
                if (!bc.quads.isEmpty()) {
                    GeoModel.BakedQuad baseQuad = bc.quads.get(0);
                    float baseNormalX = baseQuad.normalX;
                    float baseNormalY = baseQuad.normalY;
                    float baseNormalZ = baseQuad.normalZ;
                    float baseX = baseQuad.x(0);
                    float baseY = baseQuad.y(0);
                    float baseZ = baseQuad.z(0);

                    for (GeoModel.BakedQuad q : bc.quads) {
                        for (int i = 0; i < 4; i++) {
                            float dx = q.x(i) - baseX;
                            float dy = q.y(i) - baseY;
                            float dz = q.z(i) - baseZ;

                            float distance = dx * baseNormalX + dy * baseNormalY + dz * baseNormalZ;

                            if (Math.abs(distance) > 1e-3f) {
                                isZeroThickness = false;
                                break;
                            }
                        }
                        if (!isZeroThickness) break;
                    }
                } else {
                    isZeroThickness = false;
                }

                if (forceCull) {
                    bc.cullable = true;
                } else if (hasTranslucentFace) {
                    bc.cullable = false;
                } else if (isZeroThickness && validFaceCount > 1) {
                    bc.cullable = true;
                    stats.zeroThicknessFaces += validFaceCount;
                } else {
                    bc.cullable = validFaceCount >= 5;
                }

                if (!bc.quads.isEmpty()) {
                    bb.cubes.add(bc);
                }
            }
            bakedBones.add(bb);
        }

        // 回填父级索引
        for (GeoModel.BakedBone b : bakedBones) {
            String parentName = parentMap.get(b.name);
            if (parentName != null && !parentName.isEmpty()) {
                for (int i = 0; i < bakedBones.size(); i++) {
                    if (bakedBones.get(i).name.equals(parentName)) {
                        b.parentIdx = i;
                        break;
                    }
                }
            }
            if (b.name.equals("LeftArm")) b.partMask = 1;
            else if (b.name.equals("RightArm")) b.partMask = 2;
            else if (b.name.equals("Background")) b.partMask = 3;
            else if (b.parentIdx != -1) b.partMask = bakedBones.get(b.parentIdx).partMask;
            else b.partMask = 0;
        }
        finalizeOptimizationStats(bakedBones, stats);

        boolean[] translucencyArray = scanner != null ? scanner.getResults() : new boolean[Math.max(1, textureCount)];
        GeoModel mesh = buildMesh(geoBones.toArray(new GeoBone[0]), parentMap, context, translucencyArray, specialHandLocatorProfile);

        mesh.bakedBones = bakedBones;
        mesh.bakedBoneOrder = GeoModel.buildParentFirstBoneOrder(bakedBones);
        mesh.buildPartMaskBoneRenderOrders();
        stats.importBakeMillis = (System.nanoTime() - bakeStart) / 1_000_000L;
        mesh.optimizationStats = stats;
        logModelOptimizationStats(stats);
        if (RuntimeAccelerationLoader.isLoaded()) mesh.buildNativeCache();
        return mesh;
    }

    private static void finalizeOptimizationStats(List<GeoModel.BakedBone> bakedBones, ModelOptimizationStats stats) {
        int totalQuads = 0;
        int totalCubes = 0;
        for (int boneIdx = 0; boneIdx < bakedBones.size(); boneIdx++) {
            GeoModel.BakedBone bone = bakedBones.get(boneIdx);
            totalCubes += bone.cubes.size();
            for (GeoModel.BakedCube cube : bone.cubes) {
                totalQuads += cube.quads.size();
                switch (bone.partMask) {
                    case 1 -> stats.partMaskLeftArmQuads += cube.quads.size();
                    case 2 -> stats.partMaskRightArmQuads += cube.quads.size();
                    default -> stats.partMaskAllQuads += cube.quads.size();
                }
            }
        }
        stats.estimatedBakedBytes = estimateBakedBytes(bakedBones.size(), totalCubes, totalQuads);
        stats.estimatedGpuMeshBytes = estimateGpuMeshBytes(totalQuads, bakedBones.size());
        stats.internalFaceCandidatePairs = countConservativeInternalFaceCandidates(bakedBones);
    }

    private static long estimateBakedBytes(int bones, int cubes, int quads) {
        return (long) bones * 96L + (long) cubes * 32L + (long) quads * 160L;
    }

    private static long estimateGpuMeshBytes(int quads, int bones) {
        long vertexBytes = (long) quads * 4L * 32L;
        long indexBytes = (long) quads * 6L * Integer.BYTES;
        long boneBytes = (long) bones * 144L * 2L;
        return vertexBytes + indexBytes + boneBytes;
    }

    private static int countConservativeInternalFaceCandidates(List<GeoModel.BakedBone> bakedBones) {
        Map<FaceAuditKey, int[]> buckets = new HashMap<>();
        for (int boneIdx = 0; boneIdx < bakedBones.size(); boneIdx++) {
            GeoModel.BakedBone bone = bakedBones.get(boneIdx);
            if (bone.glow) {
                continue;
            }
            for (GeoModel.BakedCube cube : bone.cubes) {
                for (GeoModel.BakedQuad quad : cube.quads) {
                    int axis = dominantAxis(quad.normalX, quad.normalY, quad.normalZ);
                    if (axis < 0 || isZeroAreaQuad(quad, axis)) {
                        continue;
                    }
                    int sign = normalSign(quad, axis);
                    if (sign == 0) {
                        continue;
                    }
                    FaceAuditKey key = FaceAuditKey.from(boneIdx, bone.partMask, axis, quad);
                    int[] counts = buckets.computeIfAbsent(key, ignored -> new int[2]);
                    counts[sign > 0 ? 1 : 0]++;
                }
            }
        }
        int pairs = 0;
        for (int[] counts : buckets.values()) {
            pairs += Math.min(counts[0], counts[1]);
        }
        return pairs;
    }

    private static int dominantAxis(float x, float y, float z) {
        float ax = Math.abs(x);
        float ay = Math.abs(y);
        float az = Math.abs(z);
        if (ax < 0.999f && ay < 0.999f && az < 0.999f) {
            return -1;
        }
        if (ax >= ay && ax >= az) return 0;
        if (ay >= az) return 1;
        return 2;
    }

    private static int normalSign(GeoModel.BakedQuad quad, int axis) {
        float value = switch (axis) {
            case 0 -> quad.normalX;
            case 1 -> quad.normalY;
            default -> quad.normalZ;
        };
        if (value > 0.999f) return 1;
        if (value < -0.999f) return -1;
        return 0;
    }

    private static boolean isZeroAreaQuad(GeoModel.BakedQuad quad, int axis) {
        float minA = Float.POSITIVE_INFINITY;
        float maxA = Float.NEGATIVE_INFINITY;
        float minB = Float.POSITIVE_INFINITY;
        float maxB = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            float a;
            float b;
            if (axis == 0) {
                a = quad.y(i);
                b = quad.z(i);
            } else if (axis == 1) {
                a = quad.x(i);
                b = quad.z(i);
            } else {
                a = quad.x(i);
                b = quad.y(i);
            }
            minA = Math.min(minA, a);
            maxA = Math.max(maxA, a);
            minB = Math.min(minB, b);
            maxB = Math.max(maxB, b);
        }
        return Math.abs(maxA - minA) <= 1.0e-4f || Math.abs(maxB - minB) <= 1.0e-4f;
    }

    private record FaceAuditKey(int boneIndex, int partMask, int axis, int plane, int minA, int maxA, int minB, int maxB) {
        static FaceAuditKey from(int boneIndex, int partMask, int axis, GeoModel.BakedQuad quad) {
            float planeValue;
            float minA = Float.POSITIVE_INFINITY;
            float maxA = Float.NEGATIVE_INFINITY;
            float minB = Float.POSITIVE_INFINITY;
            float maxB = Float.NEGATIVE_INFINITY;
            if (axis == 0) {
                planeValue = quad.x(0);
            } else if (axis == 1) {
                planeValue = quad.y(0);
            } else {
                planeValue = quad.z(0);
            }
            for (int i = 0; i < 4; i++) {
                float a;
                float b;
                if (axis == 0) {
                    a = quad.y(i);
                    b = quad.z(i);
                } else if (axis == 1) {
                    a = quad.x(i);
                    b = quad.z(i);
                } else {
                    a = quad.x(i);
                    b = quad.y(i);
                }
                minA = Math.min(minA, a);
                maxA = Math.max(maxA, a);
                minB = Math.min(minB, b);
                maxB = Math.max(maxB, b);
            }
            return new FaceAuditKey(
                    boneIndex,
                    partMask,
                    axis,
                    quantize(planeValue),
                    quantize(minA),
                    quantize(maxA),
                    quantize(minB),
                    quantize(maxB)
            );
        }

        private static int quantize(float value) {
            return Math.round(value * 1000.0f);
        }
    }

    private static void logModelOptimizationStats(ModelOptimizationStats stats) {
        if (GeneralConfig.safeGet(GeneralConfig.MODEL_IMPORT_PERFORMANCE_LOG, false)) {
            YesSteveModel.LOGGER.info("[SM][Perf] model optimization {}", stats.toLogString());
        }
    }

    public static Map<String, Animation> buildAnimations(RawYsmModel.RawAnimationFile animFile, boolean mergeMultilineExpr) {
        Map<String, Animation> result = new LinkedHashMap<>();
        for (RawYsmModel.RawAnimation ra : animFile.animations.values()) {
            ILoopType loopMode = ILoopType.EDefaultLoopTypes.PLAY_ONCE;
            if (ra.loopMode == 1) loopMode = ILoopType.EDefaultLoopTypes.LOOP;
            else if (ra.loopMode == 3) loopMode = ILoopType.EDefaultLoopTypes.HOLD_ON_LAST_FRAME;

            List<BoneAnimation> boneAnims = new ArrayList<>();
            for (RawYsmModel.RawBoneAnimation rba : ra.boneAnimations) {
//                if (rba.boneName.equals("gun")) {
//                    "".hashCode();
//                }
                List<BoneKeyFrame> rotFrames = parseKeyframes(rba.rotation, true);
                List<BoneKeyFrame> posFrames = parseKeyframes(rba.position, false);
                List<BoneKeyFrame> scaleFrames = parseKeyframes(rba.scale, false);
                boneAnims.add(new BoneAnimation(rba.boneName, rotFrames, posFrames, scaleFrames));
            }

            List<EventKeyFrame<String>> soundEffects = new ArrayList<>();
            for (RawYsmModel.RawSoundEffect rse : ra.soundEffects) {
                soundEffects.add(new EventKeyFrame<>(rse.timestamp * 20.0f, rse.effectName));
            }

            List<EventKeyFrame<IValue[]>> timelineEvents = new ArrayList<>();
            for (RawYsmModel.RawTimelineEvent rte : ra.timelineEvents) {
                List<IValue> values = parse(rte.events, mergeMultilineExpr);
                timelineEvents.add(new EventKeyFrame<>(rte.timestamp * 20.0f, values.toArray(new IValue[0])));
            }

            IValue blendWeight;
            if (ra.blendWeight instanceof Float)
                blendWeight = new FloatValue((Float) ra.blendWeight);
            else if (ra.blendWeight instanceof String)
                try {
                    blendWeight = parse((String) ra.blendWeight);
                } catch (Exception e) {
                    blendWeight = null;
                }
            else blendWeight = null;

            Animation anim = new Animation(ra.name, ra.length * 20.0f, loopMode, blendWeight, null, null, null, boneAnims.toArray(new BoneAnimation[0]), soundEffects.toArray(new EventKeyFrame[0]), new ParticleEventKeyFrame[0], timelineEvents.toArray(new EventKeyFrame[0]));
            result.put(ra.name, anim);
        }
        return result;
    }

    private static List<BoneKeyFrame> parseKeyframes(List<RawYsmModel.RawKeyframe> frames, boolean isRotation) {
        List<RawBoneKeyFrame> builders = new ArrayList<>();
        for (RawYsmModel.RawKeyframe rk : frames) {
            RawBoneKeyFrame builder = new RawBoneKeyFrame();
            builder.startTick = rk.timestamp * 20.0f;
            builder.easingType = easingTypeFor(rk);
            builder.contiguous = !rk.hasPreData;

            if (rk.hasPreData) {
                assignToBuilder(builder, rk.preData, true);
                assignToBuilder(builder, rk.postData, false);
            } else {
                assignToBuilder(builder, rk.postData, true);
            }
            builders.add(builder);
        }
        return BoneKeyFrameProcessor.process(builders, isRotation);
    }

    private static EasingType easingTypeFor(RawYsmModel.RawKeyframe keyframe) {
        switch (keyframe.interpolationMode) {
            case RawYsmModel.RawKeyframe.INTERPOLATION_STEP:
                return EasingType.STEP;
            case RawYsmModel.RawKeyframe.INTERPOLATION_CATMULLROM:
                return EasingType.CATMULLROM;
            case RawYsmModel.RawKeyframe.INTERPOLATION_BEZIER:
                if (GeneralConfig.safeGet(GeneralConfig.ANIMATION_DEBUG_LOG, false)) {
                    YesSteveModel.LOGGER.warn("[SM-ANIM] Bezier keyframe at {}s was downgraded to linear because runtime Bezier evaluation is not available yet", keyframe.timestamp);
                }
                return EasingType.LINEAR;
            default:
                return EasingType.LINEAR;
        }
    }

    private static Map<String, String> buildParentMap(RawYsmModel.RawGeometry rawGeo) {
        Map<String, String> parentMap = new HashMap<>();
        if (rawGeo == null || rawGeo.bones == null) {
            return parentMap;
        }
        for (RawYsmModel.RawBone bone : rawGeo.bones) {
            if (bone != null && bone.name != null && !bone.name.isEmpty()) {
                parentMap.put(bone.name, bone.parentName);
            }
        }
        return parentMap;
    }

    private static SpecialHandLocatorProfile detectSpecialHandLocatorProfile(RawYsmModel raw, Map<String, String> parentMap) {
        if (raw == null || raw.mainEntity == null || parentMap == null || parentMap.isEmpty()) {
            return SpecialHandLocatorProfile.NONE;
        }
        if (!hasNormalizedExactBone(parentMap, "LeftHandLocator") || !hasNormalizedExactBone(parentMap, "RightHandLocator")) {
            return SpecialHandLocatorProfile.NONE;
        }
        if (hasNormalizedSwordBone(parentMap, "LeftSword") || hasNormalizedSwordBone(parentMap, "RightSword")) {
            return SpecialHandLocatorProfile.NONE;
        }
        for (int i = 2; i <= 8; i++) {
            if (hasNormalizedExactBone(parentMap, "LeftHandLocator" + i) || hasNormalizedExactBone(parentMap, "RightHandLocator" + i)) {
                return SpecialHandLocatorProfile.NONE;
            }
        }

        boolean carryOnZero = false;
        boolean nonCarryOnZero = false;
        for (RawYsmModel.RawAnimationFile animationFile : raw.mainEntity.animationFiles.values()) {
            ScaleZeroScanResult result = scanHandLocatorScaleZero(animationFile);
            if (result.unsafe()) {
                return SpecialHandLocatorProfile.NONE;
            }
            if (result.hasScaleZero()) {
                if (animationFile.animType == 6) {
                    carryOnZero = true;
                } else {
                    nonCarryOnZero = true;
                }
            }
        }
        if (!carryOnZero || nonCarryOnZero) {
            return SpecialHandLocatorProfile.NONE;
        }
        return SpecialHandLocatorProfile.HAND_LOCATOR_HIDDEN_BY_CARRYON;
    }

    private static ScaleZeroScanResult scanHandLocatorScaleZero(RawYsmModel.RawAnimationFile animationFile) {
        if (animationFile == null || animationFile.animations == null) {
            return ScaleZeroScanResult.NONE;
        }
        boolean found = false;
        for (RawYsmModel.RawAnimation animation : animationFile.animations.values()) {
            if (animation == null || animation.boneAnimations == null) {
                continue;
            }
            for (RawYsmModel.RawBoneAnimation boneAnimation : animation.boneAnimations) {
                if (boneAnimation == null || !isPrimaryHandLocatorName(boneAnimation.boneName) || boneAnimation.scale == null || boneAnimation.scale.isEmpty()) {
                    continue;
                }
                for (RawYsmModel.RawKeyframe keyframe : boneAnimation.scale) {
                    KeyframeScaleResult result = isZeroScaleKeyframe(keyframe);
                    if (result == KeyframeScaleResult.UNSAFE) {
                        return ScaleZeroScanResult.UNSAFE;
                    }
                    if (result == KeyframeScaleResult.ZERO) {
                        found = true;
                    }
                }
            }
        }
        return found ? ScaleZeroScanResult.FOUND : ScaleZeroScanResult.NONE;
    }

    private static KeyframeScaleResult isZeroScaleKeyframe(RawYsmModel.RawKeyframe keyframe) {
        if (keyframe == null) {
            return KeyframeScaleResult.NONE;
        }
        KeyframeScaleResult post = isZeroScaleData(keyframe.postData);
        if (post == KeyframeScaleResult.UNSAFE) {
            return KeyframeScaleResult.UNSAFE;
        }
        if (!keyframe.hasPreData) {
            return post;
        }
        KeyframeScaleResult pre = isZeroScaleData(keyframe.preData);
        if (pre == KeyframeScaleResult.UNSAFE) {
            return KeyframeScaleResult.UNSAFE;
        }
        return pre == KeyframeScaleResult.ZERO || post == KeyframeScaleResult.ZERO ? KeyframeScaleResult.ZERO : KeyframeScaleResult.NONE;
    }

    private static KeyframeScaleResult isZeroScaleData(Object[] data) {
        if (data == null || data.length < 3) {
            return KeyframeScaleResult.NONE;
        }
        for (int i = 0; i < 3; i++) {
            if (!isSafeZeroValue(data[i])) {
                return data[i] instanceof String ? KeyframeScaleResult.UNSAFE : KeyframeScaleResult.NONE;
            }
        }
        return KeyframeScaleResult.ZERO;
    }

    private static boolean isSafeZeroValue(Object value) {
        if (value instanceof Float) {
            return (Float) value == 0.0f;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() == 0.0d;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) {
                return false;
            }
            try {
                return Double.parseDouble(text) == 0.0d;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }

    private static boolean isPrimaryHandLocatorName(String boneName) {
        String normalized = normalizeBoneName(boneName);
        return "lefthandlocator".equals(normalized) || "righthandlocator".equals(normalized);
    }

    private static boolean hasNormalizedExactBone(Map<String, String> parentMap, String targetBone) {
        return findBoneName(targetBone, parentMap) != null;
    }

    private static boolean hasNormalizedSwordBone(Map<String, String> parentMap, String baseName) {
        String normalizedBase = normalizeBoneName(baseName);
        for (String boneName : parentMap.keySet()) {
            String normalizedName = normalizeBoneName(boneName);
            if (normalizedName.equals(normalizedBase)
                    || (normalizedName.startsWith(normalizedBase)
                    && normalizedName.substring(normalizedBase.length()).chars().allMatch(Character::isDigit))) {
                return true;
            }
        }
        return false;
    }

    private enum ScaleZeroScanResult {
        NONE(false, false),
        FOUND(true, false),
        UNSAFE(false, true);

        private final boolean hasScaleZero;
        private final boolean unsafe;

        ScaleZeroScanResult(boolean hasScaleZero, boolean unsafe) {
            this.hasScaleZero = hasScaleZero;
            this.unsafe = unsafe;
        }

        private boolean hasScaleZero() {
            return this.hasScaleZero;
        }

        private boolean unsafe() {
            return this.unsafe;
        }
    }

    private enum KeyframeScaleResult {
        NONE,
        ZERO,
        UNSAFE
    }

    private static void assignToBuilder(RawBoneKeyFrame builder, Object[] data, boolean isPre) {
        for (int axis = 0; axis < 3; axis++) {
            double dVal = 0.0;
            IValue iVal = null;
            Object val = data[axis];
            if (val instanceof Float) dVal = (Float) val;
            else if (val instanceof String) {
                try {
                    iVal = parse((String) val);
                } catch (Exception ignore) {
                }
            }
            if (isPre) {
                if (axis == 0) {
                    builder.preX = dVal;
                    builder.preXValue = iVal;
                } else if (axis == 1) {
                    builder.preY = dVal;
                    builder.preYValue = iVal;
                } else if (axis == 2) {
                    builder.preZ = dVal;
                    builder.preZValue = iVal;
                }
            } else {
                if (axis == 0) {
                    builder.postX = dVal;
                    builder.postXValue = iVal;
                } else if (axis == 1) {
                    builder.postY = dVal;
                    builder.postYValue = iVal;
                } else if (axis == 2) {
                    builder.postZ = dVal;
                    builder.postZValue = iVal;
                }
            }
        }
    }

    private static Map<String, AnimationController> buildControllers(Map<String, RawYsmModel.RawAnimationController> rawControllers, boolean mergeMultilineExpr) {
        Map<String, AnimationController> result = new LinkedHashMap<>();
        for (RawYsmModel.RawAnimationController rac : rawControllers.values()) {
            List<AnimationState> states = new ArrayList<>();
            for (RawYsmModel.RawControllerState rs : rac.states) {
                List<Pair<String, IValue>> animations = new ArrayList<>();
                for (Map.Entry<String, String> e : rs.animations.entrySet()) {
                    IValue blend = null;
                    if (!e.getValue().isEmpty()) {
                        try {
                            blend = parse(e.getValue());
                        } catch (Exception ignore) {
                        }
                    }
                    animations.add(Pair.of(e.getKey(), blend));
                }

                List<Pair<String, IValue>> transitions = new ArrayList<>();
                for (Map.Entry<String, String> e : rs.transitions.entrySet()) {
                    IValue condition = parse(e.getValue());
                    transitions.add(Pair.of(e.getKey(), condition));
                }

                List<IValue> onEntry = parse(rs.onEntry, mergeMultilineExpr);
                List<IValue> onExit = parse(rs.onExit, mergeMultilineExpr);

                IInterpolable blendTransition;
                if (!rs.blendTransitions.isEmpty()) {
                    float[] keys = new float[rs.blendTransitions.size()];
                    float[] values = new float[rs.blendTransitions.size()];
                    int i = 0;
                    for (Map.Entry<Float, Float> e : rs.blendTransitions.entrySet()) {
                        keys[i] = e.getKey();
                        values[i] = e.getValue();
                        i++;
                    }
                    blendTransition = new LinearKeyframeInterpolator(keys, values);
                } else {
                    blendTransition = new TicksInterpolator(rs.blendTransitionValue);
                }

                states.add(new AnimationState(rs.name, animations.toArray(new Pair[0]), transitions.toArray(new Pair[0]), rs.soundEffects.toArray(new String[0]), onEntry.toArray(new IValue[0]), onExit.toArray(new IValue[0]), blendTransition, rs.blendViaShortestPath));
            }
            result.put(rac.animationName,
                    new AnimationController(
                            rac.initialState.isEmpty() ? "default" : rac.initialState,
                            states.toArray(new AnimationState[0])
                    )
            );
        }
        return result;
    }

    public static ServerModelInfo buildModelInfo(RawYsmModel raw/*, String modelId*/) {
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

    private static String resolveDefaultTexture(RawYsmModel raw) {
        String defaultTexture = raw.properties.defaultTexture;
        if (raw.mainEntity.textures.isEmpty()) {
            return defaultTexture;
        }
        return defaultTexture != null && !defaultTexture.isBlank() && raw.mainEntity.textures.containsKey(defaultTexture)
                ? defaultTexture
                : raw.mainEntity.textures.keySet().iterator().next();
    }

    private static ModelExtraResourcesFile buildExtraResources(RawYsmModel raw) {
        Map<String, AudioTrackData> sounds = new LinkedHashMap<>();
        for (Map.Entry<String, RawYsmModel.RawDataFile> entry : raw.soundFiles.entrySet()) {
            String name = entry.getKey();
            byte[] data = entry.getValue().data;
            AudioTrackData track = parseAudioTrackData(data);
            if (track != null) sounds.put(name, track);
        }

        Map<String, IValue> functions = new LinkedHashMap<>();
        for (Map.Entry<String, RawYsmModel.RawDataFile> entry : raw.functionFiles.entrySet()) {
            String name = entry.getKey();
            byte[] data = entry.getValue().data;
            String molangScript = new String(data, StandardCharsets.UTF_8);
            try {
                functions.put(name, GeckoLibCache.getMolangParser().parseExpression(molangScript, true));
            } catch (Exception e) {
            }
        }

        Map<String, Map<String, String>> translations = new LinkedHashMap<>();
        for (Map.Entry<String, RawYsmModel.RawLanguageFile> entry : raw.languageFiles.entrySet()) {
            translations.put(entry.getKey(), entry.getValue().data);
        }

        return new ModelExtraResourcesFile(sounds, functions, translations);
    }

    private static AudioTrackData parseAudioTrackData(byte[] oggData) {
        if (oggData == null || oggData.length < 8) return null;
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(oggData);
            OggFile oggFile = new OggFile(bais);
            String header = new String(oggData, 0, Math.min(oggData.length, 100), StandardCharsets.US_ASCII);
            boolean isOpus = header.contains("OpusHead");

            AudioCodec codec = isOpus ? AudioCodec.OPUS : AudioCodec.VORBIS;
            int sampleRate;
            if (isOpus) {
                OpusFile opus = new OpusFile(oggFile);
                sampleRate = (int) opus.getInfo().getRate();
            } else {
                VorbisFile vorbis = new VorbisFile(oggFile);
                sampleRate = (int) vorbis.getInfo().getRate();
            }

            OggPacketReader reader = oggFile.getPacketReader();
            long durationSamples = 0;
            var packet = reader.getNextPacket();
            while (packet != null) {
                long granule = packet.getGranulePosition();
                if (granule > 0) durationSamples = granule;
                packet = reader.getNextPacket();
            }

            return new AudioTrackData(ByteBuffer.wrap(oggData), codec.ordinal(), sampleRate, durationSamples);
        } catch (Exception e) {
            return null;
        }
    }

    private static ProjectileModelFiles[] buildExtraItemModels(RawYsmModel raw, GeometryDescription context, boolean mergeMultilineExpr) {
        List<ProjectileModelFiles> list = new ArrayList<>();
        for (Map.Entry<String, RawYsmModel.RawSubEntity> entry : raw.projectiles.entrySet()) {
            RawYsmModel.RawSubEntity sub = entry.getValue();
            ProjectileModelFiles holder = buildSubEntityHolder(sub, context, 1, mergeMultilineExpr);
            list.add(holder);
        }
        return list.toArray(new ProjectileModelFiles[0]);
    }

    private static VehicleModelFiles[] buildExtraEntityModels(RawYsmModel raw, GeometryDescription context, boolean mergeMultilineExpr) {
        List<VehicleModelFiles> list = new ArrayList<>();
        for (Map.Entry<String, RawYsmModel.RawSubEntity> entry : raw.vehicles.entrySet()) {
            RawYsmModel.RawSubEntity sub = entry.getValue();
            VehicleModelFiles wrapper = buildSubEntityWrapper(sub, context, 1, mergeMultilineExpr);
            list.add(wrapper);
        }
        return list.toArray(new VehicleModelFiles[0]);
    }

    private static ProjectileModelFiles buildSubEntityHolder(RawYsmModel.RawSubEntity sub, GeometryDescription context, int textureCount, boolean mergeMultilineExpr) {
        OuterFileTexture texture = null;
        TranslucencyScanner subScanner = null;

        if (!sub.textures.isEmpty()) {
            List<BufferedImage> imgList = new ArrayList<>();
            for(RawYsmModel.RawTexture rt : sub.textures.values()) {
                BufferedImage img = decodeToImage(rt.data, rt.imageFormat, rt.width, rt.height);
                imgList.add(img);
                byte[] processedData = toPng(rt.data, rt.imageFormat, rt.width, rt.height);
                if (texture == null) {
                    texture = new OuterFileTexture(processedData, sub.identifier);
                }
            }
            if (sub.model != null) {
                subScanner = new TranslucencyScanner(imgList.toArray(new BufferedImage[0]), textureCount);
            }
        }

        GeoModel mesh = buildMesh(sub.model, context, textureCount, subScanner, true);

        Map<String, Animation> allAnimations = new LinkedHashMap<>();
        for (Map.Entry<String, RawYsmModel.RawAnimationFile> entry : sub.animationFiles.entrySet()) {
            Map<String, Animation> fileAnims = buildAnimations(entry.getValue(), mergeMultilineExpr);
            allAnimations.putAll(fileAnims);
        }
        AnimationFile combinedAnim = new AnimationFile(allAnimations);

        Map<String, AnimationController> controllerMap = new LinkedHashMap<>();
        if (sub.animationControllerFiles != null) {
            for (RawYsmModel.RawAnimationControllerFile file : sub.animationControllerFiles) {
                if (file.controllers != null && !file.controllers.isEmpty()) {
                    controllerMap.putAll(buildControllers(file.controllers, mergeMultilineExpr));
                }
            }
        }
        AnimationControllerFile controllers = new AnimationControllerFile(controllerMap);

        String[] matchIds = sub.matchIds != null ? sub.matchIds : new String[]{sub.identifier};
        return new ProjectileModelFiles(matchIds, mesh, combinedAnim, controllers, texture);
    }

    private static VehicleModelFiles buildSubEntityWrapper(RawYsmModel.RawSubEntity sub, GeometryDescription context, int textureCount, boolean mergeMultilineExpr) {
        OuterFileTexture texture = null;
        TranslucencyScanner subScanner = null;

        if (!sub.textures.isEmpty()) {
            List<BufferedImage> imgList = new ArrayList<>();
            for(RawYsmModel.RawTexture rt : sub.textures.values()) {
                BufferedImage img = decodeToImage(rt.data, rt.imageFormat, rt.width, rt.height);
                imgList.add(img);
                byte[] processedData = toPng(rt.data, rt.imageFormat, rt.width, rt.height);
                if (texture == null) {
                    texture = new OuterFileTexture(processedData, sub.identifier);
                }
            }
            if (sub.model != null) {
                subScanner = new TranslucencyScanner(imgList.toArray(new BufferedImage[0]), textureCount);
            }
        }

        GeoModel mesh = buildMesh(sub.model, context, textureCount, subScanner, true);

        Map<String, Animation> allAnimations = new LinkedHashMap<>();
        for (RawYsmModel.RawAnimationFile animFile : sub.animationFiles.values()) {
            Map<String, Animation> fileAnims = buildAnimations(animFile, mergeMultilineExpr);
            allAnimations.putAll(fileAnims);
        }
        AnimationFile combinedAnim = new AnimationFile(allAnimations);

        Map<String, AnimationController> controllerMap = new LinkedHashMap<>();
        if (sub.animationControllerFiles != null) {
            for (RawYsmModel.RawAnimationControllerFile file : sub.animationControllerFiles) {
                if (file.controllers != null && !file.controllers.isEmpty()) {
                    controllerMap.putAll(buildControllers(file.controllers, mergeMultilineExpr));
                }
            }
        }
        AnimationControllerFile controllers = new AnimationControllerFile(controllerMap);

        String[] matchIds = sub.matchIds != null ? sub.matchIds : new String[]{sub.identifier};
        return new VehicleModelFiles(matchIds, mesh, combinedAnim, controllers, texture);
    }

    private static Map<String, OuterFileTexture> buildExtraTextures(RawYsmModel raw) {
        Map<String, OuterFileTexture> result = new LinkedHashMap<>();
        for (RawYsmModel.RawImage img : raw.properties.backgroundImages) {
            if (img.name != null && !img.name.isEmpty()) {
                byte[] processedData = toPng(img.data, img.format, img.width, img.height);
                result.put(img.name, new OuterFileTexture(processedData, img.name));
            }
        }
        return result;
    }

    public static List<IValue> parse(List<String> array, boolean mergeMultilineExpr) {
        List<IValue> values = new ArrayList<>();

        if (!mergeMultilineExpr) {
            for (String expr : array) values.add(parse(expr));
            return values;
        }

        try {
            StringBuilder parserText = new StringBuilder();

            for (int i = 0; i < array.size(); i++) {
                parserText.append(array.get(i));
                if (i < array.size() - 1) {
                    parserText.append("\n");
                }
            }

            values.add(parse(parserText.toString()));
        } catch (Throwable ex) {
            values.add(FloatValue.ZERO);
        }
        return values;
    }

    public static IValue parse(String str) {
        try {
            return GeckoLibCache.getMolangParser().parseExpression(str, false);
        } catch (Throwable ex) {
            return FloatValue.ZERO;
        }
    }

    private static String[] buildPath(String targetBone, Map<String, String> parentMap) {
        String resolvedTarget = findBoneName(targetBone, parentMap);
        if (resolvedTarget == null) {
            return new String[0];
        }
        List<String> path = new ArrayList<>();
        String current = resolvedTarget;
        while (current != null && !current.isEmpty()) {
            path.add(current);
            current = parentMap.get(current);
        }
        Collections.reverse(path);
        return path.toArray(new String[0]);
    }

    private static String[] buildPathFirst(Map<String, String> parentMap, String... targetBones) {
        for (String targetBone : targetBones) {
            String[] path = buildPath(targetBone, parentMap);
            if (path.length > 0) {
                return path;
            }
        }
        return new String[0];
    }

    private static String[] buildSwordPath(Map<String, String> parentMap, String baseName) {
        String[] path = buildPath(baseName, parentMap);
        if (path.length > 0) {
            return path;
        }
        String normalizedBase = normalizeBoneName(baseName);
        for (String boneName : parentMap.keySet()) {
            String normalizedName = normalizeBoneName(boneName);
            if (normalizedName.startsWith(normalizedBase) && normalizedName.substring(normalizedBase.length()).chars().allMatch(Character::isDigit)) {
                return buildPath(boneName, parentMap);
            }
        }
        return new String[0];
    }

    private static String findBoneName(String targetBone, Map<String, String> parentMap) {
        if (parentMap.containsKey(targetBone)) {
            return targetBone;
        }
        String normalizedTarget = normalizeBoneName(targetBone);
        for (String boneName : parentMap.keySet()) {
            if (normalizeBoneName(boneName).equals(normalizedTarget)) {
                return boneName;
            }
        }
        return null;
    }

    private static String normalizeBoneName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = Character.toLowerCase(name.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String[][] buildBoneNameArrays(Map<String, String> parentMap) {
        return buildBoneNameArrays(parentMap, SpecialHandLocatorProfile.NONE);
    }

    private static String[][] buildBoneNameArrays(Map<String, String> parentMap, SpecialHandLocatorProfile specialHandLocatorProfile) {
        String[][] arrays = new String[37][];

        // 模型骨骼大全
        String[] targetLocators = new String[]{
                "LeftHandLocator",
                "RightHandLocator",
                "ElytraLocator",
                "PistolLocator",
                "RifleLocator",
                "LeftWaistLocator",
                "RightWaistLocator",
                "LeftShoulderLocator",
                "RightShoulderLocator",
                "BladeLocator",
                "SheathLocator",
                "Head",
                "BackpackLocator",
                "LeftHandLocator2",
                "LeftHandLocator3",
                "LeftHandLocator4",
                "LeftHandLocator5",
                "LeftHandLocator6",
                "LeftHandLocator7",
                "LeftHandLocator8",
                "RightHandLocator2",
                "RightHandLocator3",
                "RightHandLocator4",
                "RightHandLocator5",
                "RightHandLocator6",
                "RightHandLocator7",
                "RightHandLocator8",
                "PassengerLocator",
                "PassengerLocator2",
                "PassengerLocator3",
                "PassengerLocator4",
                "PassengerLocator5",
                "PassengerLocator6",
                "PassengerLocator7",
                "PassengerLocator8",
                "LeftSword",
                "RightSword"
        };

        for (int i = 0; i < arrays.length; i++) {
            if (i == 0) {
                arrays[i] = buildPathFirst(parentMap,
                        "LeftHandLocator", "LeftItem", "LeftHand", "LeftPalm", "LeftWrist",
                        "LeftForeArm", "LeftLowerArm", "LeftArm");
            } else if (i == 1) {
                arrays[i] = buildPathFirst(parentMap,
                        "RightHandLocator", "RightItem", "RightHand", "RightPalm", "RightWrist",
                        "RightForeArm", "RightLowerArm", "RightArm");
            } else if (i == 35) {
                arrays[i] = specialHandLocatorProfile == SpecialHandLocatorProfile.HAND_LOCATOR_HIDDEN_BY_CARRYON
                        ? buildPath("LeftHandLocator", parentMap)
                        : buildSwordPath(parentMap, "LeftSword");
            } else if (i == 36) {
                arrays[i] = specialHandLocatorProfile == SpecialHandLocatorProfile.HAND_LOCATOR_HIDDEN_BY_CARRYON
                        ? buildPath("RightHandLocator", parentMap)
                        : buildSwordPath(parentMap, "RightSword");
            } else if (targetLocators[i] != null && !targetLocators[i].isEmpty()) {
                arrays[i] = buildPath(targetLocators[i], parentMap);
            } else {
                arrays[i] = new String[0];
            }
        }

        return arrays;
    }

    public static GeoModel buildMesh(GeoBone[] bones, Map<String, String> parentMap, GeometryDescription context, boolean[] translucencyArray) {
        return buildMesh(bones, parentMap, context, translucencyArray, SpecialHandLocatorProfile.NONE);
    }

    public static GeoModel buildMesh(GeoBone[] bones, Map<String, String> parentMap, GeometryDescription context, boolean[] translucencyArray, SpecialHandLocatorProfile specialHandLocatorProfile) {
        String[][] boneNameArrays = buildBoneNameArrays(parentMap, specialHandLocatorProfile);
        boolean[] flags = new boolean[]{parentMap.containsKey("LeftArm"), parentMap.containsKey("RightArm"), parentMap.containsKey("Background")};
        return new GeoModel(bones, boneNameArrays, flags, context, translucencyArray);
    }

    public static OrderedStringMap<String, OuterFileTexture> buildTextureMap(Map<String, OuterFileTexture> textures) {
        if (textures.isEmpty()) {
            return new OrderedStringMap<>(new String[0], new OuterFileTexture[0]);
        }
        String[] keys = textures.keySet().toArray(new String[0]);
        OuterFileTexture[] values = textures.values().toArray(new OuterFileTexture[0]);
        return new OrderedStringMap<>(keys, values);
    }

    public static GeometryDescription buildContext(RawYsmModel.RawGeometry model) {
        return new GeometryDescription(
                model.identifier,
                model.textureWidth, // default texture width ratio
                model.textureHeight, // default texture height ratio
                model.visibleBoundsWidth, // offset X
                model.visibleBoundsHeight, // offset Y
                model.visibleBoundsOffset == null
                        ? new double[]{0, 1.5, 0}
                        : IntStream.range(0, model.visibleBoundsOffset.length)
                                .mapToDouble(i -> model.visibleBoundsOffset[i])
                                .toArray()
        );
    }

    private static boolean isNegativeSizedFace(RawYsmModel.RawFace f) {
        float[] p0 = f.positions[0];
        float[] p1 = f.positions[1];
        float[] p2 = f.positions[2];

        float ax = p1[0] - p0[0];
        float ay = p1[1] - p0[1];
        float az = p1[2] - p0[2];

        float bx = p2[0] - p0[0];
        float by = p2[1] - p0[1];
        float bz = p2[2] - p0[2];

        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;

        float len2 = nx * nx + ny * ny + nz * nz;
        if (len2 <= 1e-10f) {
            float[] p3 = f.positions[3];

            bx = p3[0] - p0[0];
            by = p3[1] - p0[1];
            bz = p3[2] - p0[2];

            nx = ay * bz - az * by;
            ny = az * bx - ax * bz;
            nz = ax * by - ay * bx;

            len2 = nx * nx + ny * ny + nz * nz;
            if (len2 <= 1e-10f) {
                return false;
            }
        }

        float dot = nx * f.normal[0] + ny * f.normal[1] + nz * f.normal[2];
        return dot < -1e-5f;
    }
}
