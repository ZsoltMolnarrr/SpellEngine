package net.spell_engine.fabric;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.item.v1.EnchantmentEvents;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.storage.loot.LootPool;
import net.spell_engine.PlatformEvents;
import net.spell_engine.mixin.loot.LootTableBuilderAccessor;

import java.util.function.Consumer;

/// Fabric wiring for {@link PlatformEvents}. Each method forwards a Fabric API event to the
/// common callback; no game logic lives here.
public class PlatformEventsImpl {
    public static void onServerStarting(Consumer<net.minecraft.server.MinecraftServer> callback) {
        ServerLifecycleEvents.SERVER_STARTING.register(callback::accept);
    }

    public static void onServerStarted(Consumer<net.minecraft.server.MinecraftServer> callback) {
        ServerLifecycleEvents.SERVER_STARTED.register(callback::accept);
    }

    public static void onDataPackReloadComplete(Runnable callback) {
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> callback.run());
    }

    public static void onPlayerJoin(Consumer<net.minecraft.server.level.ServerPlayer> callback) {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> callback.accept(handler.getPlayer()));
    }

    public static void onPlayerChangedWorld(Consumer<net.minecraft.server.level.ServerPlayer> callback) {
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, target) -> callback.accept(player));
    }

    public static void onIncomingDamage(PlatformEvents.IncomingDamage callback) {
        // ALLOW_DAMAGE is used purely as a side-effect hook; we never deny.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            callback.accept(entity, source, amount);
            return true;
        });
    }

    public static void onCommandRegistration(PlatformEvents.CommandRegistration callback) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                callback.register(dispatcher, registryAccess, environment));
    }

    public static void onLootTableModify(Consumer<PlatformEvents.LootTableModifyContext> callback) {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) ->
                callback.accept(new FabricLootContext(registries, key.identifier(), tableBuilder)));
    }

    public static void onItemGroupModify(ResourceKey<CreativeModeTab> group, PlatformEvents.ItemGroupModifier callback) {
        CreativeModeTabEvents.modifyOutputEvent(group).register(output -> callback.modify(output, output.getContext()));
    }

    public static void onAllowEnchanting(PlatformEvents.AllowEnchanting callback) {
        EnchantmentEvents.ALLOW_ENCHANTING.register((enchantment, target, enchantingContext) ->
                // Convert Spell Engine's TriState to Fabric's TriState.
                switch (callback.allow(enchantment, target)) {
                    case ALLOW -> TriState.TRUE;
                    case DENY -> TriState.FALSE;
                    case PASS -> TriState.DEFAULT;
                });
    }

    private record FabricLootContext(HolderLookup.Provider registries, Identifier tableId,
                                     net.minecraft.world.level.storage.loot.LootTable.Builder builder)
            implements PlatformEvents.LootTableModifyContext {
        @Override
        public java.util.List<LootPool> existingPools() {
            return ((LootTableBuilderAccessor) builder).spellEngine_getPools().build();
        }

        @Override
        public void addPool(LootPool pool) {
            builder.pool(pool); // FabricLootTableBuilder.pool(LootPool)
        }
    }
}
