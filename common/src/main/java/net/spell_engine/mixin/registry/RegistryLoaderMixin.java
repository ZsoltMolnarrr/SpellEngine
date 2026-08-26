package net.spell_engine.mixin.registry;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Decoder;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.WritableRegistry;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import net.spell_engine.api.spell.registry.SpellRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RegistryDataLoader.class)
public class RegistryLoaderMixin {
    @WrapOperation(
            method = "loadContentsFromNetwork(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceProvider;Lnet/minecraft/resources/RegistryOps$RegistryInfoLookup;Lnet/minecraft/core/WritableRegistry;Lcom/mojang/serialization/Decoder;Ljava/util/Map;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/resources/RegistryDataLoader;loadElementFromResource(Lnet/minecraft/core/WritableRegistry;Lcom/mojang/serialization/Decoder;Lnet/minecraft/resources/RegistryOps;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/server/packs/resources/Resource;Lnet/minecraft/core/RegistrationInfo;)V")
    )
    private static <E> void loadFromNetwork_Wrapped_ParseAndAdd(
            WritableRegistry<E> registry, Decoder<E> decoder, RegistryOps<JsonElement> ops, ResourceKey<E> registryKey, Resource resource, RegistrationInfo entryInfo, Operation<Void> original) {
        // Parsing spell from local resource.
        // This is a vanilla optimization, only sending what is needed.
        // But Fabric API doesn't put the correct decoder here for some reason.
        if (registry.key().equals(SpellRegistry.KEY)) {
            original.call(registry, SpellRegistry.LOCAL_CODEC, ops, registryKey, resource, entryInfo);
        } else {
            original.call(registry, decoder, ops, registryKey, resource, entryInfo);
        }
    }
}
