package net.spell_engine.wizards;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.util.Identifier;
import net.spell_engine.api.client.CustomModels;
import net.spell_engine.api.spell.ParticleBatch;
import net.spell_engine.api.status_effect.CustomModelStatusEffect;
import net.spell_engine.api.status_effect.CustomParticleStatusEffect;
import net.spell_engine.client.render.CustomLayers;
import net.spell_engine.particle.ParticleHelper;
import net.spell_power.api.statuseffects.SpellVulnerabilityStatusEffect;

public class FrozenStatusEffect extends SpellVulnerabilityStatusEffect
        implements CustomParticleStatusEffect, CustomModelStatusEffect {
    public FrozenStatusEffect(StatusEffectCategory statusEffectCategory, int color) {
        super(statusEffectCategory, color);
    }

    public boolean shouldRemoveOnDirectHit() {
        return true;
    }

    public static final ParticleBatch particles = new ParticleBatch(
            "spell_engine:frost_hit",
            ParticleBatch.Shape.SPHERE,
            ParticleBatch.Origin.CENTER,
            null,
            1,
            0.1F,
            0.3F,
            0);

    @Override
    public void spawnParticles(LivingEntity livingEntity, int amplifier) {
        var scaledParticles = new ParticleBatch(particles);
        scaledParticles.count *= (amplifier + 1);
        ParticleHelper.play(livingEntity.world, livingEntity, scaledParticles);
    }

    private static final RenderLayer RENDER_LAYER = CustomLayers.projectile(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, false);
    private static final Identifier modelId = new Identifier("spell_engine:frost_trap");
    @Override
    public void renderEffect(int amplifier, LivingEntity livingEntity, float delta, MatrixStack matrixStack, VertexConsumerProvider vertexConsumers, int light) {
        matrixStack.push();
        matrixStack.translate(0, 0.5, 0);
        CustomModels.render(RENDER_LAYER, MinecraftClient.getInstance().getItemRenderer(), modelId,
                matrixStack, vertexConsumers, light, livingEntity.getId());
        matrixStack.pop();
    }
}
