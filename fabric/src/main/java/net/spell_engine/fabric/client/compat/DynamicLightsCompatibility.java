package net.spell_engine.fabric.client.compat;

import com.mojang.serialization.MapCodec;
import dev.lambdaurora.lambdynlights.api.DynamicLightsContext;
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer;
import dev.lambdaurora.lambdynlights.api.entity.luminance.EntityLuminance;
import dev.lambdaurora.lambdynlights.api.item.ItemLightSourceManager;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.entity.SpellCloud;
import net.spell_engine.entity.SpellProjectile;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

public class DynamicLightsCompatibility implements DynamicLightsInitializer {

    /// One entity type paired with the light level it emits, read from the entity's client-visible state.
    /// Deliberately carries a plain {@link ToIntFunction} rather than a LambDynLights type, so that
    /// {@link #registrations()} — and anything mixing into it — stays free of any LambDynLights type.
    public record Registration<T extends Entity>(EntityType<T> type, ToIntFunction<T> luminance) { }

    /// The entity light sources Spell Engine contributes to LambDynLights. The returned list is mutable by
    /// design: dependent mods inject at its tail (via mixin) to append their own summons/entities.
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
    public void onInitializeDynamicLights(DynamicLightsContext context) {
        SpellEngineMod.LOGGER.info("Initializing Dynamic Lights compatibility");
        // LambDynamicLights 4.x: entity light sources are (re)registered on every resource reload
        context.entityLightSourceManager().onRegisterEvent().register(Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "entity_light_sources"), registerContext -> {
            for (var registration : registrations()) {
                register(registerContext, registration);
            }
        });
    }

    private static <T extends Entity> void register(dev.lambdaurora.lambdynlights.api.entity.EntityLightSourceManager.RegisterContext context, Registration<T> registration) {
        context.register(registration.type(), new DynamicLuminance<>(registration));
    }

    /// Code-defined luminance (no data pack codec needed; the type only exists to satisfy the API)
    private record DynamicLuminance<T extends Entity>(Registration<T> registration) implements EntityLuminance {
        private static final Type TYPE = new Type(Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "dynamic"), MapCodec.unit(new DynamicLuminance<>(null)));

        @Override
        public Type type() {
            return TYPE;
        }

        @Override
        @SuppressWarnings("unchecked")
        public int getLuminance(ItemLightSourceManager itemLightSourceManager, Entity entity) {
            if (registration == null || !registration.type().equals(entity.getType())) {
                return 0;
            }
            return registration.luminance().applyAsInt((T) entity);
        }
    }
}
