package net.spell_engine.api.spell.summon;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.Platform;
import net.spell_engine.entity.SummonedEntity;

import java.util.function.Function;

/// Registration helper for summoned-entity base attributes, centralizing the logic content mods used to
/// duplicate per entity.
public class SummonedEntities {

    /// Registers the default attribute container for a summoned entity type, resolving its base values
    /// from a mod-supplied {@code source}.
    ///
    /// The source is an opaque `Function<Identifier, SummonedEntityConfig.Entry>`: SpellEngine neither
    /// knows nor cares whether it is backed by a config file the mod owns or by an inline constant. This
    /// is the decoupling seam — persistence, defaults, and (independent) config versioning all stay with
    /// the mod, behind the function. SpellEngine only *reads* the resolved entry; it never writes, saves,
    /// or holds config state of its own.
    ///
    /// The caller passes the {@link EntityType} it just built, so there is no registry lookup and no
    /// ordering requirement: register the attributes right where the type is created.
    public static void registerAttributes(Identifier entityId,
                                          EntityType<? extends LivingEntity> type,
                                          Function<Identifier, SummonedEntityConfig.Entry> source) {
        var entry = source.apply(entityId);
        if (entry == null) {
            throw new IllegalStateException("No summoned-entity attribute config found for " + entityId
                    + " (its attribute source returned null)");
        }
        // The actual default-attribute registration is platform-specific (kept out of `common` so it
        // carries no loader-specific dependency): Fabric registers imperatively, NeoForge via its event.
        Platform.util().registerSummonedEntityAttributes(type, SummonedEntity.createAttributes(entry));
    }
}
