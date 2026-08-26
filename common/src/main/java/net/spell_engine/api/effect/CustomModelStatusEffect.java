package net.spell_engine.api.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;

public final class CustomModelStatusEffect {
    public interface Renderer {
        void renderEffect(long appliedAtWorldTime, int amplifier, LivingEntity livingEntity, float delta,
                          PoseStack matrixStack, SubmitNodeCollector queue, int light);
    }
    public record Args(boolean scaleWithEntity) {
        public static final Args DEFAULT = new Args(true);
    }
    public record Entry(Renderer renderer, Args args) { }

    private static final Map<MobEffect, Entry> renderers = new HashMap<>();

    public static void register(MobEffect statusEffect, Renderer renderer) {
        register(statusEffect, renderer, Args.DEFAULT);
    }

    public static void register(MobEffect statusEffect, Renderer renderer, Args args) {
        renderers.put(statusEffect, new Entry(renderer, args));
    }

    public static Entry entryOf(MobEffect statusEffect) {
        return renderers.get(statusEffect);
    }

    public static Renderer rendererOf(MobEffect statusEffect) {
        return renderers.get(statusEffect).renderer();
    }
}
