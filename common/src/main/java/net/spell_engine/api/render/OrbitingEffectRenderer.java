package net.spell_engine.api.render;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.api.effect.CustomModelStatusEffect;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;

public class OrbitingEffectRenderer implements CustomModelStatusEffect.Renderer {
    public record Model(RenderType layer, Identifier modelId) { }
    private List<Model> models;
    private float scale;
    private float horizontalOffset;
    protected float orbitingSpeed = 2.25F; // Speed of orbiting effect

    public OrbitingEffectRenderer(List<Model> models, float scale, float horizontalOffset) {
        this.models = models;
        this.scale = scale;
        this.horizontalOffset = horizontalOffset;
    }

    // `appliedAtWorldTime` ignored: the orbit angle is driven by the entity's own age, so a
    // client/server age offset only shifts the (continuous, periodic) phase — invisible.
    @Override
    public void renderEffect(long appliedAtWorldTime, int amplifier, LivingEntity livingEntity, float delta, PoseStack matrixStack, SubmitNodeCollector queue, int light) {
        matrixStack.pushPose();
        var time = livingEntity.tickCount + delta;

        var initialAngle = time * orbitingSpeed - 45.0F;
        var entityScale = livingEntity.getScale();
        var horizontalOffset = this.horizontalOffset * livingEntity.getAgeScale();
        var verticalOffset = livingEntity.getBbHeight() / (2F * entityScale);

        var stacks = amplifier + 1;
        var turnAngle = 360F / stacks;
        for (int i = 0; i < stacks; i++) {
            var angle = initialAngle + turnAngle * i;
            renderModel(matrixStack, scale, verticalOffset, horizontalOffset, angle, queue, light, livingEntity);
        }

        matrixStack.popPose();
    }

    private void renderModel(PoseStack matrixStack, float scale, float verticalOffset, float horizontalOffset, float rotation,
                               SubmitNodeCollector queue, int light, LivingEntity livingEntity) {
        matrixStack.pushPose();

        matrixStack.mulPose(Axis.YP.rotationDegrees(rotation));
        matrixStack.translate(0, verticalOffset, -horizontalOffset);
        matrixStack.scale(scale, scale, scale);

        for(var model: models) {
            matrixStack.pushPose();
            CustomModels.render(model.layer, model.modelId,
                    matrixStack, queue, light, livingEntity.getId());
            matrixStack.popPose();
        }

        matrixStack.popPose();
    }
}
