package com.micaftic.morpher.mixin.client;

import com.micaftic.morpher.core.gpu.VulkanBoneStorage;
import com.mojang.renderpearl.backend.api.SpvModule;
import com.mojang.renderpearl.frontend.shaders.SPIRVModule;
import org.lwjgl.util.spvc.Spvc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

/** RenderPearl reflects SSBOs; adapt only our block to the public uniform carrier. */
@Mixin(value = SPIRVModule.class, remap = false)
public abstract class VulkanStorageReflectionMixin {
    @Shadow private SpvModule.Reflection reflection;

    @Inject(method = "doReflection", at = @At("RETURN"))
    private void sparkleMorpher$reflectStorage(CallbackInfo ci) {
        SpvModule.Reflection original = reflection;
        if (original.descriptors(Spvc.SPVC_RESOURCE_TYPE_STORAGE_BUFFER).stream()
                .noneMatch(d -> VulkanBoneStorage.NAME.equals(d.name()))) return;
        List<SpvModule.Reflection.Descriptor> descriptors = original.descriptors().stream().map(d -> {
            if (!VulkanBoneStorage.NAME.equals(d.name()) || d.resourceType() != Spvc.SPVC_RESOURCE_TYPE_STORAGE_BUFFER) return d;
            return new SpvModule.Reflection.Descriptor() {
                @Override public String name() { return d.name(); }
                @Override public SpvModule.Reflection.Type type() { return d.type(); }
                @Override public int resourceType() { return Spvc.SPVC_RESOURCE_TYPE_UNIFORM_BUFFER; }
                @Override public int descriptorSetIndex() { return d.descriptorSetIndex(); }
                @Override public void descriptorSetIndex(int set) { d.descriptorSetIndex(set); }
                @Override public int binding() { return d.binding(); }
                @Override public void binding(int binding) { d.binding(binding); }
            };
        }).toList();
        reflection = new SpvModule.Reflection() {
            @Override public List<InterfaceVariable> inputs() { return original.inputs(); }
            @Override public List<InterfaceVariable> outputs() { return original.outputs(); }
            @Override public List<Descriptor> descriptors() { return descriptors; }
            @Override public List<Descriptor> descriptors(int type) {
                return descriptors.stream().filter(d -> d.resourceType() == type).toList();
            }
            @Override public List<PushConstant> pushConstants() { return original.pushConstants(); }
        };
    }
}
