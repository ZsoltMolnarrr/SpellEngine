package net.spell_engine.client.compatibility;

import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import net.bettercombat.api.animation.FirstPersonAnimation;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.spell_engine.Platform;

public class BetterCombatCompatibility {
    public static void addFirstPersonAnimationLayer(AbstractClientPlayerEntity player, ModifierLayer layer) {
        if (Platform.isModLoaded("bettercombat")) {
            FirstPersonAnimation.addLayer(player, layer);
        }
    }

    public static boolean isRenderingAttackAnimationInFirstPerson() {
        return FirstPersonAnimation.isRenderingAttackAnimationInFirstPerson();
    }
}
