package net.spell_engine.api.spell.tooltip;

import com.ibm.icu.text.DecimalFormat;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/// Canonical, dependency-free definitions of the description tokens the spell tooltip understands,
/// plus the value-formatting primitives used to render them.
///
/// This class intentionally avoids client-only references (no `MinecraftClient`, `I18n`, `KeyBinding`
/// …), so it is safe to touch from spell data definitions that run on both client and server. It only
/// depends on shared Minecraft types (`Identifier`, `EntityAttributeModifier.Operation`) and ICU's
/// number formatter, all present on a dedicated server. The client renderer
/// (`net.spell_engine.client.gui.SpellTooltip`) resolves these tokens into concrete numbers; the data
/// side only needs the token *builders* here to embed placeholders into a description string.
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

    /// Prefix of the parametric effect-value token: `{effect|<effect_id>|<amplifier>|<attribute>|<format>}`.
    /// Defined once so the builder and the renderer's collector agree on the keyword.
    public static final String effectToken = "effect";

    /// Separator between an effect token's fields. Ids use `:` `/` `.` `-` but never `|` or `}`,
    /// so this is unambiguous. Defined once and shared by the builder and the renderer's parser.
    public static final String effectTokenSeparator = "|";

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

    // MARK: Effect value tokens

    /// How an effect modifier's numeric value is formatted for display. Percentage-vs-flat is *not*
    /// a choice here — it is derived from the modifier's `Operation` by [#bonus]. This flag only
    /// controls sign handling, the one thing the operation can't tell us.
    public enum Format {
        /// The raw modifier value as-is: a negative gets a `-`, a positive gets no prefix — e.g.
        /// `-25%`, `10%`, `3`.
        SIGNED(""),
        /// The absolute value — for "reduce ... by 30%" phrasing where the stored value is negative.
        ABS("abs"),
        /// Always shows an explicit sign, positive included, e.g. `+20%`, `-20%`, `+3`.
        FORCE_SIGN("+");

        /// Text this format serialises to in the token's trailing field. `SIGNED` (the default)
        /// serialises to nothing, so the canonical token simply omits the field.
        public final String keyword;
        Format(String keyword) { this.keyword = keyword; }

        /// Inverse of [#keyword]: maps a token's format field back to a `Format`. A blank or unknown
        /// keyword falls back to `SIGNED`, matching the omitted-field default.
        public static Format parse(@Nullable String keyword) {
            if (keyword != null && !keyword.isEmpty()) {
                for (var format : values()) {
                    if (format.keyword.equals(keyword)) {
                        return format;
                    }
                }
            }
            return SIGNED;
        }

        /// Renders a resolved modifier value: applies this format's sign handling, then the
        /// operation-aware [#bonus] formatting (percent for multiplied operations, flat otherwise).
        public String render(float value, EntityAttributeModifier.Operation operation) {
            var magnitude = (this == ABS) ? Math.abs(value) : value;
            var formatted = bonus(magnitude, operation);
            // `bonus` already prefixes negatives with '-'; only a non-negative needs a forced '+'.
            if (this == FORCE_SIGN && magnitude >= 0 && !formatted.isEmpty()) {
                formatted = "+" + formatted;
            }
            return formatted;
        }
    }

    /// Builds `{effect|<effect_id>}` — the effect's sole attribute modifier at amplifier 0, signed.
    public static String effect(Identifier effectId) {
        return effect(effectId, 0, null, Format.SIGNED);
    }

    /// Builds `{effect|<effect_id>|<amplifier>}` — the sole modifier at the given amplifier, signed.
    public static String effect(Identifier effectId, int amplifier) {
        return effect(effectId, amplifier, null, Format.SIGNED);
    }

    /// Builds `{effect|<effect_id>|<amplifier>|<attribute>}` — a specific modifier, signed.
    public static String effect(Identifier effectId, int amplifier, Identifier attribute) {
        return effect(effectId, amplifier, attribute, Format.SIGNED);
    }

    /// Builds the effect-value token in *canonical minimal* form: trailing fields that hold their
    /// default (amplifier `0`, no explicit attribute, `SIGNED` format) are dropped, but a defaulted
    /// field is still emitted when a later field is non-default, to keep the positions aligned.
    ///
    /// Producing one deterministic string per meaning is what lets the renderer memoise resolution
    /// keyed on the raw token text — semantically-equal tokens are byte-equal.
    ///
    /// @param attribute which of the effect's modifiers to read; `null` means "the sole modifier"
    ///                  (deterministic only when the effect has exactly one).
    public static String effect(Identifier effectId, int amplifier, @Nullable Identifier attribute, Format format) {
        boolean hasFormat = format != Format.SIGNED;
        boolean hasAttribute = attribute != null;
        boolean hasAmplifier = amplifier != 0;

        var body = new StringBuilder(effectId.toString());
        if (hasFormat || hasAttribute || hasAmplifier) {
            body.append(effectTokenSeparator).append(amplifier);
        }
        if (hasFormat || hasAttribute) {
            body.append(effectTokenSeparator);
            if (attribute != null) {
                body.append(attribute);
            }
        }
        if (hasFormat) {
            body.append(effectTokenSeparator).append(format.keyword);
        }
        return placeholder(effectToken + effectTokenSeparator + body);
    }

    // MARK: Value formatting primitives

    // DecimalFormat is expensive to construct (loads locale/symbol data) and formattedNumber
    // is called many times per tooltip frame. Reuse one per thread (tooltips may be built off-thread).
    private static final ThreadLocal<DecimalFormat> NUMBER_FORMAT = ThreadLocal.withInitial(() -> {
        var formatter = new DecimalFormat();
        formatter.setMaximumFractionDigits(1);
        return formatter;
    });

    public static String percent(float chance) {
        return String.valueOf((int) (chance * 100)) + '%';
    }

    public static String formattedNumber(float number) {
        return NUMBER_FORMAT.get().format(number);
    }

    /// Operation-aware value formatting: flat number for `ADD_VALUE`, percentage for the multiplied
    /// operations (`ADD_MULTIPLIED_TOTAL` is offset by 1 so a total-multiplier of 1.1 reads "10%").
    public static String bonus(float amount, EntityAttributeModifier.Operation operation) {
        switch (operation) {
            case ADD_VALUE -> {
                return formattedNumber(amount);
            }
            case ADD_MULTIPLIED_BASE -> {
                return percent(amount);
            }
            case ADD_MULTIPLIED_TOTAL -> {
                return percent(amount - 1F);
            }
        }
        return "";
    }
}
