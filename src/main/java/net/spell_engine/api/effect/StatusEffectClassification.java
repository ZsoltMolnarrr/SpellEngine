package net.spell_engine.api.effect;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.HashSet;
import java.util.Set;

public class StatusEffectClassification {
    private static final Set<RegistryEntry<EntityAttribute>> movementImpairingAttributes = new HashSet<>();
    private static final Set<RegistryKey<StatusEffect>> movementImpairingEffects = new HashSet<>();

    public static void init() {
        movementImpairingAttributes.add(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        movementImpairingAttributes.add(EntityAttributes.GENERIC_FLYING_SPEED);
        movementImpairingAttributes.add(EntityAttributes.GENERIC_GRAVITY);
        ServerLifecycleEvents.SERVER_STARTED.register((server) -> {
            parse(Registries.STATUS_EFFECT);
        });
    }

    private static void parse(Registry<StatusEffect> registry) {
        registry.streamEntries().forEach(entry -> {
            var effect = entry.value();
            effect.forEachAttributeModifier(0, (attribute, modifier) -> {
                if (movementImpairingAttributes.contains(attribute)) {
                    var isMovementImpairing = false;
                    double treshold = 0;
                    switch (modifier.operation()) {
                        case ADD_VALUE, ADD_MULTIPLIED_BASE -> {
                            treshold = 0;
                        }
                        case ADD_MULTIPLIED_TOTAL -> {
                            treshold = 1;
                        }
                    }
                    if (modifier.value() < treshold) {
                        isMovementImpairing = true;
                    }
                    if (isMovementImpairing) {
                        movementImpairingEffects.add(entry.getKey().get());
                    }
                }
            });
        });
    }

    public static boolean isMovementImpairing(RegistryEntry<StatusEffect> effect) {
        var key = effect.getKey();
        if (key.isEmpty()) { // Should never happen, added due to some incompatibility crash
            return false;
        }
        return movementImpairingEffects.contains(key.get());
    }

    public static boolean disablesMobAI(RegistryEntry<StatusEffect> effectEntry) {
        var effect = effectEntry.value();
        var actionsAllowed = ((ActionImpairing) effect).actionsAllowed();
        if (actionsAllowed == null) {
            return false;
        }
        return !actionsAllowed.mobs().canUseAI();
    }
}
