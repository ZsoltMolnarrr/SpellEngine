package net.spell_engine.fx;

import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.spell_engine.api.spell.fx.ModelEffect;
import net.spell_engine.entity.SpellModelEffect;

public class ModelEffectHelper {
    public static void spawn(World world, Vec3d location, float yaw, ModelEffect[] models) {
        if (models == null || models.length == 0) {
            return;
        }
        for (var effect : models) {
            if (effect.model_id == null || effect.model_id.isEmpty()) {
                continue;
            }
            var entity = new SpellModelEffect(world);
            entity.setup(effect);
            entity.setPosition(location);
            entity.setYaw(yaw);
            world.spawnEntity(entity);
        }
    }
}
