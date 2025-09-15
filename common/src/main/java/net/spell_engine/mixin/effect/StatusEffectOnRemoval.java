package net.spell_engine.mixin.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.spell_engine.api.effect.OnRemoval;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.Consumer;

@Mixin(StatusEffect.class)
public class StatusEffectOnRemoval implements OnRemoval {
    private Consumer<Context> customRemovalHandler;
    @Override
    public Consumer<Context> removalHandler() {
        return customRemovalHandler;
    }

    @Override
    public void setRemovalHandler(Consumer<Context> handler) {
        customRemovalHandler = handler;
    }
}
