package net.spell_engine.api.entity;

import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import net.minecraft.world.entity.Entity;

public interface TwoWayCollisionChecker {
    enum CollisionResult {
        PASS,
        COLLIDE,
        NONE
    }
    @Nullable Function<Entity, CollisionResult> getReverseCollisionChecker();
    void setReverseCollisionChecker(Function<Entity, CollisionResult> reverseCollisionChecker);
}
