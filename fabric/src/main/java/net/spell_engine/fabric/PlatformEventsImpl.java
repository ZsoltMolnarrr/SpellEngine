package net.spell_engine.fabric;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemGroup;
import net.minecraft.loot.LootPool;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.spell_engine.PlatformEvents;
import net.spell_engine.compat.EnchantmentAllowBridge;
import net.spell_engine.fabric.mixin.LootTableBuilderAccessor;

import java.util.List;
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

    public static void onPlayerJoin(Consumer<net.minecraft.server.network.ServerPlayerEntity> callback) {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> callback.accept(handler.getPlayer()));
    }

    public static void onPlayerChangedWorld(Consumer<net.minecraft.server.network.ServerPlayerEntity> callback) {
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, target) -> callback.accept(player));
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
        // fabric-loot-api-v2 (Fabric API 0.92): no registry lookup is handed to the event on 1.20.1
        // (loot functions don't need one there), so `registries()` is null — see FabricLootContext.
        LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) ->
                callback.accept(new FabricLootContext(null, id, tableBuilder)));
    }

    public static void onItemGroupModify(RegistryKey<ItemGroup> group, PlatformEvents.ItemGroupModifier callback) {
        ItemGroupEvents.modifyEntriesEvent(group).register(content -> callback.modify(content, content.getContext()));
    }

    /// Fabric API 0.92 has no `EnchantmentEvents.ALLOW_ENCHANTING`. The callbacks are bridged into Spell Power's
    /// `EnchantmentRestriction`, whose `Enchantment#isAcceptableItem` + `EnchantmentHelper#getPossibleEntries`
    /// mixins gate the anvil, enchanted books and the enchanting table on both loaders (same bridge as Forge).
    /// Enchantments registered later (other mods' initializers) are picked up through the registry-add callback.
    public static void onAllowEnchanting(PlatformEvents.AllowEnchanting callback) {
        if (!enchantmentListenerInstalled) {
            enchantmentListenerInstalled = true;
            RegistryEntryAddedCallback.event(Registries.ENCHANTMENT).register((rawId, id, enchantment) ->
                    EnchantmentAllowBridge.install(enchantment));
        }
        EnchantmentAllowBridge.register(callback);
    }
    private static boolean enchantmentListenerInstalled = false;

    /// `registries` is null on 1.20.1 (see onLootTableModify); LootHelper must not depend on it.
    private record FabricLootContext(RegistryWrapper.WrapperLookup registries, Identifier tableId,
                                     net.minecraft.loot.LootTable.Builder builder)
            implements PlatformEvents.LootTableModifyContext {
        @Override
        public java.util.List<LootPool> existingPools() {
            // Snapshot: the builder's pool list is mutable (`List<LootPool>` on 1.20.1) and we append to it below.
            return List.copyOf(((LootTableBuilderAccessor) builder).spellEngine_getPools());
        }

        @Override
        public void addPool(LootPool pool) {
            builder.pool(pool); // FabricLootTableBuilder.pool(LootPool)
        }
    }
}
