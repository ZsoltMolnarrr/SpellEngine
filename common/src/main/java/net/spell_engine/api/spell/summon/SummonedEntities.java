package net.spell_engine.api.spell.summon;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.spell_engine.Platform;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.entity.SummonedEntity;

/// Registration helper for summoned-entity base attributes, centralizing the logic content mods used to
/// duplicate per entity.
public class SummonedEntities {

    /// Registers the default attribute container for a summoned entity type, sourcing its base values from
    /// the central {@link SummonedEntityConfig} (seeding `defaults` into the config if the entity has no
    /// entry yet). The caller passes the {@link EntityType} it just built, so there is no registry lookup
    /// and no ordering requirement: register the attributes right where the type is created — the two no
    /// longer have to happen as separately ordered steps. {@code entityId} is only used as the (stable)
    /// config key.
    public static void registerAttributes(Identifier entityId, EntityType<? extends LivingEntity> type, SummonedEntityConfig.Entry defaults) {
        var entry = SpellEngineMod.summonedEntityConfig.value.entries.computeIfAbsent(entityId.toString(), k -> defaults);
        // The actual default-attribute registration is platform-specific (kept out of `common` so it
        // carries no loader-specific dependency): Fabric registers imperatively, NeoForge via its event.
        Platform.util().registerSummonedEntityAttributes(type, SummonedEntity.createAttributes(entry));
        SpellEngineMod.summonedEntityConfig.save(); // persist merged-in defaults
    }
}
