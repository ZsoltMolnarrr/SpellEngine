package net.spell_engine.fabric.client.compat;

import dev.lambdaurora.lambdynlights.api.DynamicLightHandlers;
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer;
import dev.lambdaurora.lambdynlights.api.item.ItemLightSourceManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.spell_engine.entity.SpellCloud;
import net.spell_engine.entity.SpellProjectile;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

public class DynamicLightsCompatibility implements DynamicLightsInitializer {

    /// One entity type paired with the light level it emits, read from the entity's client-visible state.
    /// Deliberately carries a plain {@link ToIntFunction} rather than LambDynLights'
    /// {@code DynamicLightHandler}, so that {@link #registrations()} — and anything mixing into it — stays
    /// free of any LambDynLights type. That lets dependent mods contribute their own entity light sources
    /// (by injecting into {@code registrations()}) without a hard dependency on LambDynLights: only
    /// {@link #register} touches the LambDynLights API, and it runs solely under the LambDynLights-gated
    /// entrypoint below.
    public record Registration<T extends Entity>(EntityType<T> type, ToIntFunction<T> luminance) { }

    /// The entity light sources Spell Engine contributes to LambDynLights. The returned list is mutable by
    /// design: dependent mods inject at its tail (via mixin) to append their own summons/entities, reusing
    /// this single, already-LambDynLights-gated compat entrypoint instead of registering their own.
    public static List<Registration<?>> registrations() {
        var list = new ArrayList<Registration<?>>();
        list.add(new Registration<>(SpellProjectile.ENTITY_TYPE, entity -> {
            var data = entity.projectileData();
            return (data != null && data.client_data != null) ? data.client_data.light_level : 0;
        }));
        list.add(new Registration<>(SpellCloud.ENTITY_TYPE, entity -> {
            var data = entity.getCloudData();
            return (data != null && data.client_data != null) ? data.client_data.light_level : 0;
        }));
        return list;
    }

    @Override
    public void onInitializeDynamicLights(ItemLightSourceManager itemLightSourceManager) {
        System.out.println("Spell Engine: Initializing Dynamic Lights compatibility...");
        for (var registration : registrations()) {
            register(registration);
        }
    }

    /// Bridges one registration to LambDynLights. Generic so the entity type and its luminance function
    /// share the same captured `T` — a wildcard capture straight off the list iteration would not
    /// type-check when handed to {@code registerDynamicLightHandler}.
    private static <T extends Entity> void register(Registration<T> registration) {
        DynamicLightHandlers.registerDynamicLightHandler(
                registration.type(),
                registration.luminance()::applyAsInt);
    }
}
