package net.spell_engine.compat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.function.Function;

public class MultipartEntityCompat {
    public static final ArrayList<Function<Entity, Entity>> resolvers = new ArrayList<>();
    static {
        resolvers.add(entity -> {
            if (entity instanceof EnderDragonPart) {
                return ((EnderDragonPart) entity).owner;
            }
            return null;
        });
    }

    public static void addResolver(Function<Entity, Entity> resolver) {
        resolvers.add(resolver);
    }

    @Nullable
    public static Entity getOwner(Entity entity) {
        for (var resolver: resolvers) {
            var owner = resolver.apply(entity);
            if (owner != null) {
                return owner;
            }
        }
        return null;
    }

    /**
     * Resolves owner or returns self
     * @param entity
     * @return
     */
    public static Entity coalesce(Entity entity) {
        var owner = getOwner(entity);
        return owner != null ? owner : entity;
    }
}
