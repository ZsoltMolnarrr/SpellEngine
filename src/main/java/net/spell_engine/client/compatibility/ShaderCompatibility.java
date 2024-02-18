package net.spell_engine.client.compatibility;

import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.api.v0.IrisApi;

import java.util.function.Supplier;

public class ShaderCompatibility {
    private static Supplier<Boolean> shaderPackInUse = () -> false;
    public static void setup() {
        if (FabricLoader.getInstance().isModLoaded("iris")) {
            shaderPackInUse = () -> IrisApi.getInstance().isShaderPackInUse();
        }
    }
    public static boolean isShaderPackInUse() {
        return shaderPackInUse.get();
    }
}
