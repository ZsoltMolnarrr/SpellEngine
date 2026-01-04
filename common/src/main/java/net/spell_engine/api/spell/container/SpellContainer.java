package net.spell_engine.api.spell.container;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record SpellContainer(
        /// Type of content this container, needs to be matched with the container
        /// that is allowing the spell casting
        /// (Serves to avoid using archery spells in melee weapons, etc.)
        ContentType content,
        /// If set to true, this container allows container resolution
        /// all spells the player has access to, will be available to cast
        boolean is_proxy,
        /// Pool (spell tag) of spells this container can be assigned to.
        /// For example: `#wizards:fire`
        String pool,
        /// Restrict the container to provide its content only when equipped in a specified slot.
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
        ANY, MAGIC, ARCHERY;
        public static Codec<ContentType> CODEC = Codec.STRING.xmap(ContentType::valueOf, ContentType::name);
    }

    public static final Codec<SpellContainer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ContentType.CODEC.optionalFieldOf("content", ContentType.MAGIC).forGetter(x -> x.content),
            Codec.BOOL.optionalFieldOf("is_proxy", false).forGetter(x -> x.is_proxy),
            Codec.STRING.optionalFieldOf("pool", "").forGetter(x -> x.pool),
            Codec.STRING.optionalFieldOf("slot", "").forGetter(x -> x.slot),
            Codec.INT.optionalFieldOf("max_spell_count", 0).forGetter(x -> x.max_spell_count),
            Codec.STRING.listOf().optionalFieldOf("spell_ids", List.of()).forGetter(x -> x.spell_ids),
            Codec.BOOL.optionalFieldOf("same_tier_binding", false).forGetter(x -> x.same_tier_binding)
    ).apply(instance, SpellContainer::new));

    public static final SpellContainer EMPTY = new SpellContainer(ContentType.MAGIC, false, "", "", 0, List.of(), true);

    // Canonical constructor with default values, to avoid null values
    public SpellContainer(ContentType content, boolean is_proxy, String pool, int max_spell_count, List<String> spell_ids) {
        this(content, is_proxy, pool, "", max_spell_count, spell_ids, false);
    }

    public SpellContainer(ContentType content, boolean is_proxy, String pool, String slot, int max_spell_count, List<String> spell_ids) {
        this(content, is_proxy, pool, slot, max_spell_count, spell_ids, false);
    }

    public SpellContainer(ContentType content, boolean is_proxy, String pool, String slot, int max_spell_count, List<String> spell_ids, boolean same_tier_binding) {
        this.content = content != null ? content : ContentType.MAGIC;
        this.is_proxy = is_proxy;
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

    public boolean isValid() {
        if (is_proxy) {
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

    public boolean contentMatches(@Nullable SpellContainer.ContentType other) {
        return other == null || this.content() == SpellContainer.ContentType.ANY || this.content() == other;
    }

    public boolean slotMatches(@Nullable String other) {
        return other == null || this.slot().contains(other);
    }

    public boolean isUsable() {
        return isValid() && !spell_ids.isEmpty();
    }

    public SpellContainer copy() {
        return new SpellContainer(content, is_proxy, pool, slot, max_spell_count, new ArrayList<>(spell_ids), same_tier_binding);
    }

    public SpellContainer copyWith(List<String> spell_ids) {
        return new SpellContainer(content, is_proxy, pool, slot, max_spell_count, spell_ids, same_tier_binding);
    }

    public SpellContainer withContentType(ContentType content) {
        return new SpellContainer(content, is_proxy, pool, slot, max_spell_count, spell_ids, same_tier_binding);
    }

    public SpellContainer withBindingPool(Identifier poolId) {
        return new SpellContainer(content, is_proxy, poolId.toString(), slot, max_spell_count, spell_ids, same_tier_binding);
    }

    public SpellContainer withMaxSpellCount(int maxSpellCount) {
        return new SpellContainer(content, is_proxy, pool, slot, maxSpellCount, spell_ids, same_tier_binding);
    }

    public SpellContainer withAdditionalSpell(List<String> spellIds) {
        var newSpellIds = new ArrayList<>(spell_ids);
        newSpellIds.addAll(spellIds);
        return new SpellContainer(content, is_proxy, pool, slot, max_spell_count, newSpellIds, same_tier_binding);
    }
}