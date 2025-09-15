package net.spell_engine.client.compatibility;

public class CompatFeatures {
    public static void initialize() {
        FirstPersonAnimationCompatibility.initialize();
        ShaderCompatibility.initialize();
    }
}
