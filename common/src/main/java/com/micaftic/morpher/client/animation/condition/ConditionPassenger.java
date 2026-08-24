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

public class ConditionPassenger {

    private static final String EMPTY = "";

    private final ObjectOpenHashSet<Identifier> idTest = new ObjectOpenHashSet<>();

    private final ReferenceArrayList<TagKey<EntityType<?>>> tagTest = new ReferenceArrayList<>();

    private final String idPre;
    private final String tagPre;

    public ConditionPassenger() {
        this.idPre = "passenger$";
        this.tagPre = "passenger#";
    }

    public void doTest(String name) {
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
        Entity firstPassenger = entity.getFirstPassenger();
        if (firstPassenger == null || !firstPassenger.isAlive()) {
            return EMPTY;
        }
        String result = doIdTest(firstPassenger);
        if (result.isEmpty()) {
            return doTagTest(firstPassenger);
        }
        return result;
    }

    private String doIdTest(Entity entity) {
        Identifier key;
        if (!this.idTest.isEmpty() && (key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())) != null && this.idTest.contains(key)) {
            return this.idPre + key;
        }
        return EMPTY;
    }

    private String doTagTest(Entity entity) {
        if (this.tagTest.isEmpty()) {
            return EMPTY;
        }
        return this.tagTest.stream().filter(tagKey -> entity.getType().builtInRegistryHolder().is(tagKey)).findFirst().map(tagKey2 -> this.tagPre + tagKey2.location()).orElse(EMPTY);
    }
}
