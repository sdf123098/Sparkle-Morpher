package com.micaftic.morpher.client.core;

import dev.spm.core.scene.MolangValue;
import java.util.*;
import java.util.function.IntFunction;

/** Fresh Host-only query adapter; behavior pinned to allowed d253 QueryBinding and query functions. */
public final class CoreQueryFunctions {
    private CoreQueryFunctions() { }
    public enum Slot { CHEST, FEET, HEAD, LEGS, MAINHAND, OFFHAND }
    public record Equipment(String id, boolean empty, int maximumDamage, int damage, Set<String> tags) {
        public Equipment { Objects.requireNonNull(id); tags=Set.copyOf(tags); }
    }
    public record BlockPosition(int x,int y,int z) { }
    /** Implementations stay in Host; returned collections and equipment must be frozen snapshots. */
    public interface Source {
        Set<String> biomeTags();
        Equipment equipment(Slot slot);
        Set<String> blockTags(BlockPosition position);
        String blockId(BlockPosition position);
        double entityX(); double entityY(); double entityZ();
        double cameraPitch(); double cameraYaw();
    }
    private static final Set<String> NAMES=Set.of("query.biome_has_all_tags","query.biome_has_any_tag",
            "query.relative_block_has_all_tags","query.relative_block_has_any_tag","query.is_item_name_any",
            "query.equipped_item_all_tags","query.equipped_item_any_tag","query.max_durability",
            "query.remaining_durability","query.rotation_to_camera","ysm.relative_block_name","host.query.equipment_empty",
            "query.get_equipped_item_name");
    public static boolean supports(String name) { return NAMES.contains(name); }
    public static MolangValue resolve(String name,List<MolangValue> arguments,Source source) {
        return resolveLazy(name,arguments.size(),arguments::get,source);
    }
    /** Exposes reference demand order. An already-eager Core Call cannot recover skipped argument effects. */
    public static MolangValue resolveLazy(String name,int count,IntFunction<MolangValue> argument,Source source) {
        Objects.requireNonNull(argument); Objects.requireNonNull(source);
        if (!supports(name)) throw new IllegalArgumentException("Unknown query function: "+name);
        if(name.equals("query.get_equipped_item_name")) return legacyEquippedName(count,argument,source);
        boolean equipmentProbe=name.equals("host.query.equipment_empty");
        boolean blockName=name.equals("ysm.relative_block_name");
        boolean block=blockName || name.startsWith("query.relative_block_");
        boolean biome=name.startsWith("query.biome_");
        boolean durability=name.equals("query.max_durability") || name.equals("query.remaining_durability");
        boolean camera=name.equals("query.rotation_to_camera");
        int minimum=blockName?3:block?4:biome?1:durability||camera||equipmentProbe?1:2;
        if (count<minimum || ((durability||camera||equipmentProbe) && count!=1) || (blockName && count!=3))
            throw new IllegalArgumentException("Invalid argument count for "+name+": "+count);
        if(camera) {
            int axis=(int)argument.apply(0).number();
            return axis==0?MolangValue.number(-source.cameraPitch()):axis==1?MolangValue.number(180+source.cameraYaw()):MolangValue.missing();
        }
        Set<String> tags;
        int first;
        if(block) {
            // Source reads all three coordinates before checking any bounds.
            double x=argument.apply(0).number(),y=argument.apply(1).number(),z=argument.apply(2).number();
            if(Math.abs(x)>5 || Math.abs(y)>5 || Math.abs(z)>5) return MolangValue.missing();
            var position=new BlockPosition((int)Math.round(source.entityX()+x-0.5),
                    (int)Math.round(source.entityY()+y-0.5),(int)Math.round(source.entityZ()+z-0.5));
            if(blockName) {
                String id=source.blockId(position);
                return id==null?MolangValue.missing():MolangValue.text(id);
            }
            tags=source.blockTags(position); first=3;
        } else if(biome) {
            tags=source.biomeTags(); first=0;
        } else {
            MolangValue slotValue=argument.apply(0);
            Slot slot=parseSlot(slotValue);
            if(slot==null) {
                // d253 durability dereferences null; reject explicitly rather than silently inventing an empty slot.
                if(durability) throw new IllegalArgumentException("Invalid equipment slot for "+name);
                return MolangValue.missing();
            }
            Equipment item=source.equipment(slot);
            // Private Core-to-Host protocol; never registered as a source-visible Molang function.
            if(equipmentProbe) return MolangValue.bool(item.empty());
            if(durability) return MolangValue.number(name.equals("query.max_durability")?item.maximumDamage():item.maximumDamage()-item.damage());
            if(item.empty()) return MolangValue.bool(false);
            tags=name.equals("query.is_item_name_any")?Set.of(item.id()):item.tags(); first=1;
        }
        boolean all=name.endsWith("all_tags");
        for(int i=first;i<count;i++) {
            String id=resourceId(argument.apply(i));
            if(id==null) return MolangValue.missing();
            boolean match=tags.contains(id);
            if(all && !match) return MolangValue.bool(false);
            if(!all && match) return MolangValue.bool(true);
        }
        return MolangValue.bool(all);
    }
    private static Slot parseSlot(MolangValue value) {
        if(!(value instanceof MolangValue.TextValue text)) return null;
        return switch(text.value().toLowerCase(Locale.ENGLISH)) {
            case "chest" -> Slot.CHEST; case "feet" -> Slot.FEET; case "head" -> Slot.HEAD;
            case "legs" -> Slot.LEGS; case "mainhand" -> Slot.MAINHAND; case "offhand" -> Slot.OFFHAND;
            default -> null;
        };
    }
    /** Independent official Bedrock compatibility surface, not a d253 constructor registration. */
    private static MolangValue legacyEquippedName(int count,IntFunction<MolangValue> argument,Source source) {
        if(count<0 || count>2) throw new IllegalArgumentException("get_equipped_item_name expects zero to two arguments");
        Slot slot=Slot.MAINHAND;
        if(count>0) {
            var hand=argument.apply(0);
            if(hand instanceof MolangValue.TextValue text) {
                slot=switch(text.value()) {
                    case "main_hand" -> Slot.MAINHAND; case "off_hand" -> Slot.OFFHAND;
                    default -> throw new IllegalArgumentException("Unknown documented hand slot: "+text.value());
                };
            } else if(hand instanceof MolangValue.NumberValue number && (number.value()==0 || number.value()==1)) {
                slot=number.value()==0?Slot.MAINHAND:Slot.OFFHAND;
            } else throw new IllegalArgumentException("Hand must be 0, 1, 'main_hand' or 'off_hand'");
        }
        if(count>1) {
            var mode=argument.apply(1);
            if(!(mode instanceof MolangValue.NumberValue number))
                throw new IllegalArgumentException("Equipped/rendered item selector must be numeric");
            if(number.value()!=0) throw new UnsupportedOperationException("Rendered-item snapshot is unavailable; equipped item cannot substitute for it");
        }
        var item=source.equipment(slot);
        if(item.empty()) return MolangValue.text("");
        // Host compatibility policy: preserve legacy vanilla short names and namespaced custom IDs.
        String id=item.id();
        return MolangValue.text(id.startsWith("minecraft:")?id.substring(10):id);
    }
    private static String resourceId(MolangValue value) {
        if(!(value instanceof MolangValue.TextValue text)) return null;
        String raw=text.value(); int colon=raw.indexOf(':');
        String namespace=colon<=0?"minecraft":raw.substring(0,colon),path=colon<0?raw:raw.substring(colon+1);
        if(!namespace.matches("[a-z0-9_.-]+") || !path.matches("[a-z0-9/._-]*")) return null;
        return namespace+":"+path;
    }
}
