package net.spell_engine.client.compatibility;

import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.api.v0.IrisApi;

import java.util.function.Supplier;

public class ShaderCompatibility {
    private static Supplier<Boolean> shaderPackInUse = () -> false;
    private static boolean vanillaRenderSystem = true;
    static void initialize() {
        if (FabricLoader.getInstance().isModLoaded("iris")) {
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
