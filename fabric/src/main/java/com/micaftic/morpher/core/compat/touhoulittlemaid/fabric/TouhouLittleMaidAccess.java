package com.micaftic.morpher.core.compat.touhoulittlemaid.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Soft-link access to Touhou Little Maid Orihime.
 *
 * <p>All optional calls stay behind reflection so Sparkle Morpher can still
 * load when the maid mod is absent and does not depend on its private API.</p>
 */
final class TouhouLittleMaidAccess {
    static final String MOD_ID = "touhou_little_maid";

    private static final ConcurrentMap<MemberKey, Optional<Method>> ZERO_ARG_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<MemberKey, Optional<Field>> FIELDS = new ConcurrentHashMap<>();
    private static volatile Optional<Method> gomokuRankMethod;

    private TouhouLittleMaidAccess() {
    }

    static boolean isLoaded() {
        return FabricLoader.getInstance().isModLoaded(MOD_ID);
    }

    static boolean isMaid(Entity entity) {
        return hasEntityId(entity, "maid");
    }

    static boolean isYsmModel(Entity entity) {
        return isMaid(entity) && booleanValue(invoke(entity, "isYsmModel"), false);
    }

    static String getYsmModelId(Entity entity) {
        return isYsmModel(entity) ? stringValue(invoke(entity, "getYsmModelId"), "") : "";
    }

    static String getYsmModelTexture(Entity entity) {
        return isYsmModel(entity) ? stringValue(invoke(entity, "getYsmModelTexture"), "") : "";
    }

    static boolean isChair(Entity entity) {
        return hasEntityId(entity, "chair");
    }

    static boolean isSit(Entity entity) {
        return hasEntityId(entity, "sit");
    }

    static boolean isBroom(Entity entity) {
        return hasEntityId(entity, "broom");
    }

    static boolean isGohei(Item item) {
        if (!isLoaded() || item == null) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id != null && MOD_ID.equals(id.getNamespace())
                && ("hakurei_gohei".equals(id.getPath()) || "sanae_gohei".equals(id.getPath()));
    }

    static String getChairModelId(Entity entity) {
        return isChair(entity) ? stringValue(invoke(entity, "getModelId"), "") : "";
    }

    static String getJoyType(Entity entity) {
        return isSit(entity) ? stringValue(invoke(entity, "getJoyType"), "") : "";
    }

    static boolean isBegging(Entity entity) {
        return maidBoolean(entity, false, "isBegging");
    }

    static boolean isSitting(Entity entity) {
        return maidBoolean(entity, false, "isMaidInSittingPose");
    }

    static boolean hasBackpack(Entity entity) {
        return maidBoolean(entity, false, "hasBackpack");
    }

    static int getFavorability(Entity entity) {
        return intValue(maidInvoke(entity, "getFavorability"), 0);
    }

    static int getFavorabilityLevel(Entity entity) {
        return intValue(invoke(maidInvoke(entity, "getFavorabilityManager"), "getLevel"), 0);
    }

    static String getTaskId(Entity entity) {
        return stringValue(invoke(maidInvoke(entity, "getTask"), "getUid"), "");
    }

    static String getSchedule(Entity entity) {
        Object schedule = maidInvoke(entity, "getSchedule");
        return schedule == null ? "" : schedule.toString().toLowerCase(Locale.ENGLISH);
    }

    static String getActivity(Entity entity) {
        return stringValue(invoke(maidInvoke(entity, "getScheduleDetail"), "getName"), "");
    }

    static int getGomokuWinCount(Entity entity) {
        return intValue(invoke(gameManager(entity), "getGomokuWinCount"), 0);
    }

    static int getGomokuRank(Entity entity) {
        if (!isMaid(entity)) {
            return 1;
        }
        Method method = getGomokuRankMethod(entity);
        if (method == null) {
            return 1;
        }
        try {
            return intValue(method.invoke(null, entity), 1);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return 1;
        }
    }

    static String getGameState(Entity entity) {
        if (!isMaid(entity) || !isSit(entity.getVehicle())) {
            return "";
        }
        Object manager = gameManager(entity);
        if (booleanValue(invoke(manager, "isWin"), false)) {
            return "win";
        }
        return booleanValue(invoke(manager, "isLost"), false) ? "lost" : "";
    }

    static String getBackpackType(Entity entity) {
        return stringValue(invoke(maidInvoke(entity, "getMaidBackpackType"), "getId"), "");
    }

    static boolean isRenderState(Entity entity, String expected) {
        if (!isMaid(entity)) {
            return false;
        }
        Object state = readField(entity, "renderState");
        if (state == null) {
            state = invoke(entity, "getRenderState");
        }
        return state == null ? "ENTITY".equals(expected) : expected.equalsIgnoreCase(state.toString());
    }

    static String getBackpackShowItem(Entity entity) {
        Object value = maidInvoke(entity, "getBackpackShowItem");
        if (!(value instanceof ItemStack stack) || stack.isEmpty()) {
            return "";
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    static boolean hasFishingHook(Entity entity) {
        return maidBoolean(entity, false, "hasFishingHook");
    }

    static boolean isOwnedBy(Entity entity, net.minecraft.world.entity.player.Player player) {
        if (!isMaid(entity) || player == null) {
            return false;
        }
        Object owner = maidInvoke(entity, "getOwnerUUID");
        return player.getAbilities().instabuild || owner instanceof UUID uuid && uuid.equals(player.getUUID());
    }

    static String getGameAnimation(Entity entity) {
        if (isBegging(entity)) {
            return "beg";
        }
        return switch (getGameState(entity)) {
            case "win" -> "game_win";
            case "lost" -> "game_lost";
            default -> "";
        };
    }

    static String getRenderAnimation(Entity entity) {
        if (isRenderState(entity, "STATUE")) {
            return "statue";
        }
        return isRenderState(entity, "GARAGE_KIT") ? "garage_kit" : "";
    }

    private static Object gameManager(Entity entity) {
        return maidInvoke(entity, "getGameManager", "getGameRecordManager");
    }

    private static Object maidInvoke(Entity entity, String... names) {
        return isMaid(entity) ? invoke(entity, names) : null;
    }

    private static boolean maidBoolean(Entity entity, boolean fallback, String... names) {
        return booleanValue(maidInvoke(entity, names), fallback);
    }

    private static boolean hasEntityId(Entity entity, String path) {
        if (!isLoaded() || entity == null) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id != null && MOD_ID.equals(id.getNamespace()) && path.equals(id.getPath());
    }

    private static Object invoke(Object target, String... names) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        for (String name : names) {
            MemberKey key = new MemberKey(type, name);
            Method method = ZERO_ARG_METHODS.computeIfAbsent(key, TouhouLittleMaidAccess::findMethod).orElse(null);
            if (method != null) {
                try {
                    return method.invoke(target);
                } catch (ReflectiveOperationException | LinkageError ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static Optional<Method> findMethod(MemberKey key) {
        for (Method method : key.owner().getMethods()) {
            if (method.getName().equals(key.name()) && method.getParameterCount() == 0) {
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }

    private static Object readField(Object target, String name) {
        if (target == null) {
            return null;
        }
        MemberKey key = new MemberKey(target.getClass(), name);
        Field field = FIELDS.computeIfAbsent(key, TouhouLittleMaidAccess::findField).orElse(null);
        if (field == null) {
            return null;
        }
        try {
            return field.get(target);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Optional<Field> findField(MemberKey key) {
        try {
            return Optional.of(key.owner().getField(key.name()));
        } catch (NoSuchFieldException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    private static Method getGomokuRankMethod(Entity maid) {
        Optional<Method> cached = gomokuRankMethod;
        if (cached == null) {
            synchronized (TouhouLittleMaidAccess.class) {
                cached = gomokuRankMethod;
                if (cached == null) {
                    cached = findGomokuRankMethod(maid);
                    gomokuRankMethod = cached;
                }
            }
        }
        return cached.orElse(null);
    }

    private static Optional<Method> findGomokuRankMethod(Entity maid) {
        try {
            Class<?> type = Class.forName(
                    "com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidGomokuAI",
                    false,
                    maid.getClass().getClassLoader()
            );
            for (Method method : type.getMethods()) {
                if (method.getName().equals("getRank") && Modifier.isStatic(method.getModifiers())
                        && method.getParameterCount() == 1 && method.getParameterTypes()[0].isInstance(maid)) {
                    return Optional.of(method);
                }
            }
        } catch (ClassNotFoundException | LinkageError ignored) {
        }
        return Optional.empty();
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        return value instanceof Boolean bool ? bool : fallback;
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static String stringValue(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private record MemberKey(Class<?> owner, String name) {
    }
}
