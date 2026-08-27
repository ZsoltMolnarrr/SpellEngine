package net.spell_engine.mixin.registry;

import com.google.gson.JsonElement;
import com.mojang.serialization.Decoder;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.spell_engine.api.spell.registry.SpellRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/// When a client receives a synced dynamic registry, vanilla only sends the entries the client cannot
/// find in its own resources (`RegistrySynchronization.PackedRegistryEntry#data` is empty for those) and
/// parses the local JSON with the *same* decoder it uses for the network payload
/// (`NetworkRegistryLoadTask#load` → `RegistryLoadTask.PendingRegistration#findAndLoadFromResource(data.elementCodec(), …)`).
/// Both Fabric API (`DynamicRegistries.registerSynced(key, local, network)`) and NeoForge
/// (`DataPackRegistriesHooks#addRegistryCodec`) build that `RegistryData` with the **network** codec,
/// so the local spell JSON would be parsed with `SpellRegistry.NETWORK_CODEC_V2`. Swap in the local codec.
///
/// 26.1.2: `RegistryDataLoader.loadContentsFromNetwork`/`loadElementFromResource` no longer exist; the
/// only caller of `findAndLoadFromResource` is `NetworkRegistryLoadTask`.
@Mixin(targets = "net.minecraft.resources.RegistryLoadTask$PendingRegistration")
public class RegistryLoaderMixin {
    @ModifyVariable(
            method = "findAndLoadFromResource(Lcom/mojang/serialization/Decoder;Lnet/minecraft/resources/RegistryOps;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/FileToIdConverter;Lnet/minecraft/server/packs/resources/ResourceProvider;)Lcom/mojang/datafixers/util/Either;",
            at = @At("HEAD"),
            argsOnly = true
    )
    private static <T> Decoder<T> spell_engine$useLocalSpellCodec(
            Decoder<T> decoder,
            Decoder<T> elementDecoder, RegistryOps<JsonElement> ops, ResourceKey<T> elementKey, FileToIdConverter converter, ResourceProvider resourceProvider) {
        if (elementKey.isFor(SpellRegistry.KEY)) {
            @SuppressWarnings("unchecked")
            var local = (Decoder<T>) SpellRegistry.LOCAL_CODEC;
            return local;
        }
        return decoder;
    }
}
