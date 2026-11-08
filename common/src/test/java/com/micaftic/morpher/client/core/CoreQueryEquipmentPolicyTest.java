package com.micaftic.morpher.client.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class CoreQueryEquipmentPolicyTest {
 @Test void presenceUsesOnlyExplicitLoaderRegistration() {
  var registry=new CoreCompatibilityRegistry<Object>();
  assertThrows(IllegalStateException.class,()->registry.isInstalled("cosmeticarmorreworked"));
  registry.registerPresence(id->id.equals("cosmeticarmorreworked"));
  assertTrue(registry.isInstalled("cosmeticarmorreworked"));
  assertFalse(registry.isInstalled("absent"));
 }
 @Test void installedCosmeticArmorNeverSilentlyFallsBackToVanillaArmor() {
  var vanilla=new CoreQueryFunctions.Equipment("minecraft:iron_helmet",false,100,0,java.util.Set.of());
  var cosmetic=new CoreQueryFunctions.Equipment("minecraft:diamond_helmet",false,200,10,java.util.Set.of());
  assertThrows(IllegalStateException.class,()->CoreQueryEquipmentPolicy.select(CoreQueryEquipmentPolicy.Presence.PRESENT,CoreQueryFunctions.Slot.HEAD,null,s->vanilla));
  assertEquals(cosmetic,CoreQueryEquipmentPolicy.select(CoreQueryEquipmentPolicy.Presence.PRESENT,CoreQueryFunctions.Slot.HEAD,s->cosmetic,s->vanilla));
  assertEquals(vanilla,CoreQueryEquipmentPolicy.select(CoreQueryEquipmentPolicy.Presence.ABSENT,CoreQueryFunctions.Slot.HEAD,null,s->vanilla));
  assertEquals(vanilla,CoreQueryEquipmentPolicy.select(CoreQueryEquipmentPolicy.Presence.PRESENT,CoreQueryFunctions.Slot.MAINHAND,null,s->vanilla));
  assertThrows(IllegalStateException.class,()->CoreQueryEquipmentPolicy.select(CoreQueryEquipmentPolicy.Presence.UNKNOWN,CoreQueryFunctions.Slot.HEAD,null,s->vanilla));
 }
}
