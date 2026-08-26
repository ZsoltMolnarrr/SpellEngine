package net.spell_engine.neoforge;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.spell_engine.PlatformEvents;
import net.spell_engine.api.event.CombatEvents;
import net.spell_engine.api.util.TriState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/// NeoForge wiring for {@link PlatformEvents}. Game-bus events are attached to
/// `NeoForge.EVENT_BUS`; the item-group callbacks are buffered by tab key and dispatched from the
/// mod-bus `BuildCreativeModeTabContentsEvent` handler registered in {@link NeoForgeMod}.
/// No game logic lives here.
public class PlatformEventsImpl {
    public static void onServerStarting(Consumer<MinecraftServer> callback) {
        NeoForge.EVENT_BUS.addListener(ServerStartingEvent.class, event -> callback.accept(event.getServer()));
    }

    public static void onServerStarted(Consumer<MinecraftServer> callback) {
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, event -> callback.accept(event.getServer()));
    }

    public static void onDataPackReloadComplete(Runnable callback) {
        // Fires after datapacks (re)load; the callback is idempotent so per-player firing is harmless.
        NeoForge.EVENT_BUS.addListener(OnDatapackSyncEvent.class, event -> callback.run());
    }

    public static void onPlayerJoin(Consumer<ServerPlayer> callback) {
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                callback.accept(player);
            }
        });
    }

    public static void onPlayerChangedWorld(Consumer<ServerPlayer> callback) {
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                callback.accept(player);
            }
        });
    }

    public static void onIncomingDamage(PlatformEvents.IncomingDamage callback) {
        // Side-effect hook only; never cancels.
        NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, event ->
                callback.accept(event.getEntity(), event.getSource(), event.getAmount()));
    }

    /// NeoForge counterpart of the Fabric-only shield-block wrap in `LivingEntityEvents` (NeoForge patches
    /// `BlocksAttacksComponent.onShieldHit` with an extra argument, so the common mixin cannot match it).
    /// Fires the same CombatEvents once NeoForge has confirmed the block.
    public static void registerShieldBlockBridge() {
        NeoForge.EVENT_BUS.addListener(LivingShieldBlockEvent.class, event -> {
            if (!event.getBlocked()) { return; }
            var entity = event.getEntity();
            var source = event.getDamageSource();
            var blockedAmount = event.getBlockedDamage();
            if (CombatEvents.ENTITY_SHIELD_BLOCK.isListened()) {
                var args = new CombatEvents.EntityShieldBlock.Args(entity, source, blockedAmount);
                CombatEvents.ENTITY_SHIELD_BLOCK.invoke(listener -> listener.onShieldBlock(args));
            }
            if (entity instanceof Player player && CombatEvents.PLAYER_SHIELD_BLOCK.isListened()) {
                var args = new CombatEvents.PlayerShieldBlock.Args(player, source, blockedAmount);
                CombatEvents.PLAYER_SHIELD_BLOCK.invoke(listener -> listener.onShieldBlock(args));
            }
        });
    }

    public static void onCommandRegistration(PlatformEvents.CommandRegistration callback) {
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event ->
                callback.register(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection()));
    }

    public static void onLootTableModify(Consumer<PlatformEvents.LootTableModifyContext> callback) {
        NeoForge.EVENT_BUS.addListener(LootTableLoadEvent.class, event -> {
            var context = new NeoForgeLootContext(event.getRegistries(), event.getName(), event.getTable().pools);
            callback.accept(context);
            if (!context.pools.isEmpty()) {
                // NeoForge exposes a built LootTable — rebuild it with the extra pools appended.
                var table = event.getTable();
                var pools = new ArrayList<>(table.pools);
                pools.addAll(context.pools);
                event.setTable(new LootTable(table.paramSet, table.randomSequence, pools, table.functions));
            }
        });
    }

    // Item-group callbacks are collected here and replayed by NeoForgeMod's mod-bus handler.
    private static final Map<ResourceKey<CreativeModeTab>, List<PlatformEvents.ItemGroupModifier>> itemGroupModifiers = new HashMap<>();

    public static void onItemGroupModify(ResourceKey<CreativeModeTab> group, PlatformEvents.ItemGroupModifier callback) {
        itemGroupModifiers.computeIfAbsent(group, key -> new ArrayList<>()).add(callback);
    }

    public static void dispatchItemGroup(ResourceKey<CreativeModeTab> group, CreativeModeTab.Output entries, CreativeModeTab.ItemDisplayParameters context) {
        var modifiers = itemGroupModifiers.get(group);
        if (modifiers != null) {
            for (var modifier : modifiers) {
                modifier.modify(entries, context);
            }
        }
    }

    // Enchant-applicability callbacks are buffered and consulted from IItemExtensionMixin, which
    // hooks the single `supportsEnchantment` chokepoint the anvil and enchanting table both funnel through.
    private static final List<PlatformEvents.AllowEnchanting> enchantCallbacks = new ArrayList<>();

    public static void onAllowEnchanting(PlatformEvents.AllowEnchanting callback) {
        enchantCallbacks.add(callback);
    }

    /// Combined enchant decision for the mixin. DENY wins over ALLOW; both win over PASS.
    public static TriState evaluateAllowEnchanting(Holder<Enchantment> enchantment, ItemStack stack) {
        var result = TriState.PASS;
        for (var callback : enchantCallbacks) {
            switch (callback.allow(enchantment, stack)) {
                case DENY -> { return TriState.DENY; }
                case ALLOW -> result = TriState.ALLOW;
                case PASS -> { }
            }
        }
        return result;
    }

    private static final class NeoForgeLootContext implements PlatformEvents.LootTableModifyContext {
        private final HolderLookup.Provider registries;
        private final Identifier tableId;
        private final List<LootPool> existingPools;
        private final List<LootPool> pools = new ArrayList<>();

        private NeoForgeLootContext(HolderLookup.Provider registries, Identifier tableId, List<LootPool> existingPools) {
            this.registries = registries;
            this.tableId = tableId;
            this.existingPools = existingPools;
        }

        @Override public HolderLookup.Provider registries() { return registries; }
        @Override public Identifier tableId() { return tableId; }
        @Override public List<LootPool> existingPools() { return existingPools; }
        @Override public void addPool(LootPool pool) { pools.add(pool); }
    }
}
