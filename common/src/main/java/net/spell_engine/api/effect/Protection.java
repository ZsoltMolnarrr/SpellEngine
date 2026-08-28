package net.spell_engine.api.effect;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRules;
import net.spell_engine.api.spell.fx.ParticleGroup;
import net.spell_engine.fx.ParticleHelper;
import net.spell_engine.utils.SoundHelper;
import net.spell_engine.utils.StatusEffectUtil;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// Charge-consuming damage protections: a registered status effect turns one incoming hit into
/// a miss, plays a "pop" FX and spends one amplifier level doing it.
///
/// Applies to any [LivingEntity] (players, mobs, summons) — the single call site is
/// `mixin/entity/LivingEntityProtectionEffectMixin`, which hooks
/// `LivingEntity#isInvulnerableTo(ServerLevel, DamageSource)` at RETURN so that a charge is only
/// ever spent on a hit that vanilla (and SpellEngine's own free immunities) had already agreed
/// would land.
public class Protection {
    public record Pop(List<ParticleGroup> particles, @Nullable SoundEvent sound) { }
    public record Entry(Holder<MobEffect> effectEntry, TagKey<DamageType> protects,
                        int decrement, Pop onDecrement, Pop onRemove) { }
    public static final Map<ResourceKey<MobEffect>, Entry> PROTECTIONS = new HashMap<>();

    public static void register(Holder<MobEffect> effectEntry, Pop pop) {
        register(effectEntry.unwrapKey().get(), new Entry(effectEntry, null, 1, pop, pop));
    }

    public static void register(Holder<MobEffect> effectEntry, TagKey<DamageType> protects, Pop pop) {
        register(effectEntry.unwrapKey().get(), new Entry(effectEntry, protects, 1, pop, pop));
    }

    public static void register(ResourceKey<MobEffect> key, Entry entry) {
        PROTECTIONS.put(key, entry);
    }

    /// Spend one protection charge if `entity` holds a protection effect that covers `damageSource`.
    ///
    /// NOT a query — on a `true` result the pop FX has played and the effect's amplifier has been
    /// decremented (or the effect removed). Only call it once the hit is known to be otherwise
    /// unavoidable; see [#isHitAlreadyBlocked].
    public static boolean tryProtect(LivingEntity entity, ServerLevel level, DamageSource damageSource) {
        if (PROTECTIONS.isEmpty()) {
            return false;
        }
        if (isHitAlreadyBlocked(entity, level, damageSource)) {
            return false;
        }
        for (var entry: entity.getActiveEffectsMap().entrySet()) {
            var optionalKey = entry.getKey().unwrapKey();
            if (optionalKey.isEmpty()) { // Should never happen, added due to some incompatibility crash
                continue;
            }
            var key = optionalKey.get();
            var protection = PROTECTIONS.get(key);
            if (protection != null) {
                if (protection.protects != null && !damageSource.is(protection.protects)) {
                    continue; // This protection does not apply to this damage type
                }
                var effect = entry.getValue();
                var newAmplifier = effect.getAmplifier() - protection.decrement;

                var pop = newAmplifier < 0 ? protection.onRemove : protection.onDecrement;
                if (pop != null) {
                    ParticleHelper.sendBatches(entity, pop.particles);
                    if (pop.sound != null) {
                        SoundHelper.playSoundEvent(entity.level(), entity, pop.sound);
                    }
                }
                StatusEffectUtil.applyChanges(entity, List.of(
                        new StatusEffectUtil.Diff(effect, newAmplifier)
                ));
                return true;
            }
        }
        return false;
    }

    /// True when the hit is going to be refused further up the call stack anyway, so spending a
    /// charge on it would be waste.
    ///
    /// The protection hook sits on `LivingEntity#isInvulnerableTo`. For a player that method is
    /// reached as the `super` call on the *first* line of `Player#isInvulnerableTo`
    /// (`world/entity/player/Player.java:663-675`), so at hook time none of the following have run
    /// yet — all of them can still refuse the hit:
    /// - the four gamerule branches of `Player#isInvulnerableTo` (drowning / fall / fire / freeze),
    /// - `ServerPlayer#isInvulnerableTo`'s dimension-change and client-not-loaded branches
    ///   (`server/level/ServerPlayer.java:1248-1250`),
    /// - the `abilities.invulnerable` check in `Player#hurtServer`, which vanilla performs
    ///   immediately *after* `isInvulnerableTo` (`Player.java:678-685`) — i.e. creative mode.
    ///
    /// Non-player living entities have nothing comparable: the vanilla subclasses that override
    /// `isInvulnerableTo` (`Ghast`, `Breeze`, `Warden`) all evaluate their own condition *before*
    /// delegating to `super`, so a hit they refuse never reaches this hook at all.
    public static boolean isHitAlreadyBlocked(LivingEntity entity, ServerLevel level, DamageSource source) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        if (player.getAbilities().invulnerable && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true; // Creative mode — Player#hurtServer refuses the hit right after isInvulnerableTo
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.isChangingDimension() && !source.is(DamageTypes.ENDER_PEARL)) {
                return true;
            }
            if (serverPlayer.connection != null && !serverPlayer.connection.hasClientLoaded()) {
                return true;
            }
        }
        // Same branches, same order as Player#isInvulnerableTo
        var rules = level.getGameRules();
        if (source.is(DamageTypeTags.IS_DROWNING)) {
            return !rules.get(GameRules.DROWNING_DAMAGE);
        }
        if (source.is(DamageTypeTags.IS_FALL)) {
            return !rules.get(GameRules.FALL_DAMAGE);
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return !rules.get(GameRules.FIRE_DAMAGE);
        }
        if (source.is(DamageTypeTags.IS_FREEZING)) {
            return !rules.get(GameRules.FREEZE_DAMAGE);
        }
        return false;
    }
}
