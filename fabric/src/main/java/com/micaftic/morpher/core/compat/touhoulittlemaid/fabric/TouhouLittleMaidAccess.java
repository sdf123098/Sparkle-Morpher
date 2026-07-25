package com.micaftic.morpher.core.compat.touhoulittlemaid.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
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
 * Soft-link access to Touhou Little Maid.
 *
 * <p>Orihime keeps the upstream package names but does not publish a stable
 * 26.1 development artifact. Keeping all optional calls behind this adapter
 * also prevents class loading failures when the maid mod is absent.</p>
 */
final class TouhouLittleMaidAccess {
    static final String MOD_ID = "touhou_little_maid";

    private static final String MAID = "maid";
    private static final String CHAIR = "chair";
    private static final String SIT = "sit";
    private static final String BROOM = "broom";

    private static final ConcurrentMap<MemberKey, Optional<Method>> ZERO_ARG_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<MemberKey, Optional<Field>> FIELDS = new ConcurrentHashMap<>();

    private static volatile Optional<Method> gomokuRankMethod;

    private TouhouLittleMaidAccess() {
    }

    static boolean isLoaded() {
        return FabricLoader.getInstance().isModLoaded(MOD_ID);
    }

    static boolean isMaid(Entity entity) {
        return hasEntityId(entity, MAID);
    }

    static boolean isChair(Entity entity) {
        return hasEntityId(entity, CHAIR);
    }

    static boolean isSit(Entity entity) {
        return hasEntityId(entity, SIT);
    }

    static boolean isBroom(Entity entity) {
        return hasEntityId(entity, BROOM);
    }

    static boolean isGohei(Item item) {
        if (!isLoaded() || item == null) {
            return false;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null || !MOD_ID.equals(id.getNamespace())) {
            return false;
        }
        return "hakurei_gohei".equals(id.getPath()) || "sanae_gohei".equals(id.getPath());
    }

    static String getChairModelId(Entity entity) {
        if (!isChair(entity)) {
            return "";
        }
        return stringValue(invoke(entity, "getModelId"), "");
    }

    static String getJoyType(Entity entity) {
        if (!isSit(entity)) {
            return "";
        }
        return stringValue(invoke(entity, "getJoyType"), "");
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
        return maidInt(entity, 0, "getFavorability");
    }

    static int getFavorabilityLevel(Entity entity) {
        Object manager = maidInvoke(entity, "getFavorabilityManager");
        return intValue(invoke(manager, "getLevel"), 0);
    }

    static String getTaskId(Entity entity) {
        Object task = maidInvoke(entity, "getTask");
        return stringValue(invoke(task, "getUid"), "");
    }

    static String getSchedule(Entity entity) {
        Object schedule = maidInvoke(entity, "getSchedule");
        return schedule == null ? "" : schedule.toString().toLowerCase(Locale.ENGLISH);
    }

    static String getActivity(Entity entity) {
        Object activity = maidInvoke(entity, "getScheduleDetail");
        return stringValue(invoke(activity, "getName"), "");
    }

    static int getGomokuWinCount(Entity entity) {
        Object manager = maidInvoke(entity, "getGameManager", "getGameRecordManager");
        return intValue(invoke(manager, "getGomokuWinCount"), 0);
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
        Object manager = maidInvoke(entity, "getGameManager", "getGameRecordManager");
        if (booleanValue(invoke(manager, "isWin"), false)) {
            return "win";
        }
        if (booleanValue(invoke(manager, "isLost"), false)) {
            return "lost";
        }
        return "";
    }

    static String getBackpackType(Entity entity) {
        Object backpack = maidInvoke(entity, "getMaidBackpackType");
        return stringValue(invoke(backpack, "getId"), "");
    }

    static boolean isRenderState(Entity entity, String expected) {
        if (!isMaid(entity)) {
            return false;
        }
        Object renderState = readField(entity, "renderState");
        if (renderState == null) {
            renderState = invoke(entity, "getRenderState");
        }
        if (renderState == null) {
            return "ENTITY".equals(expected);
        }
        return expected.equalsIgnoreCase(renderState.toString());
    }

    static String getBackpackShowItem(Entity entity) {
        Object value = maidInvoke(entity, "getBackpackShowItem");
        if (!(value instanceof ItemStack stack) || stack.isEmpty()) {
            return "";
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
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

    private static Object maidInvoke(Entity entity, String... methodNames) {
        return isMaid(entity) ? invoke(entity, methodNames) : null;
    }

    private static boolean maidBoolean(Entity entity, boolean fallback, String... methodNames) {
        return booleanValue(maidInvoke(entity, methodNames), fallback);
    }

    private static int maidInt(Entity entity, int fallback, String... methodNames) {
        return intValue(maidInvoke(entity, methodNames), fallback);
    }

    private static boolean hasEntityId(Entity entity, String path) {
        if (!isLoaded() || entity == null) {
            return false;
        }
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id != null && MOD_ID.equals(id.getNamespace()) && path.equals(id.getPath());
    }

    private static Object invoke(Object target, String... methodNames) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        for (String methodName : methodNames) {
            MemberKey key = new MemberKey(type, methodName);
            Method method = ZERO_ARG_METHODS.computeIfAbsent(key, TouhouLittleMaidAccess::findZeroArgMethod).orElse(null);
            if (method == null) {
                continue;
            }
            try {
                return method.invoke(target);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return null;
            }
        }
        return null;
    }

    private static Optional<Method> findZeroArgMethod(MemberKey key) {
        for (Method method : key.owner().getMethods()) {
            if (method.getName().equals(key.name()) && method.getParameterCount() == 0) {
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        MemberKey key = new MemberKey(target.getClass(), fieldName);
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
                if (method.getName().equals("getRank")
                        && Modifier.isStatic(method.getModifiers())
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isInstance(maid)) {
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
