package net.spell_engine.api.effect;

import java.util.function.Consumer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;

public interface OnRemoval {
    record Context(LivingEntity entity) { }
    Consumer<Context> removalHandler();
    void setRemovalHandler(Consumer<Context> handler);

    static void configure(MobEffect effect, Consumer<Context> handler) {
        ((OnRemoval)effect).setRemovalHandler(handler);
    }
}
