package net.spell_engine.fx;

import net.spell_engine.api.spell.fx.ModelEffectAttachment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.api.spell.fx.ModelEffect;
import net.spell_engine.entity.SpellModelEffect;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ModelEffectHelper {
    public static void spawn(Level world, Vec3 location, float yaw, List<ModelEffect> models) {
        spawn(world, location, yaw, models, null);
    }

    public static void spawn(Level world, Vec3 location, float yaw, List<ModelEffect> models,
                             @Nullable LivingEntity contextEntity) {
        if (models == null || models.isEmpty()) {
            return;
        }
        for (var effect : models) {
            if (effect.model_id == null || effect.model_id.isEmpty()) {
                continue;
            }
            if (effect.follow_entity && contextEntity != null) {
                ModelEffectAttachment.attach(contextEntity, effect, world.getGameTime());
            } else {
                var spawnPos = (contextEntity != null)
                        ? location.add(0, effect.positioning.vertical * contextEntity.getBbHeight(), 0)
                        : location;
                var entity = new SpellModelEffect(world);
                entity.setup(effect);
                entity.setPos(spawnPos);
                entity.setYRot(yaw);
                world.addFreshEntity(entity);
            }
        }
    }
}
