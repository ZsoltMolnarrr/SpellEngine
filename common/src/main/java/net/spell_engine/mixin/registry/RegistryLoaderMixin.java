package net.spell_engine.mixin.registry;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Decoder;
import net.minecraft.registry.MutableRegistry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryLoader;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.entry.RegistryEntryInfo;
import net.minecraft.resource.Resource;
import net.spell_engine.api.spell.registry.SpellRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RegistryLoader.class)
public class RegistryLoaderMixin {
    @WrapOperation(
            method = "loadFromNetwork(Ljava/util/Map;Lnet/minecraft/resource/ResourceFactory;Lnet/minecraft/registry/RegistryOps$RegistryInfoGetter;Lnet/minecraft/registry/MutableRegistry;Lcom/mojang/serialization/Decoder;Ljava/util/Map;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/registry/RegistryLoader;parseAndAdd(Lnet/minecraft/registry/MutableRegistry;Lcom/mojang/serialization/Decoder;Lnet/minecraft/registry/RegistryOps;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/resource/Resource;Lnet/minecraft/registry/entry/RegistryEntryInfo;)V")
    )
    private static <E> void loadFromNetwork_Wrapped_ParseAndAdd(
            MutableRegistry<E> registry, Decoder<E> decoder, RegistryOps<JsonElement> ops, RegistryKey<E> registryKey, Resource resource, RegistryEntryInfo entryInfo, Operation<Void> original) {
        // Parsing spell from local resource.
        // This is a vanilla optimization, only sending what is needed.
        // But Fabric API doesn't put the correct decoder here for some reason.
        if (registry.getKey().equals(SpellRegistry.KEY)) {
            original.call(registry, SpellRegistry.LOCAL_CODEC, ops, registryKey, resource, entryInfo);
        } else {
            original.call(registry, decoder, ops, registryKey, resource, entryInfo);
        }
    }
}
