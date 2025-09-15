package net.spell_engine.api.entity;

import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public interface TwoWayCollisionChecker {
    enum CollisionResult {
        PASS,
        COLLIDE,
        NONE
    }
    @Nullable Function<Entity, CollisionResult> getReverseCollisionChecker();
    void setReverseCollisionChecker(Function<Entity, CollisionResult> reverseCollisionChecker);
}
