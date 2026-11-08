package com.micaftic.morpher.client.core;

import dev.spm.core.scene.MolangBindings;
import dev.spm.core.scene.MolangValue;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;
import java.util.stream.Collectors;

/** Host request builder. One instance belongs to one frozen owner slot and its binding retries. */
public final class MinecraftCoreQueryResolver implements CoreQueryFunctions.Source {
    private final Minecraft client;
    private final LivingEntity entity;
    private final double x,y,z;
    private Set<String> biome;
    private final Map<CoreQueryFunctions.Slot,CoreQueryFunctions.Equipment> equipment=new EnumMap<>(CoreQueryFunctions.Slot.class);
    private record BlockSample(String id,Set<String> tags) { }
    private final Map<CoreQueryFunctions.BlockPosition,BlockSample> blocks=new HashMap<>();
    private Double pitch,yaw;
    private final CoreQueryEquipmentPolicy.Presence cosmeticArmor;
    private final java.util.function.Function<CoreQueryFunctions.Slot,CoreQueryFunctions.Equipment> equipmentProvider;
    public MinecraftCoreQueryResolver(Minecraft client,LivingEntity entity,
            CoreQueryEquipmentPolicy.Presence cosmeticArmor,
            java.util.function.Function<CoreQueryFunctions.Slot,CoreQueryFunctions.Equipment> equipmentProvider) {
        this.client=Objects.requireNonNull(client); this.entity=Objects.requireNonNull(entity);
        this.cosmeticArmor=entity instanceof net.minecraft.world.entity.player.Player ? Objects.requireNonNull(cosmeticArmor) : CoreQueryEquipmentPolicy.Presence.ABSENT;
        this.equipmentProvider=equipmentProvider;
        requireThread(); x=entity.getX(); y=entity.getY(); z=entity.getZ();
    }
    public static boolean supports(String name) { return CoreQueryFunctions.supports(name); }
    public MolangValue resolve(MolangBindings.Call call) {
        requireThread();
        return CoreQueryFunctions.resolve(call.name(),call.arguments(),this);
    }
    private void requireThread() {
        if(!client.isSameThread()) throw new IllegalStateException("Core query sampling requires the Minecraft client thread");
    }
    @Override public Set<String> biomeTags() {
        requireThread();
        if(biome==null) biome=entity.level().getBiome(entity.blockPosition()).tags()
                .map(tag->tag.location().toString()).collect(Collectors.toUnmodifiableSet());
        return biome;
    }
    @Override public CoreQueryFunctions.Equipment equipment(CoreQueryFunctions.Slot slot) {
        requireThread();
        return equipment.computeIfAbsent(slot,key->CoreQueryEquipmentPolicy.select(cosmeticArmor,key,equipmentProvider,raw->{
            EquipmentSlot target=EquipmentSlot.valueOf(raw.name());
            var stack=entity.getItemBySlot(target);
            return new CoreQueryFunctions.Equipment(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                    stack.isEmpty(),stack.getMaxDamage(),stack.getDamageValue(),
                    stack.getTags().map(tag->tag.location().toString()).collect(Collectors.toUnmodifiableSet()));
        }));
    }
    @Override public Set<String> blockTags(CoreQueryFunctions.BlockPosition position) { return block(position).tags(); }
    @Override public String blockId(CoreQueryFunctions.BlockPosition position) { return block(position).id(); }
    private BlockSample block(CoreQueryFunctions.BlockPosition position) {
        requireThread();
        return blocks.computeIfAbsent(position,key->{
            var pos=new BlockPos(key.x(),key.y(),key.z());
            if(!entity.level().hasChunk(pos.getX() >> 4,pos.getZ() >> 4)) throw new IllegalStateException("Requested Core query block is not loaded: "+pos);
            var state=entity.level().getBlockState(pos);
            return new BlockSample(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                    state.getTags().map(tag->tag.location().toString()).collect(Collectors.toUnmodifiableSet()));
        });
    }
    @Override public double entityX() { return x; }
    @Override public double entityY() { return y; }
    @Override public double entityZ() { return z; }
    private void camera() {
        requireThread();
        if(pitch==null) { var camera=client.gameRenderer.getMainCamera(); pitch=(double)camera.getXRot(); yaw=(double)camera.getYRot(); }
    }
    @Override public double cameraPitch() { camera(); return pitch; }
    @Override public double cameraYaw() { camera(); return yaw; }
}
