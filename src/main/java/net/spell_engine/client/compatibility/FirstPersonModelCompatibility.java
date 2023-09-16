package net.spell_engine.client.compatibility;

import dev.tr7zw.firstperson.FirstPersonModelCore;

import java.util.function.Supplier;

public class FirstPersonModelCompatibility {
    public static boolean isActive() {
        return isActive.get();
    }

    private static Supplier<Boolean> loaded = () -> {
        boolean result;
        try {
            Class.forName("dev.tr7zw.firstperson.FirstPersonModelCore").getName();
            result = true;
        } catch(ClassNotFoundException e) {
            result = false;
        }

        boolean finalResult = result;
        loaded = () -> { return finalResult; };

        return result;
    };

    private static Supplier<Boolean> isActive = () -> {
        if (loaded.get()) {
            return FirstPersonModelCore.enabled;
        }
        return false;
    };

    /**
     * Checks if a class exists or not
     * @param name
     * @return
     */
    protected static boolean doesClassExist(String name) {
        try {
            if(Class.forName(name) != null) {
                return true;
            }
        } catch (ClassNotFoundException e) {}
        return false;
    }

}
