package net.spell_engine.client.compatibility;
import net.spell_engine.Platform;

import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.config.TriStateAuto;

public class FirstPersonAnimationCompatibility {
    private static boolean isCameraModPresent = false;

    static void initialize() {
        var cameraMods = new String[] {
            "firstperson", "realcamera"
        };
        for (var mod : cameraMods) {
            if (Platform.util().isModLoaded(mod)) {
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
