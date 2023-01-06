package net.spell_engine.wizards.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.spell_engine.api.client.CustomModels;
import net.spell_engine.api.status_effect.CustomModelStatusEffect;
import net.spell_engine.client.render.CustomLayers;

public class FrostShieldRenderer implements CustomModelStatusEffect.Renderer {
    public static final Identifier modelId_base = new Identifier("spell_engine:frost_shield_base");
    public static final Identifier modelId_overlay = new Identifier("spell_engine:frost_shield_overlay");

    private static final RenderLayer BASE_RENDER_LAYER = RenderLayer.getTranslucentMovingBlock();
    private static final RenderLayer OVERLAY_RENDER_LAYER = CustomLayers.projectile(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, false);

    @Override
    public void renderEffect(int amplifier, LivingEntity livingEntity, float delta, MatrixStack matrixStack, VertexConsumerProvider vertexConsumers, int light) {
        float yOffset = 0.51F; // y + 0.01 to avoid Y fighting
        matrixStack.push();
        matrixStack.translate(0, yOffset, 0); // y + 0.01 to avoid Y fighting
        CustomModels.render(BASE_RENDER_LAYER, MinecraftClient.getInstance().getItemRenderer(), modelId_base,
                matrixStack, vertexConsumers, light, livingEntity.getId());
        matrixStack.pop();

        float overlayScale = 1.05F;
        matrixStack.push();
        matrixStack.translate(0, yOffset, 0); // y + 0.01 to avoid Y fighting
        matrixStack.scale(overlayScale, overlayScale, overlayScale);
        CustomModels.render(OVERLAY_RENDER_LAYER, MinecraftClient.getInstance().getItemRenderer(), modelId_overlay,
                matrixStack, vertexConsumers, light, livingEntity.getId());
        matrixStack.pop();
    }
}
