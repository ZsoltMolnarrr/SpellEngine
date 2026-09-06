package net.spell_engine.forge;

import net.minecraft.item.ItemGroup;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.spell_engine.PlatformEvents;
import net.spell_engine.compat.EnchantmentAllowBridge;
import net.spell_engine.forge.mixin.LootTableAccessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/// Forge 47 wiring for {@link PlatformEvents}. Game-bus events are attached to `MinecraftForge.EVENT_BUS`
/// with the explicit 4-arg `addListener(priority, receiveCancelled, Class, Consumer)` overload (Forge 47's
/// `addListener(Consumer)` infers the event type from the lambda via TypeTools, which is fragile). The
/// item-group callbacks are buffered by tab key and dispatched from the mod-bus
/// `BuildCreativeModeTabContentsEvent` handler registered in {@link ForgeMod}. No game logic lives here.
public class PlatformEventsImpl {
    public static void onServerStarting(Consumer<MinecraftServer> callback) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ServerStartingEvent.class,
                event -> callback.accept(event.getServer()));
    }

    public static void onServerStarted(Consumer<MinecraftServer> callback) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ServerStartedEvent.class,
                event -> callback.accept(event.getServer()));
    }

    public static void onDataPackReloadComplete(Runnable callback) {
        // Forge 47 fires OnDatapackSyncEvent both after a datapack (re)load (player == null, "sync everyone")
        // and once per joining player (player != null). Only the former matches Fabric's END_DATA_PACK_RELOAD;
        // the callback is idempotent, so filtering is an optimisation, not a correctness requirement.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, OnDatapackSyncEvent.class, event -> {
            if (event.getPlayer() == null) {
                callback.run();
            }
        });
    }

    public static void onPlayerJoin(Consumer<ServerPlayerEntity> callback) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayerEntity player) {
                callback.accept(player);
            }
        });
    }

    public static void onPlayerChangedWorld(Consumer<ServerPlayerEntity> callback) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerEvent.PlayerChangedDimensionEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayerEntity player) {
                callback.accept(player);
            }
        });
    }

    public static void onIncomingDamage(PlatformEvents.IncomingDamage callback) {
        // Side-effect hook only; never cancels. LivingAttackEvent fires at `LivingEntity#damage` HEAD, the
        // closest Forge match for Fabric's ALLOW_DAMAGE (which fires after the invulnerability checks but before
        // shield blocking/armor); `LivingHurtEvent` would fire only after shield blocking and i-frames. The
        // client-side and invulnerable-target invocations (which Fabric's hook never sees) are filtered out.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingAttackEvent.class, event -> {
            var entity = event.getEntity();
            if (!entity.getWorld().isClient() && !entity.isInvulnerableTo(event.getSource())) {
                callback.accept(entity, event.getSource(), event.getAmount());
            }
        });
    }

    public static void onCommandRegistration(PlatformEvents.CommandRegistration callback) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RegisterCommandsEvent.class, event ->
                callback.register(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection()));
    }

    public static void onLootTableModify(Consumer<PlatformEvents.LootTableModifyContext> callback) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LootTableLoadEvent.class, event -> {
            var table = event.getTable();
            // LootTableLoadEvent fires while the datapack contents are still being built (no server / registry
            // manager reachable); 1.20.1 loot functions need no RegistryWrapper.WrapperLookup anyway.
            var context = new ForgeLootContext(event.getName(), List.copyOf(((LootTableAccessor) table).spellEngine_getPools()));
            callback.accept(context);
            // Mutate the loaded table in place through Forge's patched `LootTable#addPool` (the table is not
            // frozen yet at this point). Replacing it via `event.setTable(...)` would drop Forge's
            // `lootTableId`, which global loot modifiers key on.
            for (var pool : context.pools) {
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

    /// Forge 47 has no global enchant-applicability event (only the per-item `IForgeItem#canApplyAtEnchantingTable`).
    /// The callbacks are bridged into Spell Power's `EnchantmentRestriction`, whose `Enchantment#isAcceptableItem`
    /// + `EnchantmentHelper#getPossibleEntries` mixins gate the anvil, enchanted books and the enchanting table on
    /// both loaders. Enchantments registered after this call are covered by {@link ForgeMod}'s
    /// `FMLCommonSetupEvent` listener (`EnchantmentAllowBridge.installAll()`).
    public static void onAllowEnchanting(PlatformEvents.AllowEnchanting callback) {
        EnchantmentAllowBridge.register(callback);
    }

    private static final class ForgeLootContext implements PlatformEvents.LootTableModifyContext {
        private final Identifier tableId;
        private final List<LootPool> existingPools;
        private final List<LootPool> pools = new ArrayList<>();

        private ForgeLootContext(Identifier tableId, List<LootPool> existingPools) {
            this.tableId = tableId;
            this.existingPools = existingPools;
        }

        @Override public Identifier tableId() { return tableId; }
        @Override public List<LootPool> existingPools() { return existingPools; }
        @Override public void addPool(LootPool pool) { pools.add(pool); }
    }
}
