package net.spell_engine.api.spell.summon;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.entity.SummonedEntity;

/// Registration helper for summoned-entity base attributes, centralizing the logic content mods used to
/// duplicate per entity.
public class SummonedEntities {

    /// Registers the default attribute container for a summoned entity type, sourcing its base values from
    /// the central {@link SummonedEntityConfig} (seeding `defaults` into the config if the entity has no
    /// entry yet). Keyed by the raw entity {@link Identifier} — the {@link EntityType} is resolved from the
    /// registry here, because it is created at a later stage and isn't available synchronously at the call
    /// site. Call this AFTER the entity type has been registered.
    @SuppressWarnings("unchecked")
    public static void registerAttributes(Identifier entityId, SummonedEntityConfig.Entry defaults) {
        var entry = SpellEngineMod.summonedEntityConfig.value.entries.computeIfAbsent(entityId.toString(), k -> defaults);
        // getOrEmpty, not get: a defaulted registry returns minecraft:pig for a missing id; getOrEmpty is
        // empty if the type isn't registered yet (then we simply skip — the caller invoked us too early).
        Registries.ENTITY_TYPE.getOrEmpty(entityId).ifPresent(type ->
                FabricDefaultAttributeRegistry.register((EntityType<? extends LivingEntity>) type,
                        SummonedEntity.createAttributes(entry).build()));
        SpellEngineMod.summonedEntityConfig.save(); // persist merged-in defaults
    }
}
