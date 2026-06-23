package com.micaftic.morpher.util;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import java.util.HashSet;
import java.util.Set;

public final class FileTypeUtil {

    public static final String DEFAULT_MODEL_ID = "default";

    public static final String DEFAULT_TEXTURE = "default";

    private static final Set<String> ARCHIVE_EXTENSIONS = Sets.newHashSet(".zip", ".ysm", ".bbmodel");

    public static int parseHexId(String str) {
        return Integer.parseUnsignedInt(str.substring(0, 8), 16);
    }

    public static Pair<String, String> splitFileNameAndParentDir(String filePath) {
        int lastSlashIndex = filePath.lastIndexOf('/');
        if (lastSlashIndex == -1) {
            return Pair.of(filePath, StringPool.EMPTY);
        }
        return Pair.of(filePath.substring(lastSlashIndex + 1), filePath.substring(0, lastSlashIndex + 1));
    }

    public static String getNameWithoutArchiveExtension(String filePath) {
        String fileName;
        int lastSlashIndex = filePath.lastIndexOf('/');

        if (lastSlashIndex == -1) {
            fileName = filePath;
        } else {
            fileName = filePath.substring(lastSlashIndex + 1);
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 1 || !ARCHIVE_EXTENSIONS.contains(fileName.substring(dotIndex).toLowerCase())) {
            return fileName;
        }
        return fileName.substring(0, dotIndex);
    }

    public static String getFinalPathSegment(String path) {
        if (path == null || path.isEmpty()) {
            return StringPool.EMPTY;
        }

        String trimmedPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int lastSlashIndex = trimmedPath.lastIndexOf('/');

        return lastSlashIndex >= 0 ? trimmedPath.substring(lastSlashIndex + 1) : trimmedPath;
    }

    public static Identifier getPackIconLocation(String str) {
        return Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "model_pack_icon/" + str.hashCode());
    }

    /**
     * 瑙ｆ瀽 "match"瀛楁鐨?     *  "match": [
     *     "minecraft:arrow",
     *     "#minecraft:arrows"
     *  ],
     *  甯?鐨勬槸瀹炰綋 Tag
     */
    public static Set<Identifier> resolveEntityTypes(String[] strArr) {
        HashSet<Identifier> hashSet = new HashSet<>();
        for (String str : strArr) {
            if (str.startsWith("#")) {
                Identifier id = Identifier.tryParse(str.substring(1));
                if (id != null) {
                    TagKey<EntityType<?>> tagKey = TagKey.create(Registries.ENTITY_TYPE, id);
                    BuiltInRegistries.ENTITY_TYPE.get(tagKey).ifPresent(holderSet ->
                        holderSet.forEach(holder -> holder.unwrapKey().ifPresent(rk -> hashSet.add(rk.identifier())))
                    );
                }
            } else {
                Identifier id = Identifier.tryParse(str);
                if (id != null) {
                    hashSet.add(id);
                }
            }
        }
        return hashSet;
    }
}
