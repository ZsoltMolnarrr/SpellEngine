package net.spell_engine.api.spell.container;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record SpellContainer(
        /// Defines what spells this container can resolve for spell casting.
        /// (Serves to avoid using archery spells in melee weapons, etc.)
        ContentType access,
        /// Additional parameter for access type. Currently supported:
        /// - `access` with the value of: `TAG` - this would be the tag ID to use
        String access_param,
        /// Pool (spell tag) of spells this container can be assigned to.
        /// For example: `#wizards:fire`
        String pool,
        /// Restrict the container to provide its access only when equipped in a specified slot.
        /// For example: `off_hand` for shield specific spells
        /// If absent, no restriction is applied.
        String slot,
        /// Maximum number of spells that can be assigned to this container.
        int max_spell_count,
        /// List of spells by ID, this container currently holds.
        List<String> spell_ids,
        /// Allow multiple spells from the same tier to be assigned to this container.
        boolean same_tier_binding) {
    public enum ContentType {
        ANY,
        NONE,
        MAGIC,
        ARCHERY,
        CONTAINED,
        TAG;
        public static Codec<ContentType> CODEC = Codec.STRING.xmap(ContentType::valueOf, ContentType::name);
    }

    public static final Codec<SpellContainer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ContentType.CODEC.optionalFieldOf("access", ContentType.NONE).forGetter(x -> x.access),
            Codec.STRING.optionalFieldOf("access_param", "").forGetter(x -> x.access_param),
            Codec.STRING.optionalFieldOf("pool", "").forGetter(x -> x.pool),
            Codec.STRING.optionalFieldOf("slot", "").forGetter(x -> x.slot),
            Codec.INT.optionalFieldOf("max_spell_count", 0).forGetter(x -> x.max_spell_count),
            Codec.STRING.listOf().optionalFieldOf("spell_ids", List.of()).forGetter(x -> x.spell_ids),
            Codec.BOOL.optionalFieldOf("same_tier_binding", false).forGetter(x -> x.same_tier_binding)
    ).apply(instance, SpellContainer::new));

    public static final SpellContainer EMPTY = new SpellContainer(ContentType.NONE, "", "", "", 0, List.of(), false);

    // Canonical constructor with default values, to avoid null values
    public SpellContainer(ContentType content, String access_tag, String pool, int max_spell_count, List<String> spell_ids) {
        this(content, access_tag, pool, "", max_spell_count, spell_ids, false);
    }

    public SpellContainer(ContentType content, String access_tag, String pool, String slot, int max_spell_count, List<String> spell_ids) {
        this(content, access_tag, pool, slot, max_spell_count, spell_ids, false);
    }

    public SpellContainer(ContentType access, String access_param, String pool, String slot, int max_spell_count, List<String> spell_ids, boolean same_tier_binding) {
        this.access = access != null ? access : ContentType.NONE;
        this.access_param = access_param;
        this.pool = pool != null ? pool : "";
        this.slot = slot != null ? slot : "";
        this.max_spell_count = max_spell_count;
        this.spell_ids = spell_ids != null ? spell_ids : List.of();
        this.same_tier_binding = same_tier_binding;
    }

    // MARK: Helpers

    public boolean binding_mutex() {
        return !same_tier_binding;
    }

    public boolean isResolver() {
        if (access == null) {
            return false;
        }
        switch (access) {
            case NONE -> { return false; }
            case TAG -> { return !access_param.isEmpty(); }
            default -> { return true; }
        }
    }

    public boolean isValid() {
        if (isResolver()) {
            return true;
        }
        if (max_spell_count < 0) {
            return false;
        }
        return !spell_ids.isEmpty() || (pool != null && !pool.isEmpty());
    }

    public boolean contains(Identifier spellId) {
        return spell_ids.contains(spellId.toString());
    }

    public boolean slotMatches(@Nullable String other) {
        return other == null || this.slot().contains(other);
    }

    public boolean isUsable() {
        return isValid() && !spell_ids.isEmpty();
    }

    public SpellContainer copy() {
        return new SpellContainer(access, access_param, pool, slot, max_spell_count, new ArrayList<>(spell_ids), same_tier_binding);
    }

    public SpellContainer copyWith(List<String> spell_ids) {
        return new SpellContainer(access, access_param, pool, slot, max_spell_count, spell_ids, same_tier_binding);
    }

    public SpellContainer withContentType(ContentType content) {
        return new SpellContainer(content, access_param, pool, slot, max_spell_count, spell_ids, same_tier_binding);
    }

    public SpellContainer withBindingPool(Identifier poolId) {
        return new SpellContainer(access, access_param, poolId.toString(), slot, max_spell_count, spell_ids, same_tier_binding);
    }

    public SpellContainer withMaxSpellCount(int maxSpellCount) {
        return new SpellContainer(access, access_param, pool, slot, maxSpellCount, spell_ids, same_tier_binding);
    }

    public SpellContainer withSpell(String spellId) {
        return new SpellContainer(access, access_param, pool, slot, max_spell_count, List.of(spellId), same_tier_binding);
    }

    public SpellContainer withSpellId(Identifier spellId) {
        return new SpellContainer(access, access_param, pool, slot, max_spell_count, List.of(spellId.toString()), same_tier_binding);
    }

    public SpellContainer withSpellIds(List<Identifier> spellIds) {
        var spellIdStrings = spellIds.stream().map(Identifier::toString).toList();
        return new SpellContainer(access, access_param, pool, slot, max_spell_count, spellIdStrings, same_tier_binding);
    }

    public SpellContainer withAdditionalSpell(List<String> spellIds) {
        var newSpellIds = new ArrayList<>(spell_ids);
        newSpellIds.addAll(spellIds);
        return new SpellContainer(access, access_param, pool, slot, max_spell_count, newSpellIds, same_tier_binding);
    }
}