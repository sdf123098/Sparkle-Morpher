package com.micaftic.morpher.client.animation.condition;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.boat.AbstractChestBoat;

public class ConditionVehicle {

    private static final String EMPTY = "";

    private static final Identifier LEGACY_BOAT_ID = Identifier.fromNamespaceAndPath("minecraft", "boat");

    private static final Identifier LEGACY_CHEST_BOAT_ID = Identifier.fromNamespaceAndPath("minecraft", "chest_boat");

    private final ObjectOpenHashSet<Identifier> idTest = new ObjectOpenHashSet<>();

    private final ReferenceArrayList<TagKey<EntityType<?>>> tagTest = new ReferenceArrayList<>();

    private final String idPre;
    private final String tagPre;

    public ConditionVehicle() {
        this.idPre = "vehicle$";
        this.tagPre = "vehicle#";
    }

    public void addTest(String name) {
        int preSize = this.idPre.length();
        if (name.length() <= preSize) {
            return;
        }
        String strSubstring = name.substring(preSize);
        Identifier id = ConditionResourceUtil.parseIdentifier(strSubstring);
        if (id == null) {
            return;
        }
        if (name.startsWith(this.idPre)) {
            this.idTest.add(id);
        }
        if (!name.startsWith(this.tagPre)) {
            return;
        }
        this.tagTest.add(TagKey.create(Registries.ENTITY_TYPE, id));
    }

    public String doTest(LivingEntity entity) {
        Entity vehicle = entity.getVehicle();
        if (vehicle == null || !vehicle.isAlive()) {
            return EMPTY;
        }
        String result = doIdTest(vehicle);
        if (result.isEmpty()) {
            return doTagTest(vehicle);
        }
        return result;
    }

    private String doIdTest(Entity entity) {
        Identifier key;
        if (!this.idTest.isEmpty()) {
            key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (key != null && this.idTest.contains(key)) {
                return this.idPre + key;
            }
            Identifier legacyBoatKey = getLegacyBoatKey(entity);
            if (legacyBoatKey != null && this.idTest.contains(legacyBoatKey)) {
                return this.idPre + legacyBoatKey;
            }
            if (entity instanceof AbstractChestBoat && this.idTest.contains(LEGACY_BOAT_ID)) {
                return this.idPre + LEGACY_BOAT_ID;
            }
        }
        return EMPTY;
    }

    private static Identifier getLegacyBoatKey(Entity entity) {
        if (!(entity instanceof AbstractBoat)) {
            return null;
        }
        return entity instanceof AbstractChestBoat ? LEGACY_CHEST_BOAT_ID : LEGACY_BOAT_ID;
    }

    private String doTagTest(Entity entity) {
        if (this.tagTest.isEmpty()) {
            return EMPTY;
        }
        return this.tagTest.stream().filter(tagKey -> entity.getType().builtInRegistryHolder().is(tagKey)).findFirst().map(tagKey2 -> this.tagPre + tagKey2.location()).orElse(EMPTY);
    }
}
