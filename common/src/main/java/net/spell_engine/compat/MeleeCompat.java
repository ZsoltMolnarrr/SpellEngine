package net.spell_engine.compat;

import net.bettercombat.api.EntityPlayer_BetterCombat;
import net.bettercombat.api.WeaponAttributesHelper;
import net.bettercombat.logic.WeaponRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.spell_engine.api.spell.fx.PlayerAnimation;

import java.util.function.Function;

public class MeleeCompat {
    public record Attack(boolean isCombo, boolean isOffhand) {
        public static final Attack EMPTY = new Attack(false, false);
    }
    public static Function<PlayerEntity, Attack> attackProperties = player -> {
        var isCombo = player.getLastAttackTime() == (player.getAttackCooldownProgressPerTick() * 20);
        var isOffhand = false;
        return new Attack(isCombo, isOffhand);
    };
    public static void init() {
        if (FabricLoader.getInstance().isModLoaded("bettercombat")) {
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
        }
    }
}
