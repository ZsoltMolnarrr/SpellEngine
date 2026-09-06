package net.spell_engine.api.effect;

import net.spell_engine.PlatformEvents;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.HashSet;
import java.util.Set;

public class StatusEffectClassification {
    private static final Set<EntityAttribute> movementImpairingAttributes = new HashSet<>();
    private static final Set<StatusEffect> movementImpairingEffects = new HashSet<>();

    public static void init() {
        movementImpairingAttributes.add(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        movementImpairingAttributes.add(EntityAttributes.GENERIC_FLYING_SPEED);
        // 1.20.1 has no generic gravity attribute
        PlatformEvents.onServerStarted((server) -> {
            parse(Registries.STATUS_EFFECT);
        });
    }

    private static void parse(Registry<StatusEffect> registry) {
        for (var effect : registry) {
            for (var modifierEntry : effect.getAttributeModifiers().entrySet()) {
                var attribute = modifierEntry.getKey();
                var modifier = modifierEntry.getValue();
                if (movementImpairingAttributes.contains(attribute)) {
                    var isMovementImpairing = false;
                    double treshold = 0;
                    switch (modifier.getOperation()) {
                        case ADDITION, MULTIPLY_BASE -> {
                            treshold = 0;
                        }
                        case MULTIPLY_TOTAL -> {
                            treshold = 1;
                        }
                    }
                    if (modifier.getValue() < treshold) {
                        isMovementImpairing = true;
                    }
                    if (isMovementImpairing) {
                        movementImpairingEffects.add(effect);
                    }
                }
            }
        }
    }

    public static boolean isMovementImpairing(StatusEffect effect) {
        return movementImpairingEffects.contains(effect);
    }

    public static boolean isMovementImpairing(RegistryEntry<StatusEffect> effect) {
        return isMovementImpairing(effect.value());
    }

    public static boolean disablesMobAI(StatusEffect effect) {
        var actionsAllowed = ((ActionImpairing) effect).actionsAllowed();
        if (actionsAllowed == null) {
            return false;
        }
        return !actionsAllowed.mobs().canUseAI();
    }

    public static boolean disablesMobAI(RegistryEntry<StatusEffect> effectEntry) {
        return disablesMobAI(effectEntry.value());
    }
}
