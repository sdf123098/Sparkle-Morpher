package com.micaftic.morpher.client.animation.molang.functions.ctrl;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.client.input.InputStateKey;
import com.micaftic.morpher.geckolib3.util.MolangUtils;
import com.micaftic.morpher.geckolib3.core.molang.funciton.entity.LivingEntityFunction;
import com.micaftic.morpher.client.animation.condition.InnerClassify;
import com.micaftic.morpher.molang.runtime.ExecutionContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;

public class HandRenderFunction extends LivingEntityFunction {

    private static final String PREFIX_ITEM_ID = "$";

    private static final String PREFIX_ITEM_TAG = "#";

    private static final String TYPE_PREFIX = ":";

    private static final String EMPTY_ITEM = "empty";

    private static final int RESULT_FALSE = 0;

    private static final int RESULT_TRUE = 1;

    private final HandItemPredicate handItemPredicate;

    private final String debugName;

    private static long lastSwingDebugTick = -1L;

    private interface HandItemPredicate {
        boolean test(LivingEntity livingEntity, InteractionHand interactionHand);
    }

    private HandRenderFunction(String debugName, HandItemPredicate predicate) {
        this.debugName = debugName;
        this.handItemPredicate = predicate;
    }

    public static HandRenderFunction createAlways() {
        return new HandRenderFunction("hold", (entity, interactionHand) -> true);
    }

    public static HandRenderFunction createWhenSwinging() {
        return new HandRenderFunction("swing", InputStateKey::isSwinging);
    }

    public static HandRenderFunction createWhenUsing() {
        return new HandRenderFunction("use", InputStateKey::isUsingItem);
    }

    @Override
    public Object eval(ExecutionContext<IContext<LivingEntity>> context, ArgumentCollection arguments) {
        EquipmentSlot slotType = MolangUtils.parseSlotType(context.entity(), arguments.getAsString(context, 0));
        if (slotType == null || slotType.isArmor()) {
            return 0;
        }
        String id = arguments.getAsString(context, 1);
        LivingEntity entity = context.entity().entity();
        if (StringUtils.isBlank(id)) {
            return 0;
        }
        ItemStack itemBySlot = entity.getItemBySlot(slotType);
        InteractionHand hand = slotType == EquipmentSlot.OFFHAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        boolean handMatches = this.handItemPredicate.test(entity, hand) || matchesLocalModelPulse(context.entity(), hand);
        if (!handMatches) {
            debugSwing(entity, hand, id, itemBySlot, "hand", 0);
            return 0;
        }
        if (itemBySlot.isEmpty() && id.equals("empty")) {
            debugSwing(entity, hand, id, itemBySlot, "empty", 1);
            return 1;
        }
        String strSubstring = id.substring(1);
        if (id.startsWith(PREFIX_ITEM_ID)) {
            Identifier key = BuiltInRegistries.ITEM.getKey(itemBySlot.getItem());
            if (key == null) {
                debugSwing(entity, hand, id, itemBySlot, "id-missing", 0);
                return 0;
            }
            int result = strSubstring.equals(key.toString()) ? 1 : 0;
            debugSwing(entity, hand, id, itemBySlot, "id", result);
            return result;
        }
        if (id.startsWith(PREFIX_ITEM_TAG)) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(strSubstring));
            int result = itemBySlot.is(tag) ? 1 : 0;
            debugSwing(entity, hand, id, itemBySlot, "tag", result);
            return result;
        }
        if (id.startsWith(TYPE_PREFIX)) {
            String itemType = InnerClassify.getItemType(itemBySlot);
            String legacyAlias = InnerClassify.getLegacyAlias(itemType);
            if ((!StringUtils.isNotBlank(itemType) || !itemType.equals(strSubstring))
                    && (!StringUtils.isNotBlank(legacyAlias) || !legacyAlias.equals(strSubstring))
                    && !itemBySlot.getUseAnimation().name().toLowerCase(Locale.ENGLISH).equals(strSubstring)) {
                debugSwing(entity, hand, id, itemBySlot, "type:" + itemType + "/" + legacyAlias, 0);
                return 0;
            }
            debugSwing(entity, hand, id, itemBySlot, "type:" + itemType + "/" + legacyAlias, 1);
            return 1;
        }
        debugSwing(entity, hand, id, itemBySlot, "unknown", 0);
        return 0;
    }

    private void debugSwing(LivingEntity entity, InteractionHand hand, String id, ItemStack itemStack, String stage, int result) {
        if (!"swing".equals(this.debugName)
                || !GeneralConfig.safeGet(GeneralConfig.INPUT_STATE_DEBUG_LOG, false)
                || !isLocalPlayer(entity)
                || InputStateKey.getLocalSwingPulseTicks() <= 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        long gameTime = minecraft.level == null ? -1L : minecraft.level.getGameTime();
        if (gameTime == lastSwingDebugTick) {
            return;
        }
        lastSwingDebugTick = gameTime;
        String itemType = InnerClassify.getItemType(itemStack);
        String itemId = itemStack.isEmpty() ? "empty" : String.valueOf(BuiltInRegistries.ITEM.getKey(itemStack.getItem()));
        YesSteveModel.LOGGER.info("[SM-INPUT] ctrl.swing result={} stage={} hand={} id={} item={} type={} vanillaSwinging={} swingTime={} attackProgress={} localPulse={} localAge={}",
                result,
                stage,
                hand,
                id,
                itemId,
                itemType,
                entity.swinging,
                entity.swingTime,
                InputStateKey.getAttackProgress(entity, 0.0f),
                InputStateKey.getLocalSwingPulseTicks(),
                InputStateKey.getLocalSwingPulseAge());
    }

    private boolean matchesLocalModelPulse(IContext<LivingEntity> context, InteractionHand hand) {
        if (!isLocalSwingTarget(context)) {
            return false;
        }
        if ("swing".equals(this.debugName)) {
            return InputStateKey.isLocalSwinging(hand);
        }
        if ("use".equals(this.debugName)) {
            return InputStateKey.isUsingItem(context.entity(), hand);
        }
        return false;
    }

    private static boolean isLocalPlayer(LivingEntity entity) {
        return InputStateKey.isLocalPlayerEntity(entity);
    }

    private static boolean isLocalPlayerModel(IContext<LivingEntity> context) {
        return context.geoInstance() instanceof PlayerCapability cap && cap.isLocalPlayerModel();
    }

    private static boolean isLocalSwingTarget(IContext<LivingEntity> context) {
        return isLocalPlayerModel(context) || context.entity() instanceof Player;
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 2 || size == 3;
    }
}
