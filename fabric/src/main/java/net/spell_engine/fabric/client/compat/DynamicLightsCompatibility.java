package net.spell_engine.fabric.client.compat;

import dev.lambdaurora.lambdynlights.api.DynamicLightHandler;
import dev.lambdaurora.lambdynlights.api.DynamicLightHandlers;
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.spell_engine.entity.SpellCloud;
import net.spell_engine.entity.SpellProjectile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

public class DynamicLightsCompatibility implements DynamicLightsInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("SpellEngine/DynamicLightsCompat");

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

    /// LambDynamicLights entrypoint — declared under the `dynamiclights` key in `fabric.mod.json`.
    ///
    /// Compiled against LambDynamicLights `2.3.2+1.20.1`, whose `DynamicLightsInitializer` declares this
    /// method with **no** argument (the `ItemLightSourceManager` / `DynamicLightsContext` overloads are
    /// later shapes). This is also the newest 1.20.1 release whose Modrinth jar carries the API classes at
    /// the top level: LDL 4.4.0+1.20.1 moves them into a nested `lambdynamiclights-api` jar that Loom does
    /// not put on the compile classpath. That costs nothing — LDL 4.4.0 keeps the no-arg method abstract,
    /// keeps `DynamicLightHandlers.registerDynamicLightHandler(EntityType, DynamicLightHandler)` and
    /// `DynamicLightHandler#getLuminance` byte-compatible, and invokes **both** the `lambdynlights:initializer`
    /// and the `dynamiclights` entrypoint keys — so this single key initialises on 2.3.x and 4.x alike, and
    /// declaring the second key too would only double-register on 4.x.
    @Override
    public void onInitializeDynamicLights() {
        var registrations = registrations();
        LOGGER.info("LambDynamicLights detected, registering {} entity light source(s)", registrations.size());
        for (var registration : registrations) {
            register(registration);
        }
    }

    /// Bridges one registration to LambDynLights. Generic so the entity type and its luminance function
    /// share the same captured `T` — a wildcard capture straight off the list iteration would not
    /// type-check when handed to {@code registerDynamicLightHandler}.
    private static <T extends Entity> void register(Registration<T> registration) {
        DynamicLightHandlers.registerDynamicLightHandler(
                registration.type(),
                (DynamicLightHandler<T>) registration.luminance()::applyAsInt);
    }
}
