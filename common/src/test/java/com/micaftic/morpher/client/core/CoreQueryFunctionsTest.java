package com.micaftic.morpher.client.core;

import dev.spm.core.scene.MolangValue;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class CoreQueryFunctionsTest {
    private static final class Data implements CoreQueryFunctions.Source {
        Set<String> biome=new HashSet<>(Set.of("minecraft:forest"));
        CoreQueryFunctions.Equipment equipment=new CoreQueryFunctions.Equipment("minecraft:bow",false,100,17,Set.of("minecraft:tools"));
        CoreQueryFunctions.BlockPosition requested;
        int itemReads, blockReads;
        CoreQueryFunctions.Slot lastSlot;
        public Set<String> biomeTags() { return Set.copyOf(biome); }
        public CoreQueryFunctions.Equipment equipment(CoreQueryFunctions.Slot slot) { itemReads++; lastSlot=slot; return equipment; }
        public Set<String> blockTags(CoreQueryFunctions.BlockPosition position) { requested=position; blockReads++; return Set.of("minecraft:logs"); }
        public String blockId(CoreQueryFunctions.BlockPosition position) { requested=position; blockReads++; return "minecraft:oak_log"; }
        public double entityX() { return -0.25; } public double entityY() { return 64.75; } public double entityZ() { return 1.5; }
        public double cameraPitch() { return 35; } public double cameraYaw() { return -120; }
    }
    private static MolangValue t(String s) { return MolangValue.text(s); }
    private static MolangValue n(double d) { return MolangValue.number(d); }
    private static MolangValue call(Data data,String name,MolangValue... args) {
        return CoreQueryFunctions.resolve(name,List.of(args),data);
    }
    @Test void tagsNamesSlotsAndDurabilityFollowReference() {
        var data=new Data();
        assertEquals(MolangValue.bool(true),call(data,"query.biome_has_all_tags",t("forest")));
        assertEquals(MolangValue.bool(false),call(data,"query.biome_has_all_tags",t("desert"),t("Bad ID")));
        assertEquals(MolangValue.bool(false),call(data,"query.biome_has_any_tag",t("")));
        assertEquals(MolangValue.bool(false),call(data,"query.biome_has_any_tag",t("minecraft:")));
        assertEquals(MolangValue.bool(true),call(data,"query.biome_has_any_tag",t("forest"),t("Bad ID")));
        assertEquals(MolangValue.missing(),call(data,"query.biome_has_any_tag",t("Bad ID"),t("forest")));
        assertEquals(MolangValue.bool(true),call(data,"query.is_item_name_any",t("MAINHAND"),t("bow")));
        assertEquals(MolangValue.bool(true),call(data,"query.equipped_item_all_tags",t("head"),t("tools")));
        assertEquals(n(100),call(data,"query.max_durability",t("feet")));
        assertEquals(n(83),call(data,"query.remaining_durability",t("offhand")));
        int before=data.itemReads;
        assertEquals(MolangValue.missing(),call(data,"query.is_item_name_any",t("helmet"),t("bow")));
        assertEquals(before,data.itemReads);
        assertThrows(IllegalArgumentException.class,()->call(data,"query.max_durability",t("helmet")));
        assertThrows(IllegalArgumentException.class,()->call(data,"query.biome_has_any_tag"));
        data.equipment=new CoreQueryFunctions.Equipment("minecraft:air",true,0,0,Set.of());
        assertEquals(MolangValue.bool(false),call(data,"query.is_item_name_any",t("head"),t("Bad ID")));
    }
    @Test void relativeCoordinatesAreRoundedAfterAddingEntityPositionAndBoundedBeforeWorldRead() {
        var data=new Data();
        assertEquals(MolangValue.bool(true),call(data,"query.relative_block_has_any_tag",n(-0.75),n(0.5),n(0),t("logs")));
        assertEquals(new CoreQueryFunctions.BlockPosition(-1,65,1),data.requested);
        assertEquals(MolangValue.bool(true),call(data,"query.relative_block_has_all_tags",n(5),n(-5),n(5),t("logs")));
        int reads=data.blockReads;
        assertEquals(MolangValue.missing(),call(data,"query.relative_block_has_all_tags",n(5.0001),n(0),n(0),t("logs")));
        assertEquals(reads,data.blockReads);
    }
    @Test void officialBedrockLegacyNameSupportsTypedHandsAndExplicitEquippedOnlyBoundary() {
        var data=new Data();
        assertEquals(t("bow"),call(data,"query.get_equipped_item_name"));
        assertEquals(CoreQueryFunctions.Slot.MAINHAND,data.lastSlot);
        assertEquals(t("bow"),call(data,"query.get_equipped_item_name",t("main_hand")));
        assertEquals(CoreQueryFunctions.Slot.MAINHAND,data.lastSlot);
        assertEquals(t("bow"),call(data,"query.get_equipped_item_name",n(0)));
        assertEquals(CoreQueryFunctions.Slot.MAINHAND,data.lastSlot);
        assertEquals(t("bow"),call(data,"query.get_equipped_item_name",t("off_hand"),n(0)));
        assertEquals(CoreQueryFunctions.Slot.OFFHAND,data.lastSlot);
        assertEquals(t("bow"),call(data,"query.get_equipped_item_name",n(1)));
        assertEquals(CoreQueryFunctions.Slot.OFFHAND,data.lastSlot);
        int reads=data.itemReads;
        assertThrows(IllegalArgumentException.class,()->call(data,"query.get_equipped_item_name",t("mainhand")));
        assertThrows(IllegalArgumentException.class,()->call(data,"query.get_equipped_item_name",n(0.5)));
        assertThrows(IllegalArgumentException.class,()->call(data,"query.get_equipped_item_name",MolangValue.bool(false)));
        assertThrows(UnsupportedOperationException.class,()->call(data,"query.get_equipped_item_name",n(0),n(1)));
        assertThrows(IllegalArgumentException.class,()->call(data,"query.get_equipped_item_name",n(0),t("0")));
        assertThrows(IllegalArgumentException.class,()->call(data,"query.get_equipped_item_name",n(0),n(0),n(0)));
        assertEquals(reads,data.itemReads);
        data.equipment=new CoreQueryFunctions.Equipment("minecraft:air",true,0,0,Set.of());
        assertEquals(t(""),call(data,"query.get_equipped_item_name",t("main_hand")));
        data.equipment=new CoreQueryFunctions.Equipment("example:wand",false,0,0,Set.of());
        assertEquals(t("example:wand"),call(data,"query.get_equipped_item_name",t("main_hand")));
    }
    @Test void privateEquipmentProbeReadsOnlyTheSlotAndReusesTheEquipmentSource() {
        var data=new Data();
        assertEquals(MolangValue.bool(false),call(data,"host.query.equipment_empty",t("MAINHAND")));
        assertEquals(1,data.itemReads);
        assertEquals(MolangValue.missing(),call(data,"host.query.equipment_empty",t("invalid")));
        assertEquals(1,data.itemReads);
        data.equipment=new CoreQueryFunctions.Equipment("minecraft:air",true,0,0,Set.of());
        assertEquals(MolangValue.bool(true),call(data,"host.query.equipment_empty",t("head")));
        assertThrows(IllegalArgumentException.class,()->call(data,"host.query.equipment_empty"));
        assertThrows(IllegalArgumentException.class,()->call(data,"host.query.equipment_empty",t("head"),t("ignored")));
        var reads=new ArrayList<Integer>();
        assertEquals(MolangValue.bool(true),CoreQueryFunctions.resolveLazy("host.query.equipment_empty",1,
                i->{reads.add(i); return t("head");},data));
        assertEquals(List.of(0),reads);
    }
    @Test void relativeBlockNameUsesTheSameBoundedRoundedCoordinatesAsTags() {
        var data=new Data();
        assertEquals(t("minecraft:oak_log"),call(data,"ysm.relative_block_name",n(-0.75),n(0.5),n(0)));
        assertEquals(new CoreQueryFunctions.BlockPosition(-1,65,1),data.requested);
        int reads=data.blockReads;
        assertEquals(MolangValue.missing(),call(data,"ysm.relative_block_name",n(0),n(-5.01),n(0)));
        assertEquals(reads,data.blockReads);
        assertThrows(IllegalArgumentException.class,()->call(data,"ysm.relative_block_name",n(0),n(0)));
        assertThrows(IllegalArgumentException.class,()->call(data,"ysm.relative_block_name",n(0),n(0),n(0),n(0)));
    }
    @Test void lazyAdapterStopsReadingArgumentsAtReferenceBranchPoints() {
        var data=new Data();
        var read=new ArrayList<Integer>();
        var args=List.of(t("forest"),t("Bad ID"));
        assertEquals(MolangValue.bool(true),CoreQueryFunctions.resolveLazy("query.biome_has_any_tag",2,i->{read.add(i);return args.get(i);},data));
        assertEquals(List.of(0),read);
        read.clear();
        assertEquals(MolangValue.missing(),CoreQueryFunctions.resolveLazy("query.equipped_item_all_tags",3,i->{read.add(i);return i==0?t("invalid"):t("Bad ID");},data));
        assertEquals(List.of(0),read);
    }
    @Test void cameraCoercionAndSnapshotImmutabilityAreExplicit() {
        var data=new Data();
        assertEquals(n(-35),call(data,"query.rotation_to_camera",n(0.9)));
        assertEquals(n(60),call(data,"query.rotation_to_camera",t("not-a-number")));
        assertEquals(MolangValue.missing(),call(data,"query.rotation_to_camera",n(2)));
        var tags=new HashSet<>(Set.of("minecraft:tools"));
        var equipment=new CoreQueryFunctions.Equipment("minecraft:bow",false,100,17,tags);
        tags.clear(); assertEquals(Set.of("minecraft:tools"),equipment.tags());
        assertThrows(UnsupportedOperationException.class,()->equipment.tags().clear());
    }
}
