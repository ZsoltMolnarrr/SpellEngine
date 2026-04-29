package net.spell_engine.client.render;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
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
        // Note: "scale" is handled specially in SpellModelEffectRenderer (accumulated additively,
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
}
