package net.spell_engine.neoforge;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/// NeoForge-specific default-attribute registration for summoned entities.
///
/// NeoForge cannot register default attributes imperatively — they must be supplied during
/// `EntityAttributeCreationEvent`. Content mods call {@link #buffer} while registering their entity
/// types (the RegisterEvent phase, which precedes the attribute event), so we stash the containers
/// here and {@link #onCreateAttributes flush} them once the event fires.
public class SummonedEntityAttributeRegistrar {

    // Keyed by EntityType, so re-registering an id just overwrites.
    private static final Map<EntityType<? extends LivingEntity>, DefaultAttributeContainer> PENDING = new LinkedHashMap<>();

    /// Stashes a summon's attribute container until the attribute event fires. Called (via
    /// `Platform.Util`) by content mods during entity registration.
    public static void buffer(EntityType<? extends LivingEntity> type, DefaultAttributeContainer.Builder builder) {
        PENDING.put(type, builder.build());
    }

    /// Listener wired on the SpellEngine mod event bus (see NeoForgeMod). Supplies every buffered
    /// summon's attribute container to the engine. Fires once.
    public static void onCreateAttributes(EntityAttributeCreationEvent event) {
        PENDING.forEach(event::put);
        PENDING.clear(); // the event fires once; drop references and guard against a double-put
    }
}
