package net.spell_engine;

import com.mojang.brigadier.CommandDispatcher;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootPool;
import net.spell_engine.api.util.TriState;

import java.util.function.Consumer;

/// Loader-neutral game/server event registration, replacing the assorted Fabric API event
/// callbacks that `common` used to call directly (`ServerLifecycleEvents`, `ServerLivingEntityEvents`,
/// `CommandRegistrationCallback`, `LootTableEvents`, `EnchantmentEvents`, `ServerEntityWorldChangeEvents`,
/// `ServerPlayConnectionEvents`). Each method takes a common callback; the loader impl only wires it to
/// its own event bus. This keeps all behaviour in `common` while removing the Fabric API dependency.
public class PlatformEvents {
    /// Server is starting (resources available). Fabric: `ServerLifecycleEvents.SERVER_STARTING`;
    /// NeoForge: `ServerStartingEvent`.
    @ExpectPlatform
    public static void onServerStarting(Consumer<MinecraftServer> callback) {
        throw new AssertionError();
    }

    /// Server has started. Fabric: `ServerLifecycleEvents.SERVER_STARTED`; NeoForge: `ServerStartedEvent`.
    @ExpectPlatform
    public static void onServerStarted(Consumer<MinecraftServer> callback) {
        throw new AssertionError();
    }

    /// Datapacks finished (re)loading. Fabric: `ServerLifecycleEvents.END_DATA_PACK_RELOAD`;
    /// NeoForge: `OnDatapackSyncEvent`. The callback is idempotent and takes no server.
    @ExpectPlatform
    public static void onDataPackReloadComplete(Runnable callback) {
        throw new AssertionError();
    }

    /// A player joined the server. Fabric: `ServerPlayConnectionEvents.JOIN`; NeoForge:
    /// `PlayerEvent.PlayerLoggedInEvent`.
    @ExpectPlatform
    public static void onPlayerJoin(Consumer<ServerPlayer> callback) {
        throw new AssertionError();
    }

    /// A player changed dimension. Fabric: `ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD`;
    /// NeoForge: `PlayerEvent.PlayerChangedDimensionEvent`.
    @ExpectPlatform
    public static void onPlayerChangedWorld(Consumer<ServerPlayer> callback) {
        throw new AssertionError();
    }

    /// A living entity is about to take damage (side-effect hook, cannot deny). Fabric:
    /// `ServerLivingEntityEvents.ALLOW_DAMAGE`; NeoForge: `LivingIncomingDamageEvent`.
    @ExpectPlatform
    public static void onIncomingDamage(IncomingDamage callback) {
        throw new AssertionError();
    }

    /// Command registration. Fabric: `CommandRegistrationCallback`; NeoForge: `RegisterCommandsEvent`.
    @ExpectPlatform
    public static void onCommandRegistration(CommandRegistration callback) {
        throw new AssertionError();
    }

    /// Loot table load/modify. Fabric: `LootTableEvents.MODIFY`; NeoForge: `LootTableLoadEvent`.
    @ExpectPlatform
    public static void onLootTableModify(Consumer<LootTableModifyContext> callback) {
        throw new AssertionError();
    }

    /// Add entries to a creative item group. Fabric: `ItemGroupEvents.modifyEntriesEvent`; NeoForge:
    /// `BuildCreativeModeTabContentsEvent` (dispatched by tab key). Both `ItemGroup.Entries` and
    /// `ItemGroup.DisplayContext` are vanilla types the callback can use directly.
    @ExpectPlatform
    public static void onItemGroupModify(ResourceKey<CreativeModeTab> group, ItemGroupModifier callback) {
        throw new AssertionError();
    }

    /// Whether an enchantment may be applied to an item. Fabric: `EnchantmentEvents.ALLOW_ENCHANTING`;
    /// NeoForge: the `IItemExtension#supportsEnchantment` chokepoint (anvil + enchanting table) via mixin.
    /// The callback speaks in Spell Engine's own {@link TriState} ({@code ALLOW}/{@code DENY}/{@code PASS});
    /// each loader converts it to that loader's result type.
    @ExpectPlatform
    public static void onAllowEnchanting(AllowEnchanting callback) {
        throw new AssertionError();
    }

    // MARK: Callback types

    @FunctionalInterface
    public interface IncomingDamage {
        void accept(LivingEntity entity, DamageSource source, float amount);
    }

    @FunctionalInterface
    public interface CommandRegistration {
        void register(CommandDispatcher<CommandSourceStack> dispatcher,
                      CommandBuildContext registryAccess,
                      Commands.CommandSelection environment);
    }

    /// Handed to a loot-table-modify callback: exposes the table being loaded and lets the
    /// callback append pools without any loader-specific builder type.
    public interface LootTableModifyContext {
        HolderLookup.Provider registries();
        Identifier tableId();
        /// Snapshot of the pools the table already has (as parsed from the datapack, plus anything
        /// other mods added before us). Read-only; used to inspect what the table drops.
        java.util.List<LootPool> existingPools();
        void addPool(LootPool pool);
    }

    @FunctionalInterface
    public interface ItemGroupModifier {
        void modify(CreativeModeTab.Output entries, CreativeModeTab.ItemDisplayParameters context);
    }

    @FunctionalInterface
    public interface AllowEnchanting {
        /// Return {@code ALLOW} to force-allow, {@code DENY} to forbid, or {@code PASS} to defer to
        /// vanilla rules.
        TriState allow(Holder<Enchantment> enchantment, ItemStack stack);
    }
}
