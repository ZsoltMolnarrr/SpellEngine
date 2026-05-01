package net.spell_engine.client.render;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.spell_engine.api.render.CustomModels;
import net.spell_engine.api.spell.fx.EasingHelper;
import net.spell_engine.api.spell.fx.ModelEffect;

import java.util.HashMap;
import java.util.Map;

public class ModelEffectOperations {
    @FunctionalInterface
    public interface OperationHandler {
        void apply(MatrixStack matrices, float progress, ModelEffect.Transform transform);
    }

    private static final Map<String, OperationHandler> REGISTRY = new HashMap<>();

    public static void register(String id, OperationHandler handler) {
        REGISTRY.put(id, handler);
    }

    public static OperationHandler get(String id) {
        return REGISTRY.get(id);
    }

    public static void registerDefaults() {
        // Note: "scale" is handled specially (accumulated additively,
        // applied as a single MatrixStack.scale call) and is not dispatched through this registry.
        register("translate", (matrices, progress, t) -> {
            matrices.translate(progress * t.x, progress * t.y, progress * t.z);
        });
        register("rotate", (matrices, progress, t) -> {
            if (t.x != 0) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(progress * t.x));
            if (t.y != 0) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(progress * t.y));
            if (t.z != 0) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(progress * t.z));
        });
    }

    /// Applies transforms and renders a single ModelEffect.
    /// `age` is the elapsed time in ticks (with tick delta) used to evaluate animations.
    public static void renderEffect(ModelEffect effect, float age, MatrixStack matrixStack,
                                    ItemRenderer itemRenderer, VertexConsumerProvider vertexConsumers, int light, int entityId) {
        matrixStack.push();

        // Scale deltas accumulated additively and applied as a single call,
        // because MatrixStack.scale() composes multiplicatively — chaining scale(0)*scale(s) = 0 forever.
        float sx = 0, sy = 0, sz = 0;

        // Initial transforms at full effect (progress = 1)
        for (var transform : effect.initial) {
            if ("scale".equals(transform.operation)) {
                sx += transform.x; sy += transform.y; sz += transform.z;
            } else {
                var handler = get(transform.operation);
                if (handler != null) handler.apply(matrixStack, 1F, transform);
            }
        }

        // Animated transforms
        for (var anim : effect.animations) {
            if (age < anim.start) continue;
            float t = (anim.end <= anim.start) ? 1F
                    : Math.clamp((age - anim.start) / (float)(anim.end - anim.start), 0F, 1F);
            float progress = EasingHelper.apply(anim.easing, t);
            if ("scale".equals(anim.operation)) {
                sx += progress * anim.x; sy += progress * anim.y; sz += progress * anim.z;
            } else {
                var handler = get(anim.operation);
                if (handler != null) handler.apply(matrixStack, progress, anim);
            }
        }

        // Apply base scale + accumulated deltas
        matrixStack.scale(effect.scale * (1 + sx), effect.scale * (1 + sy), effect.scale * (1 + sz));

        // Render model
        var modelId = Identifier.of(effect.model_id);
        var layer = SpellModelHelper.LAYERS.get(effect.light_emission);
        CustomModels.render(layer, itemRenderer, modelId, matrixStack, vertexConsumers, light, entityId);

        matrixStack.pop();
    }
}
