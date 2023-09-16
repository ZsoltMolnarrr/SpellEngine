package net.spell_engine.api.effect;

import net.minecraft.entity.effect.StatusEffect;
import org.jetbrains.annotations.Nullable;

public interface ActionImpairing {
    @Nullable EntityActionsAllowed actionsAllowed();
    void setAllowedEntityActions(EntityActionsAllowed actionsAllowed);

    static void configure(StatusEffect effect, EntityActionsAllowed actionsAllowed) {
        ((ActionImpairing)effect).setAllowedEntityActions(actionsAllowed);
    }
}
