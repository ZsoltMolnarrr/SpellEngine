package net.spell_engine.compat;
import net.spell_engine.Platform;

import net.bettercombat.api.EntityPlayer_BetterCombat;
import net.bettercombat.logic.TargetHelper;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.spell_engine.api.spell.fx.PlayerAnimation;

import java.util.function.Function;

/// Better Combat 1.9.0+1.20.1 integration. The `net.bettercombat.api.fx` trail-appearance override
/// (`TrailAppearance` / `TrailAppearanceOverride`, Better Combat 2.4+) does not exist on the 1.20.1 line,
/// so glowing-weapon swing trails are dropped here; everything else is API-identical.
public class MeleeCompat {
    public static final String MOD_ID = "bettercombat";

    public record Attack(boolean isCombo, boolean isOffhand) {
        public static final Attack EMPTY = new Attack(false, false);
    }
    public static Function<PlayerEntity, Attack> attackProperties = player -> {
        var isCombo = player.getLastAttackTime() == (player.getAttackCooldownProgressPerTick() * 20);
        var isOffhand = false;
        return new Attack(isCombo, isOffhand);
    };
    public static Function<Entity, Boolean> isEntityHostileVehicle = entity -> { return false; };
    public static void init() {
        if (Platform.util().isModLoaded(MOD_ID)) {
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
            // TODO 1.20.1: swing-trail glow (`TrailAppearanceOverride`, Better Combat 2.4+) has no 1.20.1
            // counterpart; `GlowingItemStatusEffect` still tints the held item, only the trail stays plain.
        }
    }
}
