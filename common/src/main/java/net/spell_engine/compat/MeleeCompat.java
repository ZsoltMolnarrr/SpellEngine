package net.spell_engine.compat;
import net.spell_engine.Platform;

import net.bettercombat.api.EntityPlayer_BetterCombat;
import net.bettercombat.api.fx.TrailAppearance;
import net.bettercombat.api.fx.TrailAppearanceOverride;
import net.bettercombat.logic.TargetHelper;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.spell_engine.api.effect.GlowingItemStatusEffect;
import net.spell_engine.api.spell.fx.PlayerAnimation;
import net.spell_engine.client.util.Color;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class MeleeCompat {
    public record Attack(boolean isCombo, boolean isOffhand) {
        public static final Attack EMPTY = new Attack(false, false);
    }
    public static Function<Player, Attack> attackProperties = player -> {
        var isCombo = player.getLastHurtMobTimestamp() == (player.getCurrentItemAttackStrengthDelay() * 20);
        var isOffhand = false;
        return new Attack(isCombo, isOffhand);
    };
    public static Function<Entity, Boolean> isEntityHostileVehicle = entity -> { return false; };
    public static void init() {
        if (Platform.util().isModLoaded("bettercombat")) {
            attackProperties = (player) -> {
                var attack = ((EntityPlayer_BetterCombat) player).getCurrentAttack();
                if (attack != null) {
                    var isCombo = attack.combo().total() == attack.combo().current();
                    var isOffhand = attack.isOffHand();
                    return new Attack(isCombo, isOffhand);
                } else {
                    return Attack.EMPTY;
                }
            };
            PlayerAnimation.twoHandedChecker = (stack) -> {
                var attributes = WeaponRegistry.getAttributes(stack);
                if (attributes != null) {
                    return attributes.isTwoHanded();
                }
                return false;
            };
            isEntityHostileVehicle = (entity) -> {
                return TargetHelper.isEntityHostileVehicle(entity.getName().getString());
            };
            try {
                registerTrailAppearanceOverride();
            } catch (Throwable ignored) {
                // Better Combat only grew the trail override in 2.4.0, and an older one is entitled to be
                // installed. Swings will not glow, which is no reason to take the rest of the compat down.
            }
        }
    }

    /// Swing trails in the color of the weapon's glow, for as long as a glowing effect burns on its wielder.
    ///
    /// Kept apart from [#init] so that a Better Combat too old to carry [TrailAppearanceOverride] fails on
    /// the call to this method, where it is caught, rather than while linking `init` itself, where it is not.
    private static void registerTrailAppearanceOverride() {
        TrailAppearanceOverride.register((attacker, stack, resolved) -> {
            var glow = GlowingItemStatusEffect.resolve(attacker, stack);
            if (glow == null) {
                return null; // Nothing of ours to say, leave the trail as it was resolved
            }
            // A new appearance rather than a touch-up of the resolved one: what we are handed may be
            // `TrailAppearance.DEFAULT` itself, shared and mutable, and writing to it would color
            // every swing in the game.
            return new TrailAppearance(glowing(resolved.primary, glow), glowing(resolved.secondary, glow));
        });
    }

    /// A trail part in the color of the glow the weapon carries.
    ///
    /// The part keeps its own alpha, which is what tells the bright core of a trail from its faint edge,
    /// and takes on the color only as far as the glow has come: a single stack barely tints it, a full
    /// one carries it over completely. The same ramp the item itself glows on, so the two stay in step.
    @Nullable
    private static TrailAppearance.Part glowing(@Nullable TrailAppearance.Part part, Color glow) {
        if (part == null) {
            return null;
        }
        var base = Color.fromRGBA(part.color_rgba());
        var tinted = base.blend(glow, glow.alpha()).alpha(base.alpha());
        return new TrailAppearance.Part(tinted.toRGBA(), true);
    }
}
