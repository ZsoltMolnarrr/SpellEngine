package net.spell_engine.forge;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootPool;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.entity.living.LivingIncomingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.spell_engine.PlatformEvents;
import net.spell_engine.api.util.TriState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/// NeoForge wiring for {@link PlatformEvents}. Game-bus events are attached to
/// `MinecraftForge.EVENT_BUS`; the item-group callbacks are buffered by tab key and dispatched from the
/// mod-bus `BuildCreativeModeTabContentsEvent` handler registered in {@link ForgeMod}.
/// No game logic lives here.
public class PlatformEventsImpl {
    public static void onServerStarting(Consumer<MinecraftServer> callback) {
        MinecraftForge.EVENT_BUS.addListener(ServerStartingEvent.class, event -> callback.accept(event.getServer()));
    }

    public static void onServerStarted(Consumer<MinecraftServer> callback) {
        MinecraftForge.EVENT_BUS.addListener(ServerStartedEvent.class, event -> callback.accept(event.getServer()));
    }

    public static void onDataPackReloadComplete(Runnable callback) {
        // Fires after datapacks (re)load; the callback is idempotent so per-player firing is harmless.
        MinecraftForge.EVENT_BUS.addListener(OnDatapackSyncEvent.class, event -> callback.run());
    }

    public static void onPlayerJoin(Consumer<ServerPlayerEntity> callback) {
        MinecraftForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayerEntity player) {
                callback.accept(player);
            }
        });
    }

    public static void onPlayerChangedWorld(Consumer<ServerPlayerEntity> callback) {
        MinecraftForge.EVENT_BUS.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayerEntity player) {
                callback.accept(player);
            }
        });
    }

    public static void onIncomingDamage(PlatformEvents.IncomingDamage callback) {
        // Side-effect hook only; never cancels.
        MinecraftForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, event ->
                callback.accept(event.getEntity(), event.getSource(), event.getAmount()));
    }

    public static void onCommandRegistration(PlatformEvents.CommandRegistration callback) {
        MinecraftForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event ->
                callback.register(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection()));
    }

    public static void onLootTableModify(Consumer<PlatformEvents.LootTableModifyContext> callback) {
        MinecraftForge.EVENT_BUS.addListener(LootTableLoadEvent.class, event -> {
            var table = event.getTable();
            var context = new ForgeLootContext(event.getRegistries(), event.getName(), table.pools);
            callback.accept(context);
            // Mutate the loaded table in place. Replacing it via `event.setTable(new LootTable(...))`
            // would drop NeoForge's `lootTableId` field (set before this event, one-shot, not a codec
            // field), which is what `CommonHooks.modifyLoot` keys global loot modifiers on — every GLM
            // conditioned on `neoforge:loot_table_id` would then silently stop applying to this table.
            for (var pool: context.pools) {
                table.addPool(pool);
            }
        });
    }

    // Item-group callbacks are collected here and replayed by ForgeMod's mod-bus handler.
    private static final Map<RegistryKey<ItemGroup>, List<PlatformEvents.ItemGroupModifier>> itemGroupModifiers = new HashMap<>();

    public static void onItemGroupModify(RegistryKey<ItemGroup> group, PlatformEvents.ItemGroupModifier callback) {
        itemGroupModifiers.computeIfAbsent(group, key -> new ArrayList<>()).add(callback);
    }

    public static void dispatchItemGroup(RegistryKey<ItemGroup> group, ItemGroup.Entries entries, ItemGroup.DisplayContext context) {
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
    public static TriState evaluateAllowEnchanting(RegistryEntry<Enchantment> enchantment, ItemStack stack) {
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

    private static final class ForgeLootContext implements PlatformEvents.LootTableModifyContext {
        private final RegistryWrapper.WrapperLookup registries;
        private final Identifier tableId;
        private final List<LootPool> existingPools;
        private final List<LootPool> pools = new ArrayList<>();

        private ForgeLootContext(RegistryWrapper.WrapperLookup registries, Identifier tableId, List<LootPool> existingPools) {
            this.registries = registries;
            this.tableId = tableId;
            this.existingPools = existingPools;
        }

        @Override public RegistryWrapper.WrapperLookup registries() { return registries; }
        @Override public Identifier tableId() { return tableId; }
        @Override public List<LootPool> existingPools() { return existingPools; }
        @Override public void addPool(LootPool pool) { pools.add(pool); }
    }
}
