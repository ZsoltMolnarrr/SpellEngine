package net.spell_engine.api.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;

import java.util.function.Consumer;

public interface OnRemoval {
    record Context(LivingEntity entity) { }
    Consumer<Context> removalHandler();
    void setRemovalHandler(Consumer<Context> handler);

    static void configure(StatusEffect effect, Consumer<Context> handler) {
        ((OnRemoval)effect).setRemovalHandler(handler);
    }
}
