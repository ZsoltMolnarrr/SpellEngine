package net.spell_engine.fx;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.spell_engine.api.spell.fx.ModelEffect;
import net.spell_engine.entity.SpellModelEffect;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ModelEffectHelper {
    public static void spawn(World world, Vec3d location, float yaw, List<ModelEffect> models) {
        spawn(world, location, yaw, models, null);
    }

    public static void spawn(World world, Vec3d location, float yaw, List<ModelEffect> models,
                             @Nullable LivingEntity contextEntity) {
        if (models == null || models.isEmpty()) {
            return;
        }
        for (var effect : models) {
            if (effect.model_id == null || effect.model_id.isEmpty()) {
                continue;
            }
            var spawnPos = (contextEntity != null)
                    ? location.add(0, effect.positioning.vertical * contextEntity.getHeight(), 0)
                    : location;
            var entity = new SpellModelEffect(world);
            entity.setup(effect);
            entity.setPosition(spawnPos);
            entity.setYaw(yaw);
            world.spawnEntity(entity);
        }
    }
}
