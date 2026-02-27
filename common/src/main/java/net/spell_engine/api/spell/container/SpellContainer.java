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
        /// The number of additional spells per tier can be bound to this container.
        int extra_tier_binding) {
    public enum ContentType {
        ANY,
        NONE,
        MAGIC,
        ARCHERY,
        CONTAINED,
        TAG;
        public static Codec<ContentType> CODEC = Codec.STRING.xmap(ContentType::valueOf, ContentType::name);
    }

    /// Normalizes a spell ID string to be a valid Minecraft identifier path.
    /// Lowercases the input and strips any character not valid for identifiers.
    public static String normalizeId(String id) {
        return id.toLowerCase().replaceAll("[^a-z0-9/._:-]", "");
    }

    public static final Codec<SpellContainer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ContentType.CODEC.optionalFieldOf("access", ContentType.NONE).forGetter(x -> x.access),
            Codec.STRING.optionalFieldOf("access_param", "").forGetter(x -> x.access_param),
            Codec.STRING.optionalFieldOf("pool", "").forGetter(x -> x.pool),
            Codec.STRING.optionalFieldOf("slot", "").forGetter(x -> x.slot),
            Codec.INT.optionalFieldOf("max_spell_count", 0).forGetter(x -> x.max_spell_count),
            Codec.STRING.listOf().optionalFieldOf("spell_ids", List.of()).forGetter(x -> x.spell_ids),
            Codec.INT.optionalFieldOf("extra_tier_binding", 0).forGetter(x -> x.extra_tier_binding)
    ).apply(instance, SpellContainer::new));

    public static final SpellContainer EMPTY = new SpellContainer(ContentType.NONE, "", "", "", 0, List.of(), 0);

    // Canonical constructor with default values, to avoid null values
    public SpellContainer(ContentType content, String access_tag, String pool, int max_spell_count, List<String> spell_ids) {
        this(content, access_tag, pool, "", max_spell_count, spell_ids, 0);
    }

    public SpellContainer(ContentType content, String access_tag, String pool, String slot, int max_spell_count, List<String> spell_ids) {
        this(content, access_tag, pool, slot, max_spell_count, spell_ids, 0);
    }

    public SpellContainer(ContentType access, String access_param, String pool, String slot, int max_spell_count, List<String> spell_ids, int extra_tier_binding) {
        this.access = access != null ? access : ContentType.NONE;
        this.access_param = access_param;
        this.pool = pool != null ? pool : "";
        this.slot = slot != null ? slot : "";
        this.max_spell_count = max_spell_count;
        this.spell_ids = spell_ids != null ? spell_ids.stream().map(SpellContainer::normalizeId).toList() : List.of();
        this.extra_tier_binding = extra_tier_binding;
    }

    // MARK: Helpers

    public int binding_mutex_count() {
        return Math.max(0, extra_tier_binding + 1);
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
        return new SpellContainer(access, access_param, pool, slot, max_spell_count, new ArrayList<>(spell_ids), extra_tier_binding);
    }

    public SpellContainer copyWith(List<String> spell_ids) {
        return new SpellContainer(access, access_param, pool, slot, max_spell_count, spell_ids, extra_tier_binding);
    }

    public SpellContainer withContentType(ContentType content) {
        return new SpellContainer(content, access_param, pool, slot, max_spell_count, spell_ids, extra_tier_binding);
    }

    public SpellContainer withBindingPool(Identifier poolId) {
        return new SpellContainer(access, access_param, poolId.toString(), slot, max_spell_count, spell_ids, extra_tier_binding);
    }

    public SpellContainer withMaxSpellCount(int maxSpellCount) {
        return new SpellContainer(access, access_param, pool, slot, maxSpellCount, spell_ids, extra_tier_binding);
    }

    public SpellContainer withSpell(String spellId) {
        return new SpellContainer(access, access_param, pool, slot, max_spell_count, List.of(spellId), extra_tier_binding);
    }

    public SpellContainer withSpellId(Identifier spellId) {
        return new SpellContainer(access, access_param, pool, slot, max_spell_count, List.of(spellId.toString()), extra_tier_binding);
    }

    public SpellContainer withSpellIds(List<Identifier> spellIds) {
        var spellIdStrings = spellIds.stream().map(Identifier::toString).toList();
        return new SpellContainer(access, access_param, pool, slot, max_spell_count, spellIdStrings, extra_tier_binding);
    }

    public SpellContainer withAdditionalSpell(List<String> spellIds) {
        var newSpellIds = new ArrayList<>(spell_ids);
        newSpellIds.addAll(spellIds);
        return new SpellContainer(access, access_param, pool, slot, max_spell_count, newSpellIds, extra_tier_binding);
    }
}