package net.spell_engine.api.effect;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.spell_engine.PlatformEvents;
import java.util.HashSet;
import java.util.Set;

public class StatusEffectClassification {
    private static final Set<Holder<Attribute>> movementImpairingAttributes = new HashSet<>();
    private static final Set<ResourceKey<MobEffect>> movementImpairingEffects = new HashSet<>();

    public static void init() {
        movementImpairingAttributes.add(Attributes.MOVEMENT_SPEED);
        movementImpairingAttributes.add(Attributes.FLYING_SPEED);
        movementImpairingAttributes.add(Attributes.GRAVITY);
        PlatformEvents.onServerStarted((server) -> {
            parse(BuiltInRegistries.MOB_EFFECT);
        });
    }

    private static void parse(Registry<MobEffect> registry) {
        registry.listElements().forEach(entry -> {
            var effect = entry.value();
            effect.createModifiers(0, (attribute, modifier) -> {
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
                    if (modifier.amount() < treshold) {
                        isMovementImpairing = true;
                    }
                    if (isMovementImpairing) {
                        movementImpairingEffects.add(entry.unwrapKey().get());
                    }
                }
            });
        });
    }

    public static boolean isMovementImpairing(Holder<MobEffect> effect) {
        var key = effect.unwrapKey();
        if (key.isEmpty()) { // Should never happen, added due to some incompatibility crash
            return false;
        }
        return movementImpairingEffects.contains(key.get());
    }

    public static boolean disablesMobAI(Holder<MobEffect> effectEntry) {
        var effect = effectEntry.value();
        var actionsAllowed = ((ActionImpairing) effect).actionsAllowed();
        if (actionsAllowed == null) {
            return false;
        }
        return !actionsAllowed.mobs().canUseAI();
    }
}
