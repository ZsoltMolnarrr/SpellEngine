package net.spell_engine.api.spell.tooltip;

/// Canonical, dependency-free definitions of the description tokens the spell tooltip understands.
///
/// This class intentionally references nothing else (no client, no Minecraft, no SpellEngine types),
/// so it is safe to touch from spell data definitions that run on both client and server. The client
/// renderer (`net.spell_engine.client.gui.SpellTooltip`) resolves these tokens into concrete numbers;
/// the data side only needs the token *names* (and, going forward, the builder helpers here) to embed
/// placeholders into a description string.
///
/// Referencing the client-only `SpellTooltip` from a data definition would load client classes on a
/// dedicated server and crash it — hence the split.
public final class TooltipTokens {
    private TooltipTokens() {}

    public static final String damageToken = "damage";
    public static final String healToken = "heal";
    public static final String rangeToken = "range";
    public static final String durationToken = "duration";
    public static final String itemToken = "item";
    public static final String effectDurationToken = "effect_duration";
    public static final String effectAmplifierToken = "effect_amplifier";
    public static final String effectAmplifierCapToken = "effect_amplifier_cap";
    public static final String impactRangeToken = "impact_range";
    public static final String teleportDistanceToken = "teleport_distance";
    public static final String countToken = "count";
    public static final String impact_chance = "impact_chance";
    public static final String trigger_chance = "trigger_chance";
    public static final String trigger_list = "trigger_list";
    public static final String additional_placement_count = "additional_placement_count";
    public static final String summonDurationToken = "summon_duration";
    public static final String summonCountToken = "summon_count";
    public static final String summonGroupCountToken = "summon_group_count";

    /// Wraps a token name in the `{...}` delimiters the renderer scans for.
    public static String placeholder(String token) {
        return "{" + token + "}";
    }

    /// Placeholder for the value at `index` (0-based) of a token that carries `count` values.
    /// A single value uses the bare `{token}`; multiple values are disambiguated with a 1-based
    /// suffix: `{token_1}`, `{token_2}`, … This mirrors how the renderer replaces indexed tokens.
    public static String placeholder(String token, int index, int count) {
        return count > 1 ? placeholder(token + "_" + (index + 1)) : placeholder(token);
    }
}
