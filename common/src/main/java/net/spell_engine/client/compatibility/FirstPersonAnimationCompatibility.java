package net.spell_engine.client.compatibility;

import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import net.fabricmc.loader.api.FabricLoader;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.config.TriStateAuto;

public class FirstPersonAnimationCompatibility {
    private static boolean isCameraModPresent = false;

    static void initialize() {
        var cameraMods = new String[] {
            "firstperson", "realcamera"
        };
        for (var mod : cameraMods) {
            if (FabricLoader.getInstance().isModLoaded(mod)) {
                isCameraModPresent = true;
                break;
            }
        }
    }

    public static FirstPersonMode firstPersonMode() {
        switch (SpellEngineClient.config.firstPersonAnimations) {
            case TriStateAuto.YES:
                return FirstPersonMode.THIRD_PERSON_MODEL;
            case TriStateAuto.NO:
                return FirstPersonMode.NONE;
            default:
                return isCameraModPresent ? FirstPersonMode.NONE : FirstPersonMode.THIRD_PERSON_MODEL;
        }
    }
}
