package net.spell_engine.client.compatibility;
import net.spell_engine.Platform;

import net.irisshaders.iris.api.v0.IrisApi;

import java.util.function.Supplier;

public class ShaderCompatibility {
    private static Supplier<Boolean> shaderPackInUse = () -> false;
    private static boolean vanillaRenderSystem = true;
    static void initialize() {
        if (Platform.util().isModLoaded("iris")) {
            vanillaRenderSystem = false;
            shaderPackInUse = () -> IrisApi.getInstance().isShaderPackInUse();
        }
    }
    public static boolean isShaderPackInUse() {
        return shaderPackInUse.get();
    }
    public static boolean isVanillaRenderSystem() {
        return vanillaRenderSystem;
    }
}
