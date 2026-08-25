package net.spell_engine.mixin.client.render.state;

import net.spell_engine.client.render.extension.EntityRenderStateExtension;

import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements EntityRenderStateExtension {
    @Unique @Nullable private Entity spellEngine_entity;
    @Unique private float spellEngine_tickDelta;

    @Override
    public void spellEngine_setEntity(@Nullable Entity entity, float tickDelta) {
        this.spellEngine_entity = entity;
        this.spellEngine_tickDelta = tickDelta;
    }

    @Override
    public @Nullable Entity spellEngine_getEntity() {
        return spellEngine_entity;
    }

    @Override
    public float spellEngine_getTickDelta() {
        return spellEngine_tickDelta;
    }
}
