package net.spell_engine.wizards;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.util.Identifier;
import net.spell_engine.api.client.CustomModels;
import net.spell_engine.api.status_effect.CustomModelStatusEffect;
import net.spell_engine.client.render.CustomLayers;

public class FrostShieldStatusEffect extends StatusEffect implements CustomModelStatusEffect {
    public FrostShieldStatusEffect(StatusEffectCategory category, int color) {
        super(category, color);
    }

    public static final Identifier modelId_base = new Identifier("spell_engine:frost_shield_base");
    public static final Identifier modelId_emissive = new Identifier("spell_engine:frost_shield_emissive");


    private static final RenderLayer BASE_RENDER_LAYER = RenderLayer.getTranslucentMovingBlock();
            //RenderLayer.getTranslucent();
    // RenderLayer.getTranslucentMovingBlock();
            // CustomLayers.projectile(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, true, false);
    private static final RenderLayer EMISSIVE_RENDER_LAYER = CustomLayers.projectile(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, false);


    @Override
    public void renderEffect(int amplifier, LivingEntity livingEntity, float delta, MatrixStack matrixStack, VertexConsumerProvider vertexConsumers, int light) {
        float yOffset = 0.51F; // y + 0.01 to avoid Y fighting
        matrixStack.push();
        matrixStack.translate(0, yOffset, 0); // y + 0.01 to avoid Y fighting
        CustomModels.render(BASE_RENDER_LAYER, MinecraftClient.getInstance().getItemRenderer(), modelId_base,
                matrixStack, vertexConsumers, light, livingEntity.getId());
        matrixStack.pop();

        matrixStack.push();
        matrixStack.translate(0, yOffset, 0); // y + 0.01 to avoid Y fighting
        CustomModels.render(EMISSIVE_RENDER_LAYER, MinecraftClient.getInstance().getItemRenderer(), modelId_emissive,
                matrixStack, vertexConsumers, light, livingEntity.getId());
        matrixStack.pop();
    }
}
