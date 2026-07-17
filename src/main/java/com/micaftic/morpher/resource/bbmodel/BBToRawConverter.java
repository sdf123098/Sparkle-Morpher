package com.micaftic.morpher.resource.bbmodel;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.geckolib3.util.Interpolations;
import com.micaftic.morpher.resource.pojo.RawYsmModel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.HexFormat;

/**
 * 鎶?{@link BBModelFile} 杞崲涓?{@link RawYsmModel}锛屽杺缁欑幇鏈夌殑 YSMClientMapper 绠＄嚎銆? *
 * <p>鍏抽敭璁捐锛?/p>
 * <ul>
 *   <li>bone 灞傜骇浠?{@link BBModelFile#outliner} 鏍戦噸寤猴紙涓嶄緷璧栦换浣曢《灞?groups[]锛夈€?/li>
 *   <li>鏃犵埗 group 鐨勫绔?element 鑷姩鏀惧叆鍚嶄负 "default" 鐨勬牴 bone銆?/li>
 *   <li>cube UV 鎸?{@code resolution.width/height} 褰掍竴鍖栧埌 0..1銆?/li>
 *   <li>宓屽叆寮?base64 PNG 绾圭悊锛氫粠 IHDR 瑙ｆ瀽鐪熷疄 width/height锛屼笉渚濊禆 bbmodel 涓殑 width/height 瀛楁銆?/li>
 *   <li>Mesh 鏆傛湭鏀寔锛堜繚鐣欎綅锛岃浆鎹㈡椂璺宠繃骞惰褰?warning锛夈€?/li>
 * </ul>
 */
public class BBToRawConverter {

    /** UV 褰掍竴鍖栨椂鐢ㄧ殑鏈€灏忓垎姣嶏紝閬垮厤闄ら浂銆?*/
    private static final float MIN_RESOLUTION = 1e-6f;
    private static final float IMPORTED_PLAYER_SCALE = 1.0f;
    private static final int BEZIER_BAKE_SAMPLES_PER_SECOND = 24;
    private static final int BEZIER_BAKE_MAX_SEGMENT_SAMPLES = 24;
    private static final String IMPORT_SOURCE_EXTRA = "sparkle_morpher:bbmodel_import";
    private static final int IMPORT_FOOTER_VERSION = 32;
    private static final byte[] IMPORT_CACHE_VERSION = "sparkle_morpher:bbmodel_import:v14".getBytes(StandardCharsets.UTF_8);
    private static final String ELYTRA_LOCATOR = "ElytraLocator";
    private static final String LEFT_HAND_LOCATOR = "LeftHandLocator";
    private static final String RIGHT_HAND_LOCATOR = "RightHandLocator";
    private static final String[] ELYTRA_PARENT_CANDIDATES = {
            "body", "torso", "chest", "upperbody", "vanillabody", "waist"
    };
    private static final String[] LEFT_HAND_PARENT_CANDIDATES = {
            "lefthand", "leftpalm", "leftwrist", "leftforearm", "leftlowerarm", "leftarm"
    };
    private static final String[] RIGHT_HAND_PARENT_CANDIDATES = {
            "righthand", "rightpalm", "rightwrist", "rightforearm", "rightlowerarm", "rightarm"
    };

    private BBToRawConverter() {}

    public static RawYsmModel convert(BBModelFile bbmodel) {
        return convert(bbmodel, null);
    }

    public static String importCacheSha256(byte[] sourceBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(IMPORT_CACHE_VERSION);
            digest.update((byte) 0);
            digest.update(sourceBytes);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public static String importCacheIdentity() {
        return new String(IMPORT_CACHE_VERSION, StandardCharsets.UTF_8) + "\nfooterVersion=" + IMPORT_FOOTER_VERSION;
    }

    /**
     * 杞崲 bbmodel 鍒?RawYsmModel銆?     *
     * @param bbmodel       宸茶В鏋愮殑 bbmodel 鏁版嵁
     * @param sideTextures  鏉ヨ嚜澶栭儴锛堝 Figura zip 鍚岀洰褰曪級鐨?PNG 绾圭悊瑕嗙洊銆?     *                      key 鏄?PNG 鏂囦欢鍚嶏紙灏忓啓锛屼笉鍚洰褰曪級锛寁alue 鏄?PNG 瀛楄妭銆?     *                      闈?null 涓斿尮閰嶆椂浼樺厛浜?bbmodel 鍐呭祵鐨?base64 source銆?     */
    public static RawYsmModel convert(BBModelFile bbmodel, Map<String, byte[]> sideTextures) {
        Objects.requireNonNull(bbmodel, "bbmodel");
        RawYsmModel raw = new RawYsmModel();

        raw.modelId = (bbmodel.model_identifier == null || bbmodel.model_identifier.isEmpty())
                ? bbmodel.name : bbmodel.model_identifier;
        raw.formatVersion = 65535;

        RawYsmModel.RawGeometry mainGeo = new RawYsmModel.RawGeometry();
        mainGeo.modelType = 1; // 1=main
        mainGeo.identifier = raw.modelId == null ? "" : raw.modelId;
        mainGeo.textureWidth = bbmodel.resolution == null ? 64f : Math.max(1f, bbmodel.resolution.width);
        mainGeo.textureHeight = bbmodel.resolution == null ? 64f : Math.max(1f, bbmodel.resolution.height);
        mainGeo.visibleBoundsWidth = 0;
        mainGeo.visibleBoundsHeight = 0;
        mainGeo.visibleBoundsOffset = new float[]{0, 1.5f, 0};
        raw.mainEntity.mainModel = mainGeo;

        // 绱㈠紩 elements 鏂逛究 outliner 寮曠敤鏌ヨ
        Map<String, BBElement> elementsById = new HashMap<>();
        for (BBElement el : bbmodel.elements) {
            if (el.uuid != null && !el.uuid.isEmpty()) {
                elementsById.put(el.uuid, el);
            }
        }

        // textures锛氬繀椤诲厛杞紝cube 闈㈡墠鑳芥纭紩鐢?texture
        convertTextures(bbmodel, raw, sideTextures);

        convertGeometry(bbmodel, raw, elementsById);
        applyImportedPlayerDefaults(raw);
        ensureElytraLocator(raw.mainEntity.mainModel);
        ensureHandLocators(raw.mainEntity.mainModel);

        // 鍔ㄧ敾
        convertAnimations(bbmodel, raw);
        ensureVanillaFallbackAnimations(raw);
        applyImportedRouletteDefaults(raw);

        convertAnimationControllers(bbmodel, raw);

        return raw;
    }

    private static void applyImportedPlayerDefaults(RawYsmModel raw) {
        raw.properties.widthScale = IMPORTED_PLAYER_SCALE;
        raw.properties.heightScale = IMPORTED_PLAYER_SCALE;
        raw.footer.version = IMPORT_FOOTER_VERSION;
        raw.footer.unkInt1 = 1;
        raw.footer.extra = IMPORT_SOURCE_EXTRA;
    }

    // Default action-roulette entries for imported bbmodel/figura players.
    // key = animation name (played by index), value = display label (fallback when the
    // model has no localized "properties.extra_animation.<key>" entry).
    // Kept in sync with the bbmodel emote preset (builtin/bbmodel/animations/extra.animation.json).
    private static final String[][] IMPORTED_ROULETTE_ENTRIES = {
            {"extra0", "Wave"},
            {"extra1", "Sit"},
            {"extra2", "Cheer"},
            {"extra3", "Point"}
    };

    // Give imported models a default emote roulette so their builtin bbmodel emotes are
    // reachable via the roulette / hotkeys. putIfAbsent keeps any model-authored entries.
    private static void applyImportedRouletteDefaults(RawYsmModel raw) {
        if (raw.properties == null || raw.properties.extraAnimations == null) {
            return;
        }
        for (String[] entry : IMPORTED_ROULETTE_ENTRIES) {
            raw.properties.extraAnimations.putIfAbsent(entry[0], entry[1]);
        }
    }

    private static void ensureHandLocators(RawYsmModel.RawGeometry geometry) {
        if (geometry == null || geometry.bones == null || geometry.bones.isEmpty()) {
            return;
        }
        ensureHandLocator(geometry, LEFT_HAND_LOCATOR, LEFT_HAND_PARENT_CANDIDATES);
        ensureHandLocator(geometry, RIGHT_HAND_LOCATOR, RIGHT_HAND_PARENT_CANDIDATES);
    }

    private static void ensureElytraLocator(RawYsmModel.RawGeometry geometry) {
        if (geometry == null || geometry.bones == null || geometry.bones.isEmpty()) {
            return;
        }
        if (findBoneByName(geometry.bones, ELYTRA_LOCATOR) != null) {
            return;
        }
        RawYsmModel.RawBone parent = findPreferredParentBone(geometry.bones, ELYTRA_PARENT_CANDIDATES);
        if (parent == null) {
            return;
        }

        RawYsmModel.RawBone locator = new RawYsmModel.RawBone();
        locator.name = ELYTRA_LOCATOR;
        locator.parentName = parent.name == null ? "" : parent.name;
        locator.pivot = estimateElytraLocatorPivot(parent);
        locator.rotation = new float[]{0, 0, 0};
        geometry.bones.add(locator);
    }

    private static void ensureHandLocator(RawYsmModel.RawGeometry geometry, String locatorName, String[] parentCandidates) {
        if (findBoneByName(geometry.bones, locatorName) != null) {
            return;
        }
        RawYsmModel.RawBone parent = findPreferredParentBone(geometry.bones, parentCandidates);
        if (parent == null) {
            return;
        }

        RawYsmModel.RawBone locator = new RawYsmModel.RawBone();
        locator.name = locatorName;
        locator.parentName = parent.name == null ? "" : parent.name;
        locator.pivot = estimateLocatorPivot(parent);
        locator.rotation = new float[]{0, 0, 0};
        geometry.bones.add(locator);
    }

    private static RawYsmModel.RawBone findBoneByName(List<RawYsmModel.RawBone> bones, String name) {
        for (RawYsmModel.RawBone bone : bones) {
            if (name.equals(bone.name)) {
                return bone;
            }
        }
        return null;
    }

    private static RawYsmModel.RawBone findPreferredParentBone(List<RawYsmModel.RawBone> bones, String[] candidates) {
        for (String candidate : candidates) {
            for (RawYsmModel.RawBone bone : bones) {
                if (candidate.equals(normalizeBoneName(bone.name))) {
                    return bone;
                }
            }
        }

        RawYsmModel.RawBone best = null;
        int bestScore = Integer.MIN_VALUE;
        for (RawYsmModel.RawBone bone : bones) {
            String normalized = normalizeBoneName(bone.name);
            int score = handParentScore(normalized, candidates);
            if (score > bestScore) {
                bestScore = score;
                best = bone;
            }
        }
        return bestScore > 0 ? best : null;
    }

    private static int handParentScore(String normalizedName, String[] candidates) {
        int score = 0;
        for (int i = 0; i < candidates.length; i++) {
            if (normalizedName.contains(candidates[i])) {
                score = Math.max(score, 100 - i * 10);
            }
        }
        if (score == 0) {
            return 0;
        }
        if (normalizedName.contains("locator") || normalizedName.contains("cloth")
                || normalizedName.contains("sleeve") || normalizedName.contains("item")) {
            score -= 50;
        }
        return score;
    }

    private static float[] estimateElytraLocatorPivot(RawYsmModel.RawBone parent) {
        Bounds bounds = Bounds.from(parent);
        float[] pivot = parent.pivot == null ? new float[]{0, 24, 2} : parent.pivot.clone();
        if (pivot.length < 3) {
            pivot = new float[]{0, 24, 2};
        }
        // 鞘翅挂在躯干的“上背/肩颈”处，与原版一致：
        //   X 取躯干水平中心；Y 取躯干顶部（肩线）——原版鞘翅模型自带向下延展的翼面，从肩线垂下才是正确观感；
        //   Z 取躯干背面再略微后移，让翼面贴在背后。
        // 旧实现此处用 pivot[1] - 6，会把挂点压到躯干中部，导致鞘翅整体偏低、像裙子一样垂到腿上。
        if (bounds.valid) {
            return new float[]{
                    (bounds.minX + bounds.maxX) * 0.5f,
                    bounds.maxY,
                    bounds.maxZ + 1.5f
            };
        }
        // 无几何包围盒时退回 body 骨骼 pivot：标准人形 body 的 pivot 位于颈部（上背），
        // 直接使用该高度并略微后移即可，不再向下偏移。
        pivot[2] += 1.5f;
        return pivot;
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

    private static float[] estimateLocatorPivot(RawYsmModel.RawBone parent) {
        Bounds bounds = Bounds.from(parent);
        if (bounds.valid) {
            String normalized = normalizeBoneName(parent.name);
            boolean armBone = normalized.contains("arm") && !normalized.contains("hand")
                    && !normalized.contains("palm") && !normalized.contains("wrist");
            float y = armBone ? bounds.minY : (bounds.minY + bounds.maxY) * 0.5f;
            return new float[]{
                    (bounds.minX + bounds.maxX) * 0.5f,
                    y,
                    (bounds.minZ + bounds.maxZ) * 0.5f
            };
        }
        return parent.pivot == null ? new float[]{0, 0, 0} : parent.pivot.clone();
    }

    private static final class Bounds {
        private float minX = Float.POSITIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float minZ = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;
        private float maxZ = Float.NEGATIVE_INFINITY;
        private boolean valid;

        private static Bounds from(RawYsmModel.RawBone bone) {
            Bounds bounds = new Bounds();
            if (bone == null || bone.cubes == null) {
                return bounds;
            }
            for (RawYsmModel.RawCube cube : bone.cubes) {
                if (cube == null || cube.faces == null) {
                    continue;
                }
                for (RawYsmModel.RawFace face : cube.faces) {
                    if (face == null || face.positions == null) {
                        continue;
                    }
                    for (float[] position : face.positions) {
                        if (position == null || position.length < 3) {
                            continue;
                        }
                        bounds.include(position[0] * 16f, position[1] * 16f, position[2] * 16f);
                    }
                }
            }
            return bounds;
        }

        private void include(float x, float y, float z) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
            valid = true;
        }
    }

    // ============================================================
    // Textures
    // ============================================================

    private static void convertTextures(BBModelFile bbmodel, RawYsmModel raw,
                                        Map<String, byte[]> sideTextures) {
        if (bbmodel.textures == null) return;
        for (BBTexture bbTexture : bbmodel.textures) {
            RawYsmModel.RawTexture rawTexture = new RawYsmModel.RawTexture();
            rawTexture.name = bbTexture.name == null ? "" : bbTexture.name;
            rawTexture.hash = bbTexture.uuid == null ? UUID.randomUUID().toString() : bbTexture.uuid;
            rawTexture.imageFormat = 1; // 1 = PNG
            rawTexture.unknownFlag = 1;

            // 浼樺厛淇′换 bbmodel 鑷甫鐨?width/height
            int w = bbTexture.width;
            int h = bbTexture.height;

            byte[] data = null;

            // 1) 浼樺厛鐢ㄥ鎸傜汗鐞嗭紙sideTextures锛夛紝鎸?name 鎴?relative_path 鍖归厤
            if (sideTextures != null && !sideTextures.isEmpty()) {
                String candidate = pickSideTextureKey(bbTexture, sideTextures);
                if (candidate != null) {
                    data = sideTextures.get(candidate);
                }
            }

            // 2) fallback 鍒?bbmodel 鍐呭祵鐨?base64
            if (data == null && bbTexture.isEmbedded()) {
                data = decodeBase64Texture(bbTexture.source);
            }

            if (data == null || data.length == 0) {
                String textureRef = bbTexture.relative_path == null || bbTexture.relative_path.isBlank() ? bbTexture.name : bbTexture.relative_path;
                throw new IllegalArgumentException("Missing Blockbench texture data: " + textureRef);
            }
            rawTexture.data = data;
            if ((w <= 0 || h <= 0) && data.length >= 24) {
                int[] dim = parsePngDimensions(data);
                if (dim != null) {
                    if (w <= 0) w = dim[0];
                    if (h <= 0) h = dim[1];
                }
            }

            rawTexture.width = Math.max(1, w);
            rawTexture.height = Math.max(1, h);
            raw.mainEntity.textures.put(rawTexture.hash, rawTexture);
        }
    }

    /** 浠?sideTextures 涓寫鍑轰笌 bbTexture 鏈€鍖归厤鐨?key銆備紭鍏堢骇锛歯ame 瀹屽叏鍖归厤 > relative_path 鏈鍖归厤銆?*/
    private static String pickSideTextureKey(BBTexture bbTexture, Map<String, byte[]> sideTextures) {
        if (bbTexture.name != null && !bbTexture.name.isEmpty()) {
            String lower = bbTexture.name.toLowerCase(java.util.Locale.ROOT);
            if (sideTextures.containsKey(lower)) return lower;
            if (!lower.endsWith(".png")) {
                String withExt = lower + ".png";
                if (sideTextures.containsKey(withExt)) return withExt;
            }
        }
        if (bbTexture.relative_path != null && !bbTexture.relative_path.isEmpty()) {
            String path = bbTexture.relative_path.toLowerCase(java.util.Locale.ROOT)
                    .replace('\\', '/');
            int slash = path.lastIndexOf('/');
            String last = slash < 0 ? path : path.substring(slash + 1);
            if (sideTextures.containsKey(last)) return last;
        }
        return null;
    }

    /** 浠?PNG 瀛楄妭娴佸ご閮紙IHDR锛夎鍑?width/height銆傚け璐ヨ繑鍥?null銆?*/
    private static int[] parsePngDimensions(byte[] data) {
        // PNG 绛惧悕: 89 50 4E 47 0D 0A 1A 0A锛孖HDR 鍦ㄥ亸绉?16 璧风殑 8 瀛楄妭
        if (data == null || data.length < 24) return null;
        if ((data[0] & 0xFF) != 0x89 || data[1] != 'P' || data[2] != 'N' || data[3] != 'G') return null;
        int width = ((data[16] & 0xFF) << 24) | ((data[17] & 0xFF) << 16)
                  | ((data[18] & 0xFF) << 8)  |  (data[19] & 0xFF);
        int height = ((data[20] & 0xFF) << 24) | ((data[21] & 0xFF) << 16)
                   | ((data[22] & 0xFF) << 8)  |  (data[23] & 0xFF);
        if (width <= 0 || height <= 0 || width > 32768 || height > 32768) return null;
        return new int[]{width, height};
    }

    private static byte[] decodeBase64Texture(String src) {
        if (src == null || src.isEmpty()) return new byte[0];
        String data = src;
        int commaIndex = src.indexOf(',');
        if (commaIndex >= 0) {
            data = src.substring(commaIndex + 1);
        }
        try {
            return Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            try {
                return Base64.getMimeDecoder().decode(data);
            } catch (IllegalArgumentException ignored) {
                return new byte[0];
            }
        }
    }

    // ============================================================
    // Geometry 鈥?浠?outliner 閫掑綊閲嶅缓 bone 灞傜骇
    // ============================================================

    private static void convertGeometry(BBModelFile bbmodel, RawYsmModel raw,
                                        Map<String, BBElement> elementsById) {
        Map<String, BBGroup> groupsByUuid = new HashMap<>();
        if (bbmodel.groups != null) {
            for (BBGroup g : bbmodel.groups) {
                if (g.uuid != null && !g.uuid.isEmpty()) {
                    groupsByUuid.put(g.uuid, g);
                }
            }
        }

        Set<String> referencedElements = new HashSet<>();
        if (bbmodel.outliner != null) {
            for (BBOutlinerNode rootNode : bbmodel.outliner) {
                walkOutliner(rootNode, "", raw.mainEntity.mainModel.bones,
                        elementsById, referencedElements, bbmodel, groupsByUuid);
            }
        }

        List<BBElement> orphan = new ArrayList<>();
        for (BBElement el : bbmodel.elements) {
            if (el.uuid != null && !referencedElements.contains(el.uuid)) {
                orphan.add(el);
            }
        }
        if (!orphan.isEmpty()) {
            RawYsmModel.RawBone defaultBone = new RawYsmModel.RawBone();
            defaultBone.name = "default";
            defaultBone.parentName = "";
            defaultBone.pivot = new float[]{0, 0, 0};
            defaultBone.rotation = new float[]{0, 0, 0};
            for (BBElement el : orphan) {
                if ("cube".equals(el.type)) {
                    defaultBone.cubes.add(convertElementToCube(el, bbmodel));
                } else if ("mesh".equals(el.type)) {
                    defaultBone.cubes.addAll(convertMeshElementToCubes(el, bbmodel));
                } else if (isLocatorElement(el)) {
                    RawYsmModel.RawBone locator = convertLocatorElementToBone(el, defaultBone.name);
                    if (locator != null) {
                        raw.mainEntity.mainModel.bones.add(locator);
                    }
                }
            }
            if (!defaultBone.cubes.isEmpty()) {
                raw.mainEntity.mainModel.bones.add(defaultBone);
            }
        }
    }

    private static void walkOutliner(BBOutlinerNode node, String parentBoneName,
                                     List<RawYsmModel.RawBone> boneList,
                                     Map<String, BBElement> elementsById,
                                     Set<String> referencedElements,
                                     BBModelFile bbmodel,
                                     Map<String, BBGroup> groupsByUuid) {
        if (node == null) {
            return;
        }

        if (node.isElementRef()) {
            referencedElements.add(node.elementUuid);
            return;
        }

        BBGroup g = groupsByUuid == null ? null : groupsByUuid.get(node.uuid);
        String resolvedName = (node.name != null && !node.name.isEmpty()) ? node.name
                : (g != null && g.name != null && !g.name.isEmpty()) ? g.name : node.uuid;
        float[] resolvedOrigin;
        if (isNonZero(node.origin)) {
            resolvedOrigin = node.origin.clone();
        } else if (g != null && isNonZero(g.origin)) {
            resolvedOrigin = g.origin.clone();
        } else {
            resolvedOrigin = new float[3];
        }
        float[] resolvedRotation;
        if (isNonZero(node.rotation)) {
            resolvedRotation = node.rotation.clone();
        } else if (g != null && isNonZero(g.rotation)) {
            resolvedRotation = g.rotation.clone();
        } else {
            resolvedRotation = new float[3];
        }

        RawYsmModel.RawBone bone = new RawYsmModel.RawBone();
        bone.name = resolvedName;
        bone.parentName = parentBoneName == null ? "" : parentBoneName;
        bone.pivot = new float[]{resolvedOrigin[0], resolvedOrigin[1], resolvedOrigin[2]};
        bone.rotation = new float[]{
                (float) -Math.toRadians(resolvedRotation[0]),
                (float) -Math.toRadians(resolvedRotation[1]),
                (float)  Math.toRadians(resolvedRotation[2])
        };

        if (node.children != null) {
            for (BBOutlinerNode child : node.children) {
                if (child.isElementRef()) {
                    BBElement el = elementsById.get(child.elementUuid);
                    if (el != null) {
                        referencedElements.add(el.uuid);
                        if ("cube".equals(el.type)) {
                            bone.cubes.add(convertElementToCube(el, bbmodel));
                        } else if ("mesh".equals(el.type)) {
                            bone.cubes.addAll(convertMeshElementToCubes(el, bbmodel));
                        } else if (isLocatorElement(el)) {
                            RawYsmModel.RawBone locator = convertLocatorElementToBone(el, bone.name);
                            if (locator != null) {
                                boneList.add(locator);
                            }
                        }
                    }
                }
            }
        }

        boneList.add(bone);

        if (node.children != null) {
            for (BBOutlinerNode child : node.children) {
                if (child.isGroup()) {
                    walkOutliner(child, bone.name, boneList, elementsById, referencedElements,
                            bbmodel, groupsByUuid);
                }
            }
        }
    }

    private static boolean isNonZero(float[] v) {
        return v != null && v.length >= 3 && (v[0] != 0f || v[1] != 0f || v[2] != 0f);
    }

    private static boolean isLocatorElement(BBElement element) {
        if (element == null || element.type == null) {
            return false;
        }
        return "locator".equals(element.type) || "null_object".equals(element.type);
    }

    private static RawYsmModel.RawBone convertLocatorElementToBone(BBElement element, String parentName) {
        if (element.name == null || element.name.isEmpty()) {
            return null;
        }
        RawYsmModel.RawBone locator = new RawYsmModel.RawBone();
        locator.name = element.name;
        locator.parentName = parentName == null ? "" : parentName;
        float[] position = element.position != null && element.position.length >= 3 ? element.position : new float[3];
        float[] rotation = element.rotation != null && element.rotation.length >= 3 ? element.rotation : new float[3];
        locator.pivot = new float[]{position[0], position[1], position[2]};
        locator.rotation = new float[]{
                (float) -Math.toRadians(rotation[0]),
                (float) -Math.toRadians(rotation[1]),
                (float)  Math.toRadians(rotation[2])
        };
        return locator;
    }
    private static RawYsmModel.RawCube convertElementToCube(BBElement element, BBModelFile bbmodel) {
        RawYsmModel.RawCube cube = new RawYsmModel.RawCube();

        float[] from = element.from == null ? new float[3] : element.from;
        float[] to = element.to == null ? new float[3] : element.to;
        float inf = element.inflate;
        float[] inflatedFrom = new float[]{from[0] - inf, from[1] - inf, from[2] - inf};
        float[] inflatedTo = new float[]{to[0] + inf, to[1] + inf, to[2] + inf};
        float[] size = new float[]{
                inflatedTo[0] - inflatedFrom[0],
                inflatedTo[1] - inflatedFrom[1],
                inflatedTo[2] - inflatedFrom[2]
        };

        float texW = (bbmodel.resolution == null || bbmodel.resolution.width <= 0) ? 16f : bbmodel.resolution.width;
        float texH = (bbmodel.resolution == null || bbmodel.resolution.height <= 0) ? 16f : bbmodel.resolution.height;

        for (String dir : new String[]{"north", "south", "east", "west", "up", "down"}) {
            BBElement.BBFace bbFace = element.cube_faces == null ? null : element.cube_faces.get(dir);
            cube.faces.add(createFace(inflatedFrom, size, dir, bbFace, texW, texH));
        }
        return cube;
    }

    // ============================================================
    // Mesh 鈫?涓夎褰?鍥涜竟褰㈤潰闆嗗悎
    // ============================================================

    /**
     * 鎶婁竴涓?mesh element 杞崲涓鸿嫢骞插崟闈?RawCube銆?     *
     * <p>YSM 鐨勬覆鏌撶绾垮彧璇嗗埆 4 椤剁偣鐭╁舰闈紙{@link RawYsmModel.RawFace}锛夛紝鎵€浠ユ妸 N 杈瑰舰
     * 閫氳繃 fan triangulation锛堟墖褰笁瑙掑墫鍒嗭級鍒囨垚 N-2 涓笁瑙掑舰锛屾瘡涓笁瑙掑舰鍖呮垚涓€涓嫭绔嬬殑
     * {@code RawCube}锛屽唴鍚?1 涓?{@code RawFace}锛? 椤剁偣涓渶鍚庝竴涓《鐐归噸澶嶇涓変釜椤剁偣锛?     * 褰㈡垚閫€鍖栧洓杈瑰舰锛圷SM 娓叉煋鏃朵粛鎸夊洓杈瑰舰鐢伙紝閲嶅椤剁偣澶勯潰绉负 0锛岃瑙変笂鏄笁瑙掞級銆?/p>
     *
     * <p>鍥涜竟褰㈤潰锛? 椤剁偣锛夊垯鐩存帴褰撲竴涓?RawFace銆?/p>
     */
    private static List<RawYsmModel.RawCube> convertMeshElementToCubes(BBElement element, BBModelFile bbmodel) {
        List<RawYsmModel.RawCube> out = new ArrayList<>();
        if (element.vertices == null || element.vertices.isEmpty()
                || element.faces == null || element.faces.isEmpty()) {
            return out;
        }

        // mesh element 鐨?origin 鏄潰椤剁偣鍧愭爣鐨?灞€閮ㄥ師鐐瑰亸绉?鈥斺€攂bmodel mesh 椤剁偣宸茬粡鏄眬閮ㄥ潗鏍囷紝
        float[] elementOrigin = element.origin == null ? new float[3] : element.origin;
        float[] elementRotation = element.rotation == null ? new float[3] : element.rotation;
        boolean hasNonzeroRotation = elementRotation[0] != 0 || elementRotation[1] != 0 || elementRotation[2] != 0;

        float texW = (bbmodel.resolution == null || bbmodel.resolution.width <= 0) ? 16f : bbmodel.resolution.width;
        float texH = (bbmodel.resolution == null || bbmodel.resolution.height <= 0) ? 16f : bbmodel.resolution.height;

        for (BBElement.BBMeshFace meshFace : element.faces.values()) {
            if (meshFace == null || meshFace.vertices == null || meshFace.vertices.length < 3) {
                continue;
            }

            // 鏀堕泦闈㈤《鐐瑰潗鏍囧拰 UV
            int n = meshFace.vertices.length;
            float[][] positions = new float[n][3];
            float[][] uvs = new float[n][2];
            boolean valid = true;
            for (int i = 0; i < n; i++) {
                String key = meshFace.vertices[i];
                BBElement.BBMeshVertex v = element.vertices.get(key);
                if (v == null || v.position == null || v.position.length < 3) {
                    valid = false;
                    break;
                }
                float[] p = v.position;
                if (hasNonzeroRotation) {
                    p = rotateAroundOrigin(p, elementOrigin, elementRotation);
                }
                positions[i][0] = p[0] / 16f;
                positions[i][1] = p[1] / 16f;
                positions[i][2] = p[2] / 16f;

                if (meshFace.uv != null) {
                    float[] uv = meshFace.uv.get(key);
                    if (uv != null && uv.length >= 2) {
                        uvs[i][0] = uv[0] / Math.max(MIN_RESOLUTION, texW);
                        uvs[i][1] = uv[1] / Math.max(MIN_RESOLUTION, texH);
                    }
                }
            }
            if (!valid) continue;

            float[] normal = computeNormal(positions[0], positions[1], positions[2]);

            if (n == 4) {
                out.add(makeSingleFaceCube(positions, uvs, normal));
            } else {
                for (int i = 1; i + 1 < n; i++) {
                    float[][] triPos = {
                            positions[0], positions[i], positions[i + 1], positions[i + 1]
                    };
                    float[][] triUv = {
                            uvs[0], uvs[i], uvs[i + 1], uvs[i + 1]
                    };
                    out.add(makeSingleFaceCube(triPos, triUv, normal));
                }
            }
        }

        return out;
    }

    /** 鎶?4 涓《鐐?+ 4 涓?UV + 1 涓硶绾垮寘鎴愪竴涓崟闈?RawCube銆?*/
    private static RawYsmModel.RawCube makeSingleFaceCube(float[][] positions, float[][] uvs, float[] normal) {
        RawYsmModel.RawCube cube = new RawYsmModel.RawCube();
        RawYsmModel.RawFace face = new RawYsmModel.RawFace();
        face.positions = new float[][]{
                positions[0].clone(), positions[1].clone(),
                positions[2].clone(), positions[3].clone()
        };
        face.u = new float[]{uvs[0][0], uvs[1][0], uvs[2][0], uvs[3][0]};
        face.v = new float[]{uvs[0][1], uvs[1][1], uvs[2][1], uvs[3][1]};
        face.normal = normal == null ? new float[]{0, 1, 0} : normal;
        cube.faces.add(face);
        return cube;
    }

    /** 璁＄畻涓夎褰㈡硶绾匡紙v1-v0锛壝楋紙v2-v0锛夛紝褰掍竴鍖栥€?*/
    private static float[] computeNormal(float[] v0, float[] v1, float[] v2) {
        float ax = v1[0] - v0[0], ay = v1[1] - v0[1], az = v1[2] - v0[2];
        float bx = v2[0] - v0[0], by = v2[1] - v0[1], bz = v2[2] - v0[2];
        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-6f) return new float[]{0, 1, 0};
        return new float[]{nx / len, ny / len, nz / len};
    }

    /** 鎶婄偣 p 鍥寸粫 origin 鎸夋鎷夎锛堝害锛孹YZ 椤哄簭锛夋棆杞€?*/
    private static float[] rotateAroundOrigin(float[] p, float[] origin, float[] eulerDeg) {
        float x = p[0] - origin[0], y = p[1] - origin[1], z = p[2] - origin[2];
        double rx = Math.toRadians(eulerDeg[0]);
        double ry = Math.toRadians(eulerDeg[1]);
        double rz = Math.toRadians(eulerDeg[2]);
        // X axis
        double cx = Math.cos(rx), sx = Math.sin(rx);
        double y1 = y * cx - z * sx;
        double z1 = y * sx + z * cx;
        // Y axis
        double cy = Math.cos(ry), sy = Math.sin(ry);
        double x2 = x * cy + z1 * sy;
        double z2 = -x * sy + z1 * cy;
        // Z axis
        double cz = Math.cos(rz), sz = Math.sin(rz);
        double x3 = x2 * cz - y1 * sz;
        double y3 = x2 * sz + y1 * cz;
        return new float[]{
                (float) (x3 + origin[0]),
                (float) (y3 + origin[1]),
                (float) (z2 + origin[2])
        };
    }

    // ============================================================

    private static RawYsmModel.RawFace createFace(float[] from, float[] size, String direction,
                                                  BBElement.BBFace bbFace, float texW, float texH) {
        RawYsmModel.RawFace face = new RawYsmModel.RawFace();

        switch (direction) {
            case "north": face.normal = new float[]{0, 0, -1}; break;
            case "south": face.normal = new float[]{0, 0,  1}; break;
            case "east":  face.normal = new float[]{ 1, 0, 0}; break;
            case "west":  face.normal = new float[]{-1, 0, 0}; break;
            case "up":    face.normal = new float[]{0,  1, 0}; break;
            case "down":  face.normal = new float[]{0, -1, 0}; break;
        }

        face.positions = calculateFaceVertices(from, size, direction);

        // UV锛欱lockbench 鐢ㄥ儚绱犲潗鏍?[x1, y1, x2, y2]锛岄渶瑕佹寜 resolution 褰掍竴鍖栧埌 0..1
        float u1, v1, u2, v2;
        if (bbFace != null && bbFace.uv != null && bbFace.uv.length >= 4
                && (bbFace.enabled || bbFace.texture != null)) {
            u1 = bbFace.uv[0] / Math.max(MIN_RESOLUTION, texW);
            v1 = bbFace.uv[1] / Math.max(MIN_RESOLUTION, texH);
            u2 = bbFace.uv[2] / Math.max(MIN_RESOLUTION, texW);
            v2 = bbFace.uv[3] / Math.max(MIN_RESOLUTION, texH);
        } else {
            // 娌℃湁 face 鏁版嵁锛氱敤鏁村紶绾圭悊锛圷SM 娓叉煋鏃朵細澶勭悊琚鐢ㄧ殑闈級
            u1 = 0; v1 = 0; u2 = 1; v2 = 1;
        }

        face.u = new float[]{u1, u2, u2, u1};
        face.v = new float[]{v1, v1, v2, v2};

        if (bbFace != null && bbFace.rotation != 0) {
            rotateUv(face, bbFace.rotation);
        }

        return face;
    }

    private static void rotateUv(RawYsmModel.RawFace face, int degrees) {
        int steps = ((degrees % 360) + 360) / 90 % 4;
        for (int i = 0; i < steps; i++) {
            // 4 椤剁偣寰幆宸︾Щ
            float u0 = face.u[0], v0 = face.v[0];
            face.u[0] = face.u[1]; face.v[0] = face.v[1];
            face.u[1] = face.u[2]; face.v[1] = face.v[2];
            face.u[2] = face.u[3]; face.v[2] = face.v[3];
            face.u[3] = u0;         face.v[3] = v0;
        }
    }

    private static float[][] calculateFaceVertices(float[] from, float[] size, String direction) {
        // bbmodel 鐢ㄥ儚绱狅紝YSM 娓叉煋绔潰椤剁偣鐢ㄦ柟鍧楀崟浣嶏紙=鍍忕礌/16锛屽弬瑙?YSMFolderDeserializer.bakeFaceToRaw
        float x = from[0] / 16f;
        float y = from[1] / 16f;
        float z = from[2] / 16f;
        float w = size[0] / 16f;
        float h = size[1] / 16f;
        float d = size[2] / 16f;

        switch (direction) {
            case "north":
                return new float[][]{
                        {x, y, z}, {x + w, y, z}, {x + w, y + h, z}, {x, y + h, z}
                };
            case "south":
                return new float[][]{
                        {x + w, y, z + d}, {x, y, z + d}, {x, y + h, z + d}, {x + w, y + h, z + d}
                };
            case "east":
                return new float[][]{
                        {x + w, y, z}, {x + w, y, z + d}, {x + w, y + h, z + d}, {x + w, y + h, z}
                };
            case "west":
                return new float[][]{
                        {x, y, z + d}, {x, y, z}, {x, y + h, z}, {x, y + h, z + d}
                };
            case "up":
                return new float[][]{
                        {x, y + h, z}, {x + w, y + h, z}, {x + w, y + h, z + d}, {x, y + h, z + d}
                };
            case "down":
                return new float[][]{
                        {x, y, z + d}, {x + w, y, z + d}, {x + w, y, z}, {x, y, z}
                };
            default:
                return new float[4][3];
        }
    }

    // ============================================================
    // Animations
    // ============================================================

    private static void convertAnimations(BBModelFile bbmodel, RawYsmModel raw) {
        if (bbmodel.animations == null || bbmodel.animations.isEmpty()) {
            return;
        }

        RawYsmModel.RawAnimationFile animFile = new RawYsmModel.RawAnimationFile();
        animFile.animType = 1; // main
        animFile.fileHash = UUID.randomUUID().toString();

        // Blockbench 鐨?animator key 鏄?groupUuid 鈫?鎴戜滑鐢?group 鐨?name 鍋?boneName銆?        // 杩欓噷寤轰竴涓?uuid鈫抧ame 鏄犲皠锛氬厛鎵?outliner锛?鑳? outliner 鐨?name 鍦ㄨ妭鐐逛笂锛夛紝
        Map<String, String> groupUuidToName = new HashMap<>();
        if (bbmodel.outliner != null) {
            for (BBOutlinerNode root : bbmodel.outliner) {
                collectGroupNames(root, groupUuidToName);
            }
        }
        if (bbmodel.groups != null) {
            for (BBGroup g : bbmodel.groups) {
                if (g.uuid == null || g.uuid.isEmpty()) continue;
                if (g.name != null && !g.name.isEmpty()) {
                    // groups[] 鐨?name 浼樺厛绾т綆浜?outliner 鑺傜偣宸叉湁鐨?name
                    groupUuidToName.putIfAbsent(g.uuid, g.name);
                }
            }
        }

        for (BBAnimation bbAnim : bbmodel.animations) {
            RawYsmModel.RawAnimation rawAnim = convertAnimation(bbAnim, groupUuidToName);
            animFile.animations.put(rawAnim.name == null ? bbAnim.uuid : rawAnim.name, rawAnim);
        }

        raw.mainEntity.animationFiles.put("animation-main", animFile);
    }

    private static void ensureVanillaFallbackAnimations(RawYsmModel raw) {
        RawYsmModel.RawGeometry geometry = raw.mainEntity.mainModel;
        if (geometry == null || geometry.bones == null || geometry.bones.isEmpty()) {
            return;
        }

        RawYsmModel.RawAnimationFile animFile = raw.mainEntity.animationFiles.get("animation-main");
        if (animFile == null) {
            animFile = new RawYsmModel.RawAnimationFile();
            animFile.animType = 1;
            animFile.fileHash = UUID.randomUUID().toString();
            raw.mainEntity.animationFiles.put("animation-main", animFile);
        }

        Map<String, String> bones = collectNormalizedBoneNames(geometry);
        animFile.animations.putIfAbsent("idle", createVanillaFallbackAnimation("idle", bones, 0f, 0f));
        animFile.animations.putIfAbsent("walk", createVanillaFallbackAnimation("walk", bones, 25f, 35f));
        animFile.animations.putIfAbsent("run", createVanillaFallbackAnimation("run", bones, 35f, 45f));
    }

    private static Map<String, String> collectNormalizedBoneNames(RawYsmModel.RawGeometry geometry) {
        Map<String, String> out = new HashMap<>();
        for (RawYsmModel.RawBone bone : geometry.bones) {
            String normalized = normalizeBoneName(bone.name);
            if (!normalized.isEmpty()) {
                out.putIfAbsent(normalized, bone.name);
            }
        }
        return out;
    }

    private static RawYsmModel.RawAnimation createVanillaFallbackAnimation(String name, Map<String, String> bones,
                                                                          float armAmplitude, float legAmplitude) {
        RawYsmModel.RawAnimation anim = new RawYsmModel.RawAnimation();
        anim.name = name;
        anim.length = 1.0f;
        anim.loopMode = 1;
        addFallbackBoneAnimation(anim, firstBone(bones, "leftarm", "leftupperarm", "leftshoulder", "leftuparm", "leftbicep", "armleft"), swingExpression(armAmplitude, false));
        addFallbackBoneAnimation(anim, firstBone(bones, "rightarm", "rightupperarm", "rightshoulder", "rightuparm", "rightbicep", "armright"), swingExpression(armAmplitude, true));
        addFallbackBoneAnimation(anim, firstBone(bones, "leftleg", "leftupperleg", "leftthigh", "leftupleg", "legleft"), swingExpression(legAmplitude, true));
        addFallbackBoneAnimation(anim, firstBone(bones, "rightleg", "rightupperleg", "rightthigh", "rightupleg", "legright"), swingExpression(legAmplitude, false));
        return anim;
    }

    private static String firstBone(Map<String, String> bones, String... candidates) {
        for (String candidate : candidates) {
            String bone = bones.get(candidate);
            if (bone != null) {
                return bone;
            }
        }
        return null;
    }

    private static String swingExpression(float amplitude, boolean oppositePhase) {
        if (amplitude == 0f) {
            return "0";
        }
        return "math.cos(query.anim_time * 360" + (oppositePhase ? " + 180" : "") + ") * " + amplitude;
    }

    private static void addFallbackBoneAnimation(RawYsmModel.RawAnimation anim, String boneName, Object xRotation) {
        if (boneName == null) {
            return;
        }
        RawYsmModel.RawBoneAnimation boneAnim = new RawYsmModel.RawBoneAnimation();
        boneAnim.boneName = boneName;
        RawYsmModel.RawKeyframe keyframe = new RawYsmModel.RawKeyframe();
        keyframe.timestamp = 0.0f;
        keyframe.interpolationMode = RawYsmModel.RawKeyframe.INTERPOLATION_LINEAR;
        keyframe.postData = new Object[]{xRotation, 0f, 0f};
        boneAnim.rotation.add(keyframe);
        anim.boneAnimations.add(boneAnim);
    }

    private static void collectGroupNames(BBOutlinerNode node, Map<String, String> out) {
        if (node == null || !node.isGroup()) return;
        if (node.uuid != null && !node.uuid.isEmpty()) {
            out.put(node.uuid, node.name == null || node.name.isEmpty() ? node.uuid : node.name);
        }
        if (node.children != null) {
            for (BBOutlinerNode child : node.children) {
                collectGroupNames(child, out);
            }
        }
    }

    private static RawYsmModel.RawAnimation convertAnimation(BBAnimation bbAnim,
                                                             Map<String, String> groupUuidToName) {
        RawYsmModel.RawAnimation rawAnim = new RawYsmModel.RawAnimation();
        rawAnim.name = bbAnim.name;
        rawAnim.length = bbAnim.length;
        // YSM: 0=once, 1=loop, 3=hold_on_last_frame
        if ("loop".equalsIgnoreCase(bbAnim.loopMode) || bbAnim.loop) {
            rawAnim.loopMode = 1;
        } else if ("hold".equalsIgnoreCase(bbAnim.loopMode)) {
            rawAnim.loopMode = 3;
        } else {
            rawAnim.loopMode = 0;
        }

        convertTimelines(bbAnim, rawAnim);

        if (bbAnim.animators == null) return rawAnim;
        for (Map.Entry<String, BBAnimation.BBAnimator> entry : bbAnim.animators.entrySet()) {
            BBAnimation.BBAnimator animator = entry.getValue();
            if (!"bone".equals(animator.type)) {
                convertEventAnimator(animator, rawAnim);
                continue;
            }

            RawYsmModel.RawBoneAnimation boneAnim = new RawYsmModel.RawBoneAnimation();
            // 浼樺厛鐢?animator 鑷甫 name锛涙病鏈夊氨鍥炴煡 outliner 鐨?groupUuid鈫抧ame 鏄犲皠锛涢兘娌″氨鐢?key
            if (animator.name != null && !animator.name.isEmpty()) {
                boneAnim.boneName = animator.name;
            } else {
                boneAnim.boneName = groupUuidToName.getOrDefault(entry.getKey(), entry.getKey());
            }

            addChannelKeyframes(animator.keyframes, "rotation", boneAnim.rotation);
            addChannelKeyframes(animator.keyframes, "position", boneAnim.position);
            addChannelKeyframes(animator.keyframes, "scale", boneAnim.scale);
            rawAnim.boneAnimations.add(boneAnim);
        }

        return rawAnim;
    }

    private static void addChannelKeyframes(List<BBAnimation.BBKeyframe> keyframes, String channel,
                                            List<RawYsmModel.RawKeyframe> out) {
        if (keyframes == null || keyframes.isEmpty()) {
            return;
        }
        List<BBAnimation.BBKeyframe> channelFrames = new ArrayList<>();
        for (BBAnimation.BBKeyframe keyframe : keyframes) {
            if (channel.equals(keyframe.channel)) {
                channelFrames.add(keyframe);
            }
        }
        channelFrames.sort(Comparator.comparingDouble(kf -> kf.time));
        for (int i = 0; i < channelFrames.size(); i++) {
            BBAnimation.BBKeyframe current = channelFrames.get(i);
            RawYsmModel.RawKeyframe raw = convertKeyframe(current);
            BBAnimation.BBKeyframe next = i + 1 < channelFrames.size() ? channelFrames.get(i + 1) : null;
            List<RawYsmModel.RawKeyframe> baked = bakeBezierSegment(current, next, raw);
            if (baked.isEmpty()) {
                out.add(raw);
            } else {
                out.addAll(baked);
            }
        }
    }

    private static List<RawYsmModel.RawKeyframe> bakeBezierSegment(BBAnimation.BBKeyframe current,
                                                                  BBAnimation.BBKeyframe next,
                                                                  RawYsmModel.RawKeyframe currentRaw) {
        if (!"bezier".equalsIgnoreCase(current.interpolation)) {
            return Collections.emptyList();
        }
        if (next == null || next.time <= current.time) {
            logBezierRuntimeFallback(current, "there is no following keyframe");
            return Collections.emptyList();
        }
        RawYsmModel.RawKeyframe nextRaw = convertKeyframe(next);
        Object[] start = currentRaw.postData;
        Object[] end = nextRaw.hasPreData ? nextRaw.preData : nextRaw.postData;
        if (!allNumeric(start) || !allNumeric(end)
                || current.bezier_right_time.length == 0 || current.bezier_right_value.length == 0) {
            logBezierRuntimeFallback(current, "handles or endpoint values are not numeric");
            return Collections.emptyList();
        }

        float duration = next.time - current.time;
        int samples = Math.max(2, Math.min(BEZIER_BAKE_MAX_SEGMENT_SAMPLES,
                Math.round(duration * BEZIER_BAKE_SAMPLES_PER_SECOND)));
        List<RawYsmModel.RawKeyframe> baked = new ArrayList<>(samples);
        currentRaw.interpolationMode = RawYsmModel.RawKeyframe.INTERPOLATION_LINEAR;
        baked.add(currentRaw);
        for (int i = 1; i < samples; i++) {
            float percent = (float) i / samples;
            RawYsmModel.RawKeyframe sample = new RawYsmModel.RawKeyframe();
            sample.timestamp = current.time + duration * percent;
            sample.interpolationMode = RawYsmModel.RawKeyframe.INTERPOLATION_LINEAR;
            sample.postData = sampleBezier(current, next, start, end, duration, percent);
            baked.add(sample);
        }
        return baked;
    }

    private static Object[] sampleBezier(BBAnimation.BBKeyframe current, BBAnimation.BBKeyframe next,
                                         Object[] start, Object[] end, float duration, float percent) {
        Object[] out = new Object[3];
        for (int axis = 0; axis < 3; axis++) {
            float startValue = ((Number) start[axis]).floatValue();
            float endValue = ((Number) end[axis]).floatValue();
            float rightTime = handleTime(current.bezier_right_time, axis, 0.33f, duration, current.time);
            float leftTime = handleTime(next.bezier_left_time, axis, 0.66f, duration, current.time);
            float rightValue = handleValue(current.bezier_right_value, axis, startValue);
            float leftValue = handleValue(next.bezier_left_value, axis, endValue);
            float curveT = Interpolations.bezierX(rightTime, leftTime, percent);
            out[axis] = Interpolations.bezier(startValue, rightValue, leftValue, endValue, curveT);
        }
        return out;
    }

    private static float handleTime(float[] values, int axis, float fallback, float duration, float startTime) {
        if (values == null || values.length == 0) {
            return fallback;
        }
        float value = values[Math.min(axis, values.length - 1)];
        if (value < 0f || value > 1f) {
            value = (value - startTime) / Math.max(1e-6f, duration);
        }
        return Math.max(0f, Math.min(1f, value));
    }

    private static float handleValue(float[] values, int axis, float fallback) {
        if (values == null || values.length == 0) {
            return fallback;
        }
        return values[Math.min(axis, values.length - 1)];
    }

    private static boolean allNumeric(Object[] values) {
        if (values == null || values.length < 3) {
            return false;
        }
        for (int i = 0; i < 3; i++) {
            if (!(values[i] instanceof Number)) {
                return false;
            }
        }
        return true;
    }

    private static void logBezierRuntimeFallback(BBAnimation.BBKeyframe keyframe, String reason) {
        if (GeneralConfig.safeGet(GeneralConfig.ANIMATION_DEBUG_LOG, false)) {
            YesSteveModel.LOGGER.warn("[SM-ANIM] BBModel Bezier keyframe {} at {}s could not be baked and will use runtime fallback: {}",
                    keyframe.uuid, keyframe.time, reason);
        }
    }

    private static void convertTimelines(BBAnimation bbAnim, RawYsmModel.RawAnimation rawAnim) {
        if (bbAnim.timelines == null) {
            return;
        }
        for (BBAnimation.BBTimeline timeline : bbAnim.timelines) {
            if (timeline == null || timeline.entries == null) {
                continue;
            }
            for (BBAnimation.BBTimelineEntry entry : timeline.entries) {
                addTimelineEvent(rawAnim, entry.time, entry.script);
            }
        }
    }

    private static void convertEventAnimator(BBAnimation.BBAnimator animator, RawYsmModel.RawAnimation rawAnim) {
        if (animator == null || animator.keyframes == null) {
            return;
        }
        String type = animator.type == null ? "" : animator.type.toLowerCase(Locale.ROOT);
        for (BBAnimation.BBKeyframe keyframe : animator.keyframes) {
            String channel = keyframe.channel == null ? "" : keyframe.channel.toLowerCase(Locale.ROOT);
            if ("sound".equals(type) || "sound".equals(channel)) {
                String sound = firstDataPointValue(keyframe, "effect", "sound", "name", "file", "id", "value", "x");
                if (!sound.isEmpty()) {
                    RawYsmModel.RawSoundEffect rawSound = new RawYsmModel.RawSoundEffect();
                    rawSound.timestamp = keyframe.time;
                    rawSound.effectName = sound;
                    rawAnim.soundEffects.add(rawSound);
                }
            } else if ("timeline".equals(channel) || "effect".equals(type) || "particle".equals(type)
                    || "effect".equals(channel) || "particle".equals(channel)) {
                String script = keyframeToTimelineScript(keyframe, type.isEmpty() ? channel : type);
                addTimelineEvent(rawAnim, keyframe.time, script);
                if (("particle".equals(type) || "particle".equals(channel))
                        && GeneralConfig.safeGet(GeneralConfig.ANIMATION_DEBUG_LOG, false)) {
                    YesSteveModel.LOGGER.warn("[SM-ANIM] BBModel particle keyframe {} at {}s was preserved as a timeline event",
                            keyframe.uuid, keyframe.time);
                }
            }
        }
    }

    private static void addTimelineEvent(RawYsmModel.RawAnimation rawAnim, float time, String script) {
        if (script == null || script.trim().isEmpty()) {
            return;
        }
        RawYsmModel.RawTimelineEvent event = new RawYsmModel.RawTimelineEvent();
        event.timestamp = time;
        for (String line : script.split(";")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                event.events.add(trimmed);
            }
        }
        if (!event.events.isEmpty()) {
            rawAnim.timelineEvents.add(event);
        }
    }

    private static String keyframeToTimelineScript(BBAnimation.BBKeyframe keyframe, String prefix) {
        if (keyframe.data_points == null || keyframe.data_points.isEmpty()) {
            return "";
        }
        BBAnimation.BBDataPoint data = keyframe.data_points.get(0);
        String script = firstValue(data, "script", "effect", "particle", "name", "id", "value", "x");
        if (!script.isEmpty()) {
            return script;
        }
        return prefix + " " + data.values;
    }

    private static String firstDataPointValue(BBAnimation.BBKeyframe keyframe, String... keys) {
        if (keyframe.data_points == null || keyframe.data_points.isEmpty()) {
            return "";
        }
        return firstValue(keyframe.data_points.get(0), keys);
    }

    private static String firstValue(BBAnimation.BBDataPoint data, String... keys) {
        if (data == null || data.values == null) {
            return "";
        }
        for (String key : keys) {
            String value = data.values.get(key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static RawYsmModel.RawKeyframe convertKeyframe(BBAnimation.BBKeyframe bbKf) {
        RawYsmModel.RawKeyframe rawKf = new RawYsmModel.RawKeyframe();
        rawKf.timestamp = bbKf.time;
        switch ((bbKf.interpolation == null ? "" : bbKf.interpolation).toLowerCase(Locale.ROOT)) {
            case "step":
                rawKf.interpolationMode = RawYsmModel.RawKeyframe.INTERPOLATION_STEP;
                break;
            case "catmullrom":
                rawKf.interpolationMode = RawYsmModel.RawKeyframe.INTERPOLATION_CATMULLROM;
                break;
            case "bezier":
                rawKf.interpolationMode = RawYsmModel.RawKeyframe.INTERPOLATION_BEZIER;
                rawKf.bezierLinked = bbKf.bezier_linked;
                rawKf.bezierLeftValue = copyOrNull(bbKf.bezier_left_value);
                rawKf.bezierRightValue = copyOrNull(bbKf.bezier_right_value);
                rawKf.bezierLeftTime = copyOrNull(bbKf.bezier_left_time);
                rawKf.bezierRightTime = copyOrNull(bbKf.bezier_right_time);
                if (GeneralConfig.safeGet(GeneralConfig.ANIMATION_DEBUG_LOG, false)) {
                    YesSteveModel.LOGGER.warn("[SM-ANIM] BBModel Bezier keyframe {} at {}s is preserved in RawYSM and will be baked to linear samples when possible", bbKf.uuid, bbKf.time);
                }
                break;
            default:
                rawKf.interpolationMode = RawYsmModel.RawKeyframe.INTERPOLATION_LINEAR;
                break;
        }

        if (bbKf.data_points != null && !bbKf.data_points.isEmpty()) {
            BBAnimation.BBDataPoint dp = bbKf.data_points.get(0);
            rawKf.postData = new Object[]{
                    foldOrKeep(dp.x),
                    foldOrKeep(dp.y),
                    foldOrKeep(dp.z)
            };

            if (bbKf.data_points.size() >= 2) {
                BBAnimation.BBDataPoint pre = bbKf.data_points.get(1);
                rawKf.preData = new Object[]{
                        foldOrKeep(pre.x),
                        foldOrKeep(pre.y),
                        foldOrKeep(pre.z)
                };
                rawKf.hasPreData = true;
            }
        }
        return rawKf;
    }

    private static float[] copyOrNull(float[] values) {
        return values == null || values.length == 0 ? null : Arrays.copyOf(values, values.length);
    }

    /** 绾暟瀛楀瓧绗︿覆鎶樺彔涓?Float锛涘叾瀹冿紙Molang 琛ㄨ揪寮?/ 绌轰覆锛夊師鏍蜂繚鐣欎负 String銆?*/
    private static Object foldOrKeep(String s) {
        if (s == null || s.isEmpty()) return 0f;
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return s; // 璁╀笅娓?Molang 寮曟搸澶勭悊
        }
    }

    // ============================================================
    // Animation Controllers
    // ============================================================

    private static void convertAnimationControllers(BBModelFile bbmodel, RawYsmModel raw) {
        if (bbmodel.animation_controllers == null || bbmodel.animation_controllers.isEmpty()) {
            return;
        }

        RawYsmModel.RawAnimationControllerFile controllerFile = new RawYsmModel.RawAnimationControllerFile();
        controllerFile.name = "animation_controllers";
        controllerFile.hash = UUID.randomUUID().toString();

        for (BBAnimationController bbController : bbmodel.animation_controllers) {
            RawYsmModel.RawAnimationController rawController = new RawYsmModel.RawAnimationController();
            rawController.animationName = bbController.name;
            if (bbController.initial_state != null && !bbController.initial_state.isEmpty()) {
                rawController.initialState = bbController.initial_state;
            } else if (!bbController.stateOrder.isEmpty()) {
                rawController.initialState = bbController.stateOrder.get(0);
            }

            List<String> order = bbController.stateOrder.isEmpty()
                    ? new ArrayList<>(bbController.states.keySet())
                    : bbController.stateOrder;
            for (String stateName : order) {
                BBAnimationController.BBControllerState bbState = bbController.states.get(stateName);
                if (bbState == null) continue;
                rawController.states.add(convertControllerState(bbState));
            }

            controllerFile.controllers.put(bbController.name == null ? bbController.uuid : bbController.name,
                    rawController);
        }

        raw.mainEntity.animationControllerFiles.add(controllerFile);
    }

    private static RawYsmModel.RawControllerState convertControllerState(BBAnimationController.BBControllerState bb) {
        RawYsmModel.RawControllerState rs = new RawYsmModel.RawControllerState();
        rs.name = bb.name;
        for (String anim : bb.animations) {
            // 杩欓噷 key/value 閮界敤寮曠敤鏈韩鈥斺€斿叿浣撶粦瀹氫緷璧栬繍琛屾椂鐨?animation 鍚嶈В鏋?            rs.animations.put(anim, anim);
        }
        for (BBAnimationController.BBControllerTransition tr : bb.transitions) {
            rs.transitions.put(tr.target, tr.condition);
        }
        rs.onEntry.addAll(bb.on_entry);
        rs.onExit.addAll(bb.on_exit);
        return rs;
    }
}
