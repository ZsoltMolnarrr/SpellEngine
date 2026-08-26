package net.spell_engine.rpg_series.config;

import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ConfigUtil {
    public record Entry(RegistryEntry<EntityAttribute> attribute, EntityAttributeModifier modifier) { }

    private static final Set<String> reportedLegacyIds = new HashSet<>();

    /// Resolves an attribute id string coming from user-editable config or spell data. 1.21.2
    /// dropped the category prefix from every vanilla attribute id (`minecraft:generic.max_health`
    /// → `minecraft:max_health`, `player.block_break_speed` → `block_break_speed`,
    /// `zombie.spawn_reinforcements` → `spawn_reinforcements`), so files written on 1.21.1 carry
    /// ids that no longer exist. Those are mapped here once, for every mod resolving through
    /// Spell Engine, instead of each config format bumping its version.
    public static Optional<RegistryEntry<EntityAttribute>> attribute(String idString) {
        if (idString == null || idString.isEmpty()) { return Optional.empty(); }
        var id = Identifier.tryParse(idString);
        if (id == null) { return Optional.empty(); }
        Optional<RegistryEntry<EntityAttribute>> entry = Registries.ATTRIBUTE.getEntry(id).map(e -> e);
        if (entry.isPresent()) { return entry; }
        var legacy = legacyVanillaId(id);
        if (legacy == null) { return Optional.empty(); }
        entry = Registries.ATTRIBUTE.getEntry(legacy).map(e -> e);
        if (entry.isPresent() && reportedLegacyIds.add(idString)) {
            System.err.println("[Spell Engine] Legacy attribute id `" + idString + "` resolved as `" + legacy + "`, update your config");
        }
        return entry;
    }

    /// The 1.21.2+ form of a pre-1.21.2 vanilla attribute id, `null` if `id` has no legacy prefix.
    @org.jetbrains.annotations.Nullable
    private static Identifier legacyVanillaId(Identifier id) {
        if (!id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) { return null; }
        var path = id.getPath();
        for (var prefix : new String[] { "generic.", "player.", "zombie." }) {
            if (path.startsWith(prefix)) {
                return Identifier.ofVanilla(path.substring(prefix.length()));
            }
        }
        return null;
    }
    public static AttributeModifiersComponent.Builder attributesComponent(Identifier modifierId, List<AttributeModifier> attributesConfig) {
        AttributeModifiersComponent.Builder componentBuilder = AttributeModifiersComponent.builder();
        var modifiers = modifiersFrom(modifierId, attributesConfig);
        for (var modifier : modifiers) {
            componentBuilder.add(modifier.attribute(), modifier.modifier(), AttributeModifierSlot.ANY);
        }
        return componentBuilder;
    }

    public static List<Entry> modifiersFrom(Identifier modifierId, List<AttributeModifier> attributesConfig) {
        var modifiers = new ArrayList<Entry>();
        for (var modifier : attributesConfig) {
            var attribute = attribute(modifier.attribute);
            if (attribute.isPresent()) {
                var id = (modifier.id != null && !modifier.id.isEmpty())
                        ? Identifier.of(modifier.id)
                        : modifierId;
                modifiers.add(new Entry(
                        attribute.get(),
                        new EntityAttributeModifier(
                                id,
                                modifier.value,
                                modifier.operation
                        )
                ));
            } else {
                System.err.println("Failed to resolve EntityAttribute with id: " + modifier.attribute);
            }
        }
        return modifiers;
    }
}
