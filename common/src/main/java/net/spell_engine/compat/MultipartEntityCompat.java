package net.spell_engine.compat;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.function.Function;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;

public class MultipartEntityCompat {
    public static final ArrayList<Function<Entity, Entity>> resolvers = new ArrayList<>();
    static {
        resolvers.add(entity -> {
            if (entity instanceof EnderDragonPart) {
                return ((EnderDragonPart) entity).parentMob;
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
