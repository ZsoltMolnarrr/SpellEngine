package net.spell_engine.mixin.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.spell_engine.api.effect.ActionImpairing;
import net.spell_engine.api.effect.EntityActionsAllowed;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(StatusEffect.class)
public class StatusEffectActionImpairing implements ActionImpairing {
    private EntityActionsAllowed entityActionsAllowed = null;
    @Override
    public @Nullable EntityActionsAllowed actionsAllowed() {
        return entityActionsAllowed;
    }

    @Override
    public void setAllowedEntityActions(EntityActionsAllowed actionsAllowed) {
        entityActionsAllowed = actionsAllowed;
    }
}
