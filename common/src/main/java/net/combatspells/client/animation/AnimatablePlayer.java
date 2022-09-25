package net.combatspells.client.animation;

import net.minecraft.util.math.Vec3d;

public interface AnimatablePlayer {
    void playRollAnimation(String animationName, Vec3d direction);
}
