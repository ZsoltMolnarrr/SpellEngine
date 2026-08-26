package net.spell_engine.mixin.client.render.state;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.spell_engine.api.effect.EntityTints;
import net.spell_engine.client.render.extension.EntityRenderStateExtension;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements EntityRenderStateExtension {
    @Unique @Nullable private Entity spellEngine_entity;
    @Unique private float spellEngine_tickDelta;
    @Unique private int spellEngine_tint = EntityTints.NEUTRAL;

    @Override
    public void spellEngine_setEntity(@Nullable Entity entity, float tickDelta) {
        this.spellEngine_entity = entity;
        this.spellEngine_tickDelta = tickDelta;
        // States are reused across extractions; LivingEntityRenderer re-extracts the tint for living entities
        this.spellEngine_tint = EntityTints.NEUTRAL;
    }

    @Override
    public @Nullable Entity spellEngine_getEntity() {
        return spellEngine_entity;
    }

    @Override
    public float spellEngine_getTickDelta() {
        return spellEngine_tickDelta;
    }

    @Override
    public void spellEngine_setTint(int argb) {
        this.spellEngine_tint = argb;
    }

    @Override
    public int spellEngine_getTint() {
        return spellEngine_tint;
    }
}
