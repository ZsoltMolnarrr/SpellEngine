package net.spell_engine.client.gui;

import net.spell_engine.mixin.client.control.KeybindingAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.world.World;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.container.SpellChoice;
import net.spell_engine.api.spell.tooltip.TooltipTokens;
import static net.spell_engine.api.spell.tooltip.TooltipTokens.*;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.api.tags.SpellEngineItemTags;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.input.Keybindings;
import net.spell_engine.internals.cost.Ammo;
import net.spell_engine.internals.SpellModifiers;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import net.spell_engine.spellbinding.spellchoice.SpellChoices;
import net.spell_power.api.SpellPower;
import net.spell_power.api.SpellSchool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.spell_engine.internals.impact.SpellEstimation;
import net.spell_engine.internals.SpellParameters;

public class SpellTooltip {
    // Token names live in the dependency-free `TooltipTokens`, statically imported above so they can
    // be referenced unqualified here. This delegate preserves the `SpellTooltip.placeholder(...)` API.
    public static String placeholder(String token) { return TooltipTokens.placeholder(token); }

    // Constant token-start matcher; compiling per-tooltip-frame was a needless cost.
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{[a-z]{3}");

    // Parametric effect-value token: `{effect|<effect_id>|<amplifier>|<attribute>|<format>}`. The
    // prefix doubles as a cheap `String.contains` gate so the regex only runs for descriptions that
    // actually carry one. Built from the shared TooltipTokens constants so both sides agree.
    private static final String EFFECT_TOKEN_PREFIX =
            "{" + TooltipTokens.effectToken + TooltipTokens.effectTokenSeparator; // "{effect|"
    private static final Pattern EFFECT_TOKEN_PATTERN =
            Pattern.compile(Pattern.quote(EFFECT_TOKEN_PREFIX) + "([^}]*)}");

    public static void addSpellLines(ItemStack itemStack, TooltipType tooltipType, List<Text> lines) {
        var player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }
        int addSectionDivider = 0;
        var config = SpellEngineClient.config;
        var spellTextLines = new ArrayList<Text>();

        var choices = SpellChoices.from(itemStack);
        var container = SpellContainerHelper.containerFromItemStack(itemStack);
        if (container != null && container.isValid()) {
            if (container.isResolver() && config.showSpellBookSuppportTooltip) {
                switch (container.access()) {
                    case ANY -> {
                        spellTextLines.add(Text.translatable("spell.tooltip.container.access.any")
                                .formatted(Formatting.GRAY));
                    }
                    case MAGIC -> {
                        spellTextLines.add(Text.translatable("spell.tooltip.container.access.spell")
                                .formatted(Formatting.GRAY));
                    }
                    case ARCHERY -> {
                        spellTextLines.add(Text.translatable("spell.tooltip.container.access.archery")
                                .formatted(Formatting.GRAY));
                    }
                    case TAG -> {
                        if (!container.access_param().isBlank()) {
                            var id = Identifier.tryParse(container.access_param());
                            if (id != null) {
                                var key = "spell.tooltip.container.access.tag." + id.getNamespace() + "." + id.getPath();
                                if (Language.getInstance().hasTranslation(key)) {
                                    spellTextLines.add(Text.translatable(key)
                                            .formatted(Formatting.GRAY));
                                }
                            }
                        }
                    }
                }
                addSectionDivider += 1;
            }
        }
        if (choices != null) {
            var spellInfo = getSpellChoiceInfo(choices, player);
            spellTextLines.addAll(spellInfo.content());
            addSectionDivider += spellInfo.sectionDividersAdded();
        }
        if (container != null && container.isValid()) {
            var spellInfo = getSpellInfoExpandedWithKey(itemStack, container, player, false, true);
            spellTextLines.addAll(spellInfo.content());
            addSectionDivider += spellInfo.sectionDividersAdded();
        }

        if (spellTextLines.isEmpty()) {
            return;
        }
        var found = 0;
        if (tooltipType.isAdvanced()) {
            var searchedStyle = Text.literal("x")
                    .formatted(Formatting.DARK_GRAY)
                    .getStyle(); // From: ItemStack.java, advanced tooltip section
            var reverseIndex = lines.size();
            for (var line : lines.reversed()) {
                --reverseIndex;
                var style = line.getStyle();
                if (style != null) {
                    var newFind = searchedStyle.getColor().equals(style.getColor());
                    if (found != 0 && !newFind) {
                        break;
                    } else {
                        found = reverseIndex;
                    }
                }
            }
        }
        if (found <= 0) {
            if (addSectionDivider > 0) {
                spellTextLines.addFirst(Text.literal(""));
            }
            lines.addAll(spellTextLines);
        } else {
            if (addSectionDivider > 0) {
                boolean hasPreceedignEmptyLine = false;
                if (found > 0) {
                    var previousLine = lines.get(found - 1).getString();
                    hasPreceedignEmptyLine = previousLine.isBlank();
                }
                if (!hasPreceedignEmptyLine) {
                    spellTextLines.addFirst(Text.literal(""));
                }
            }
            lines.addAll(found, spellTextLines);
        }
    }

    public static @NotNull SpellTooltip.SpellInfo getSpellInfoExpandedWithKey(
            ItemStack itemStack, SpellContainer container, PlayerEntity player,
            boolean forceHideHeader, boolean allowDetailsHint) {
        var config = SpellEngineClient.config;
        var keybinding = Keybindings.bypass_spell_hotbar;
        var showDetails = config.alwaysShowFullTooltip
                || (!keybinding.isUnbound() && isKeyPressed(keybinding)
        );
        var spellInfo = getSpellInfo(itemStack, container, player, forceHideHeader, showDetails);
        if (!showDetails) {
            if (config.showSpellBindingTooltip
                    && SpellChoices.from(itemStack) == null
                    && container.pool() != null && !container.pool().isEmpty()
                    && container.spell_ids().isEmpty()) {
                spellInfo.content().add(Text.translatable("spell.tooltip.spell_binding_tip")
                        .formatted(Formatting.GRAY));
            }
        }
        if (allowDetailsHint && !showDetails) {
            if (!keybinding.isUnbound() && container.spell_ids().size() > 0) {
                spellInfo.content().add(Text.translatable("spell.tooltip.hold_for_details",
                                keybinding.getBoundKeyLocalizedText())
                        .formatted(Formatting.DARK_GRAY));
            }
        }
        return spellInfo;
    }

    public record SpellInfo(List<Text> content, int sectionDividersAdded) {  }

    public static @NotNull SpellInfo getSpellChoiceInfo(SpellChoice container, PlayerEntity player) {
        // Get world reference
        final var world = player.getWorld();
        if (world == null) {
            return new SpellInfo(List.of(), 0);
        }

        // Validate pool
        var pool = container.pool();
        if (pool == null || pool.isEmpty()) {
            return new SpellInfo(List.of(), 0);
        }

        // Resolve all spells from the pool tag
        List<RegistryEntry<Spell>> spells = SpellRegistry.entries(world, pool);
        if (spells.isEmpty()) {
            return new SpellInfo(List.of(), 0);
        }
        var archetype = spells.getFirst().value().school.archetype;

        // Sort spells by tier then alphabetically
        HashMap<Identifier, Spell> spellMap = new HashMap<>();
        for (var entry : spells) {
            var id = entry.getKey().get().getValue();
            spellMap.put(id, entry.value());
        }

        List<Identifier> sortedSpellIds = spellMap.entrySet().stream()
            .sorted(SpellContainerHelper.containerSorter)
            .map(entry -> entry.getKey())
            .collect(Collectors.toList());

        // Build tooltip access
        var spellTextLines = new ArrayList<Text>();

        // Header: "Spell choice:" or "Skill choice:" based on access type
        var key = archetype == SpellSchool.Archetype.MAGIC
            ? "spell.tooltip.choice.list.spell"
            : "spell.tooltip.choice.list.skill";
        spellTextLines.add(Text.translatable(key).formatted(Formatting.GRAY));

        // Spell names: "  Arcane Blast | Pyroblast | Frostbolt"
        var spellNames = sortedSpellIds.stream()
            .map(SpellTooltip::spellTranslationKey)
            .map(I18n::translate)
            .collect(Collectors.toList());

        var joinedNames = String.join(" | ", spellNames);
        spellTextLines.add(indentation(1).append(Text.literal(joinedNames)).formatted(Formatting.GRAY));

        // Return with 1 section divider (for the header)
        return new SpellInfo(spellTextLines, 1);
    }

    public static @NotNull SpellTooltip.SpellInfo getSpellInfo(ItemStack itemStack, SpellContainer container, PlayerEntity player,
                                                               boolean forceHideHeader, boolean showDetails) {
        var config = SpellEngineClient.config;
        List<RegistryEntry<Spell>> spells = List.of();
        final var world = MinecraftClient.getInstance().world;
        var spellTextLines = new ArrayList<Text>();
        int addSectionDivider = 0;
        if (world != null) {
            spells = container.spell_ids().stream()
                    .map(idString -> resolveSpell(world, idString))
                    .filter(Objects::nonNull)
                    .toList();
        }

        forceHideHeader = forceHideHeader || itemStack.isIn(SpellEngineItemTags.SPELL_BOOK_MERGEABLE);
        boolean showListHeader = false;
        if (!forceHideHeader) {
            for (var spellEntry : spells) {
                var spell = spellEntry.value();
                var tooltip = spell.tooltip();
                showListHeader = showListHeader || tooltip.show_header;
            }
        }
        int indentLevel = showListHeader ? 1 : 0;

        if (spells.isEmpty()) {
            return new SpellInfo(spellTextLines, addSectionDivider);
        }

        var activeSpells = spells.stream()
                .filter(entry -> entry.value().type == Spell.Type.ACTIVE)
                .toList();
        if (!activeSpells.isEmpty()) {
            String limit = "";
            if (container.max_spell_count() > 0) {
                limit = I18n.translate("spell.tooltip.container.limit")
                        .replace(placeholder("current"), "" + container.spell_ids().size())
                        .replace(placeholder("max"), "" + container.max_spell_count());
            }

            var key = "spell.tooltip.container.list.spell";
            switch (container.access()) {
                case MAGIC -> {
                    key = "spell.tooltip.container.list.spell";
                }
                case ARCHERY -> {
                    key = "spell.tooltip.container.list.archery";
                }
            }
            var header = Text.translatable(key)
                    .append(Text.literal(" " + limit))
                    .formatted(Formatting.GRAY);
            addSpellSection(spellTextLines, indentLevel, showListHeader ? header : null,
                    activeSpells, player, itemStack, showDetails);
            if (showListHeader) {
                addSectionDivider += 1;
            }
        }

        var passiveSpells = spells.stream()
                .filter(entry -> entry.value().type == Spell.Type.PASSIVE)
                .toList();
        if (!passiveSpells.isEmpty()) {
            var header = Text.translatable("spell.tooltip.container.list.passives").formatted(Formatting.GRAY);
            addSpellSection(spellTextLines, indentLevel, showListHeader ? header : null,
                    passiveSpells, player, itemStack, showDetails);
            if (showListHeader) {
                addSectionDivider += 1;
            }
        }

        var modifiers = spells.stream()
                .filter(entry -> entry.value().type == Spell.Type.MODIFIER)
                .toList();
        if (!modifiers.isEmpty()) {
            var header = Text.translatable("spell.tooltip.container.list.modifiers").formatted(Formatting.GRAY);
            addSpellSection(spellTextLines, indentLevel, showListHeader ? header : null,
                    modifiers, player, itemStack, showDetails);
            if (showListHeader) {
                addSectionDivider += 1;
            }
        }

        return new SpellInfo(spellTextLines, addSectionDivider);
    }

    private static void addSpellSection(ArrayList<Text> lines, int indentLevel, @Nullable Text header, List<RegistryEntry<Spell>> content,
                                        PlayerEntity player, ItemStack itemStack, boolean showDetails) {
        if (header != null) {
            lines.add(header);
        }
        for (int i = 0; i < content.size(); i++) {
            var spellEntry = content.get(i);
            var info = spellEntry(spellEntry, player, itemStack, showDetails, indentLevel);
            if (!info.isEmpty()) {
                if (i > 0 && showDetails) {
                    lines.add(Text.literal(" ")); // Separator: empty line
                }
                lines.addAll(info);
            }
        }
    }

    private static boolean isKeyPressed(KeyBinding keybinding) {
        var client = MinecraftClient.getInstance();
        // Checking execution on render thread
        // some loaders like to call these concurrently
        if (client.isOnThread()) {
            return InputUtil.isKeyPressed(client.getWindow().getHandle(),
                    ((KeybindingAccessor) keybinding).spellEngine_getBoundKey().getCode());
        }
        return false;
    }

    public static List<Text> spellEntry(Identifier spellId, PlayerEntity player, ItemStack itemStack, boolean details, int indentLevel) {
        var world = player.getWorld();
        if (world == null) {
            return List.of();
        }
        var spellEntry = resolveSpell(world, spellId);
        if (spellEntry == null) {
            return List.of();
        }
        return spellEntry(spellEntry, player, itemStack, details, indentLevel);
    }

    public static List<Text> spellEntry(RegistryEntry<Spell> spellEntry, PlayerEntity player, ItemStack itemStack, boolean details, int indentLevel) {
        var lines = new ArrayList<Text>();
        var spell = spellEntry.value();
        var spellId = spellEntry.getKey().get().getValue();
        var tooltipData = spell.tooltip != null ? spell.tooltip : Spell.Tooltip.DEFAULT;

        if (shouldShow(tooltipData.name, details)) {
            var color = Formatting.byName(tooltipData.name.color);
            var name = Text.empty().formatted(Formatting.BOLD);
            name.append(Text.translatable(spellTranslationKey(spellId))
                    .formatted(Formatting.BOLD));
//            if (spell.type == Spell.Type.PASSIVE) {
//                name.append(Text.literal(" "))
//                        .formatted(Formatting.RESET);
//                name.append(Text.translatable("spell.type.passive"));
//            }
//            if (spell.group != null) {
//                var translatedGroup = spellGroup(spell.group);
//                if (!translatedGroup.isEmpty()) {
//                    name.append(Text.literal(" "))
//                            .formatted(Formatting.RESET);
//                    name.append(Text.literal(translatedGroup))
//                            .formatted(color);
//                }
//            }
            name = name.formatted(color);
            lines.add(indentation(indentLevel)
                    .append(name));
            indentLevel += 1;
        }

        addSpellDescription(spellEntry, player, itemStack, details, indentLevel, lines);
        if (details) {
            addSpellDetails(spellEntry, player, itemStack, indentLevel, lines);
        }

        return lines;
    }

    private static void addSpellDescription(RegistryEntry<Spell> spellEntry, PlayerEntity player, ItemStack itemStack, boolean details, int indentLevel, ArrayList<Text> lines) {
        var spell = spellEntry.value();
        var tooltipData = spell.tooltip != null ? spell.tooltip : Spell.Tooltip.DEFAULT;
        if (shouldShow(tooltipData.description, details)) {
            var primaryPower = SpellPower.getSpellPower(spell.school, player);
            var color = Formatting.byName(tooltipData.description.color);
            var description = createDescription(spellEntry, player, itemStack, spell, primaryPower);
            lines.add(indentation(indentLevel)
                    .append(Text.translatable(description))
                    .formatted(color));
            addSummonCapabilities(spell, color, indentLevel, lines);
        }
    }

    /// Appends one "Capabilities: ..." line per SUMMON impact, listing what each summoned entity can
    /// do (the spells it casts and whether it melees), built by simple concatenation rather than token
    /// replacement. Multiple summon impacts simply produce multiple lines.
    private static void addSummonCapabilities(Spell spell, Formatting color, int indentLevel, ArrayList<Text> lines) {
        ArrayList<Spell.Impact> impacts = new ArrayList<>();
        if (spell.impacts != null) {
            impacts.addAll(spell.impacts);
        }
        if (spell.modifiers != null) {
            for (var modifier : spell.modifiers) {
                impacts.addAll(modifier.impacts);
            }
        }
        for (var impact : impacts) {
            if (impact.action == null || impact.action.type != Spell.Impact.Action.Type.SUMMON) {
                continue;
            }
            var summon = impact.action.summon;
            if (summon == null) {
                continue;
            }
            var capabilities = summonCapabilities(summon);
            if (capabilities.isEmpty()) {
                continue;
            }
            var line = I18n.translate("spell.tooltip.summon.capabilities") + " " + String.join(", ", capabilities);
            lines.add(indentation(indentLevel)
                    .append(Text.literal(line))
                    .formatted(color));
        }
    }

    /// Translated capability names for a single summon: each spell it casts (by spell name) plus a
    /// single "Melee Attacks" entry if it has any melee attack action, in behaviour-declaration order.
    private static List<String> summonCapabilities(Spell.Impact.Action.Summon summon) {
        var names = new ArrayList<String>();
        var behaviour = summon.behaviour;
        if (behaviour == null) {
            return names;
        }
        boolean meleeListed = false;
        for (var entry : behaviour.actions) {
            if (entry == null || entry.type == null) {
                continue;
            }
            switch (entry.type) {
                case SPELL_CAST -> {
                    if (entry.spell_cast != null && entry.spell_cast.spell_id != null && !entry.spell_cast.spell_id.isBlank()) {
                        var id = Identifier.tryParse(entry.spell_cast.spell_id);
                        if (id != null) {
                            names.add(I18n.translate(spellTranslationKey(id)));
                        }
                    }
                }
                case MELEE_ATTACK -> {
                    if (!meleeListed) {
                        names.add(I18n.translate("spell.tooltip.summon.melee"));
                        meleeListed = true;
                    }
                }
            }
        }
        return names;
    }

    public static List<Text> spellDescriptionWithDetails(Identifier spellId, PlayerEntity player, ItemStack itemStack, int indentLevel) {
        var world = player.getWorld();
        if (world == null) {
            return List.of();
        }
        var spellEntry = resolveSpell(world, spellId);
        if (spellEntry == null) {
            return List.of();
        }
        var lines = new ArrayList<Text>();
        addSpellDescription(spellEntry, player, itemStack, true, indentLevel, lines);
        addSpellDetails(spellEntry, player, itemStack, indentLevel, lines);
        return lines;
    }

    private static void addSpellDetails(RegistryEntry<Spell> spellEntry, PlayerEntity player, ItemStack itemStack, int indentLevel, ArrayList<Text> lines) {
        var spell = spellEntry.value();
        var active = spell.active;
        if (spell.tooltip().show_activation) {
            if (active != null) {
                if (active.cast != null) {
                    if (SpellParameters.isInstant(spell)) {
                        lines.add(indentation(indentLevel)
                                .append(Text.translatable("spell.tooltip.cast_instant"))
                                .formatted(Formatting.GOLD));
                    } else if (active.cast.type == Spell.Active.Cast.Type.CHARGE) {
                        // CHARGE: the duration is the time to reach full charge (releasable earlier).
                        var chargeDuration = SpellParameters.getCastDuration(player, spell, itemStack);
                        var chargeKey = keyWithPlural("spell.tooltip.cast_charge", chargeDuration);
                        var chargeText = I18n.translate(chargeKey).replace(placeholder(durationToken), formattedNumber(chargeDuration));
                        lines.add(indentation(indentLevel)
                                .append(Text.literal(chargeText))
                                .formatted(Formatting.GOLD));
                    } else if (active.cast.type == Spell.Active.Cast.Type.CHANNEL) {
                        // CHANNEL: the duration is the span over which the repeated deliveries are spread.
                        var channelDuration = SpellParameters.getCastDuration(player, spell, itemStack);
                        var channelKey = keyWithPlural("spell.tooltip.cast_channel", channelDuration);
                        var channelText = I18n.translate(channelKey).replace(placeholder(durationToken), formattedNumber(channelDuration));
                        lines.add(indentation(indentLevel)
                                .append(Text.literal(channelText))
                                .formatted(Formatting.GOLD));
                    } else {
                        var castDuration = SpellParameters.getCastDuration(player, spell, itemStack);
                        var castTimeKey = keyWithPlural("spell.tooltip.cast_time", castDuration);
                        var castTime = I18n.translate(castTimeKey).replace(placeholder(durationToken), formattedNumber(castDuration));
                        lines.add(indentation(indentLevel)
                                .append(Text.literal(castTime))
                                .formatted(Formatting.GOLD));
                    }
                }
            }
            var passive = spell.passive;
            if (passive != null) {
                if (!passive.triggers.isEmpty()) {
                    var triggerList = passive.triggers.stream()
                            .map(trigger -> "spell.tooltip.trigger." + trigger.type.toString().toLowerCase(Locale.ENGLISH))
                            .map(I18n::translate)
                            .toList();
                    var joinedTriggers = String.join(", ", triggerList);
                    var triggerText = I18n.translate("spell.tooltip.trigger.base")
                            .replace(placeholder(trigger_list), joinedTriggers);

                    lines.add(indentation(indentLevel)
                            .append(Text.literal(triggerText))
                            .formatted(Formatting.GOLD));
                }
            }
        }

        var chargeRangeAdd = chargeRangeAdd(spell);
        if (spell.tooltip().show_range &&
                (spell.range > 0 || spell.range_mechanic != null || chargeRangeAdd != 0)) {
            String rangeText = "";
            if (spell.range_mechanic != null) {
                switch (spell.range_mechanic) {
                    case MELEE -> {
                        if (spell.range == 0) {
                            rangeText = I18n.translate("spell.tooltip.range.melee");
                        } else {
                            var key = spell.range > 0 ? "spell.tooltip.range.melee.plus" : "spell.tooltip.range.melee.minus";
                            var rangeKey = keyWithPlural(key, spell.range);
                            rangeText = I18n.translate(rangeKey).replace(placeholder(rangeToken), formattedNumber(Math.abs(spell.range))); // Abs to avoid "--1"
                        }
                    }
                }
            } else if (chargeRangeAdd != 0) {
                // CHARGE spell whose charge bonus scales range: show the span from the weakest allowed
                // release to a full charge, e.g. "4 - 10". The charge modifier's range_add scales with
                // the curved charge ratio (full at ratio 1), matching the runtime in getRange; the
                // minimum uses the same `curve(min_release_ratio)` the damage line uses for its floor.
                var charge = spell.active.cast.charge;
                var minRange = spell.range + chargeRangeAdd * charge.curve.apply(charge.min_release_ratio);
                var maxRange = spell.range + chargeRangeAdd;
                var rangeKey = keyWithPlural("spell.tooltip.range", maxRange);
                rangeText = I18n.translate(rangeKey).replace(placeholder(rangeToken), formattedRange(minRange, maxRange));
            } else {
                var rangeKey = keyWithPlural("spell.tooltip.range", spell.range);
                rangeText = I18n.translate(rangeKey).replace(placeholder(rangeToken), formattedNumber(spell.range));
            }
            lines.add(indentation(indentLevel)
                    .append(Text.literal(rangeText))
                    .formatted(Formatting.GOLD));
        }

        var cooldownDuration = SpellParameters.getCooldownDuration(player, spellEntry, itemStack);
        if (cooldownDuration > 0) {
            var cooldownKey = keyWithPlural("spell.tooltip.cooldown", cooldownDuration);
            var cooldown = I18n.translate(cooldownKey).replace(placeholder(durationToken), formattedNumber(cooldownDuration));
            if (spell.cost.cooldown.proportional) {
                // Proportional cooldowns grow with repeated use; the duration shown is the current value.
                cooldown = cooldown + " " + I18n.translate("spell.tooltip.cooldown.proportional");
            }
            lines.add(indentation(indentLevel)
                    .append(Text.literal(cooldown))
                    .formatted(Formatting.GOLD));
        }

        var showItemCost = true;
        var config = SpellEngineMod.config;
        if (config != null) {
            showItemCost = config.spell_cost_item_allowed;
        }
        if (showItemCost) {
            var ammoResult = Ammo.ammoForSpell(player, spell, itemStack);
            if (ammoResult.item() != null) {
                var amount = spell.cost.item.amount;
                var ammoKey = keyWithPlural("spell.tooltip.ammo", amount);
                var itemName = I18n.translate(ammoResult.item().getTranslationKey());
                var ammo = I18n.translate(ammoKey)
                        .replace(placeholder(itemToken), itemName)
                        .replace(placeholder(countToken), amount + "");
                lines.add(indentation(indentLevel)
                        .append(Text.literal(ammo).formatted(ammoResult.satisfied() ? Formatting.GREEN : Formatting.RED)));
            }
        }
    }

    public static boolean shouldShow(Spell.Tooltip.LineOptions options, boolean details) {
        return details ? options.show_in_details : options.show_in_compact;
    }

    private static String createDescription(RegistryEntry<Spell> spellEntry, PlayerEntity player, ItemStack itemStack, Spell spell, SpellPower.Result primaryPower) {
        var spellId = spellEntry.getKey().get().getValue();
        var description = I18n.translate(spellDescriptionTranslationKey(spellId));

        List<Spell.Trigger> triggers = new ArrayList<>();
        var tokenReplacements = new HashMap<String, List<String>>();
        if (spell.passive != null) {
            triggers.addAll(spell.passive.triggers);
        }
        if (spell.deliver != null) {
            Spell.ProjectileData projectile = null;
            if (spell.deliver.projectile != null) {
                projectile = spell.deliver.projectile.projectile;
            }
            if (spell.deliver.meteor != null) {
                projectile = spell.deliver.meteor.projectile;
            }
            if (projectile != null) {
                tokenizeProjectilePerks(projectile.perks, tokenReplacements);
            }

            Spell.LaunchProperties launchProperties = null;
            if (spell.deliver.projectile != null) {
                launchProperties = spell.deliver.projectile.launch_properties;
            }
            if (spell.deliver.meteor != null) {
                launchProperties = spell.deliver.meteor.launch_properties;
            }
            if (launchProperties != null) {
                tokenizeProjectileLaunch(launchProperties, tokenReplacements);
            }

            if (spell.deliver.clouds != null && !spell.deliver.clouds.isEmpty()) {
                var cloud = spell.deliver.clouds.get(0);
                if (cloud != null) {
                    var cloud_duration = cloud.time_to_live_seconds;
                    if (cloud_duration > 0) {
                        addToken("cloud_duration", formattedNumber(cloud_duration), tokenReplacements);
                    }
                    var radius = cloud.volume.combinedRadius(primaryPower.baseValue());
                    addToken("cloud_radius", formattedNumber(radius), tokenReplacements);
                }
            }
            if (spell.deliver.stash_effect != null) {
                var stash = spell.deliver.stash_effect;
                addToken("stash_amplifier", formattedNumber(stash.amplifier + 1), tokenReplacements);
                addToken("stash_duration", formattedNumber(stash.duration), tokenReplacements);
                triggers.addAll(stash.triggers);
            }
            // A melee attack's weapon damage_bonus has no impact-based estimate; expose it as a
            // percentage token (0.5 -> "50%") from the first attack that carries one.
            if (spell.deliver.melee != null) {
                for (var attack : spell.deliver.melee.attacks) {
                    if (attack.damage_bonus != 0) {
                        addToken("weapon_damage_bonus", percent(attack.damage_bonus), tokenReplacements);
                        break;
                    }
                }
            }
        }

        ArrayList<Spell.Impact> impacts = new ArrayList<>();
        if (spell.impacts != null) {
            impacts.addAll(spell.impacts);
        }
        if (spell.modifiers != null) {
            for (var modifier : spell.modifiers) {
                impacts.addAll(modifier.impacts);
            }
        }
        if (!impacts.isEmpty()) {
            var estimatedOutput = SpellEstimation.estimate(spell, player, itemStack);
            for (var impact : impacts) {
                if (impact.chance != 1) {
                    addToken(impact_chance, percent(impact.chance), tokenReplacements);
                }
                switch (impact.action.type) {
                    case DAMAGE -> {
                        description = replaceDamageTokens(description, damageToken, estimatedOutput.damage());
                    }
                    case HEAL -> {
                        description = replaceDamageTokens(description, healToken, estimatedOutput.heal());
                    }
                    case STATUS_EFFECT -> {
                        var statusEffect = impact.action.status_effect;
                        addToken(effectAmplifierToken, "" + (statusEffect.amplifier + 1), tokenReplacements);
                        if (statusEffect.amplifier_cap > 0) {
                            addToken(effectAmplifierCapToken, "" + (statusEffect.amplifier_cap + 1), tokenReplacements);
                        }
                        addToken(effectDurationToken, formattedNumber(statusEffect.duration), tokenReplacements);
                    }
                    case TELEPORT -> {
                        var teleport = impact.action.teleport;
                        switch (teleport.mode) {
                            case FORWARD -> {
                                var forward = teleport.forward;
                                // Distance reflects the player's equipped spell modifiers (matches SpellImpacts TELEPORT).
                                var distance = forward.distance;
                                for (var modifier : SpellModifiers.of(player, spellEntry)) {
                                    distance += modifier.teleport_distance_add;
                                }
                                addToken(teleportDistanceToken, formattedNumber(Math.max(0, distance)), tokenReplacements);
                            }
                        }
                    }
                    case AGGRO -> {
                        // if (impact.action.taunt != null) {
                        //    var taunt = impact.action.taunt;
                        // }
                    }
                    case SUMMON -> {
                        var summon = impact.action.summon;
                        if (summon != null) {
                            var spawnCount = summon.spawn_count;
                            var groupCount = summon.group_count;
                            // Counts reflect the player's equipped spell modifiers (matches SpellImpacts.summon).
                            for (var modifier : SpellModifiers.of(player, spellEntry)) {
                                spawnCount += modifier.summon_spawn_count_add;
                                groupCount += modifier.summon_group_count_add;
                            }
                            addToken(summonDurationToken, formattedNumber(summon.behaviour.lifespan.active_seconds), tokenReplacements);
                            addToken(summonCountToken, "" + Math.max(0, spawnCount), tokenReplacements);
                            addToken(summonGroupCountToken, "" + Math.max(0, groupCount), tokenReplacements);
                        }
                    }
                }
            }
            var area_impact = spell.area_impact;
            tokenizeAreaImpact(primaryPower, area_impact, tokenReplacements);
        }
        for(var trigger : triggers) {
            addToken(trigger_chance, percent(trigger.chance), tokenReplacements);
        }
        for(var modifier: spell.modifiers) {
            if (modifier.range_add != 0) {
                addToken("range_add", formattedNumber(modifier.range_add), tokenReplacements);
            }
            if (modifier.power_modifier != null) {
                addToken("power_multiplier", percent(modifier.power_modifier.power_multiplier), tokenReplacements);
                addToken("critical_chance_bonus", percent(modifier.power_modifier.critical_chance_bonus), tokenReplacements);
                addToken("critical_damage_bonus", percent(modifier.power_modifier.critical_damage_bonus), tokenReplacements);
            }
            if (modifier.replacing_area_impact != null) {
                tokenizeAreaImpact(primaryPower, modifier.replacing_area_impact, tokenReplacements);
            }
            if (modifier.knockback_multiply_base != 0) {
                addToken("knockback_multiply_base", percent(modifier.knockback_multiply_base), tokenReplacements);
            }
            if (modifier.projectile_perks != null) {
                tokenizeProjectilePerks(modifier.projectile_perks, tokenReplacements);
            }
            if (modifier.projectile_launch != null) {
                tokenizeProjectileLaunch(modifier.projectile_launch, tokenReplacements);
            }
            if (modifier.channel_ticks_add != 0) {
                addToken("channel_ticks_add", formattedNumber(modifier.channel_ticks_add), tokenReplacements);
            }
            if (modifier.meteor_launch_radius_add != 0) {
                addToken("meteor_launch_radius_add", formattedNumber(modifier.meteor_launch_radius_add), tokenReplacements);
            }
            if (modifier.effect_amplifier_add != 0) {
                addToken("effect_amplifier_add", formattedNumber(modifier.effect_amplifier_add), tokenReplacements);
            }
            if (modifier.effect_amplifier_cap_add != 0) {
                addToken("effect_amplifier_cap_add", formattedNumber(modifier.effect_amplifier_cap_add), tokenReplacements);
            }
            if (modifier.effect_duration_add != 0) {
                addToken("effect_duration_add", formattedNumber(modifier.effect_duration_add), tokenReplacements);
            }
            if (modifier.stash_amplifier_add != 0) {
                addToken("stash_amplifier_add", formattedNumber(modifier.stash_amplifier_add), tokenReplacements);
            }
            if (modifier.spawn_duration_add != 0) {
                addToken("spawn_duration_add", formattedNumber(modifier.spawn_duration_add), tokenReplacements);
            }
            if (modifier.teleport_distance_add != 0) {
                addToken("teleport_distance_add", formattedNumber(modifier.teleport_distance_add), tokenReplacements);
            }
            if (modifier.cooldown_duration_deduct != 0) {
                addToken("cooldown_duration_deduct", formattedNumber(modifier.cooldown_duration_deduct), tokenReplacements);
            }
            if (modifier.melee_momentum_add != 0) {
                addToken("melee_momentum_add", formattedNumber(modifier.melee_momentum_add), tokenReplacements);
            }
            if (modifier.melee_slipperiness_add != 0) {
                addToken("melee_slipperiness_add", formattedNumber(modifier.melee_slipperiness_add), tokenReplacements);
            }
            if (modifier.melee_damage_multiplier != 0) {
                addToken("melee_damage_multiplier", percent(modifier.melee_damage_multiplier), tokenReplacements);
            }

            if (!modifier.additional_placements.isEmpty()) {
                addToken(additional_placement_count, formattedNumber(modifier.additional_placements.size()), tokenReplacements);
            }

            // Summon impact modifiers
            if (modifier.summon_spawn_count_add != 0) {
                addToken("summon_spawn_count_add", formattedNumber(modifier.summon_spawn_count_add), tokenReplacements);
            }
            if (modifier.summon_group_count_add != 0) {
                addToken("summon_group_count_add", formattedNumber(modifier.summon_group_count_add), tokenReplacements);
            }
            if (modifier.summon_behaviour != null) {
                var summonBehaviour = modifier.summon_behaviour;
                if (!summonBehaviour.actions_add.isEmpty()) {
                    addToken("summon_actions_add_count", formattedNumber(summonBehaviour.actions_add.size()), tokenReplacements);
                }
                var lifespan = summonBehaviour.lifespan;
                if (lifespan.spawn_ticks_add != 0) {
                    addToken("summon_spawn_ticks_add", formattedNumber(lifespan.spawn_ticks_add), tokenReplacements);
                }
                if (lifespan.active_seconds_add != 0) {
                    addToken("summon_active_seconds_add", formattedNumber(lifespan.active_seconds_add), tokenReplacements);
                }
                if (lifespan.despawn_ticks_add != 0) {
                    addToken("summon_despawn_ticks_add", formattedNumber(lifespan.despawn_ticks_add), tokenReplacements);
                }
            }
        }

        Set<String> tokenStarts = new HashSet<>();
        Matcher matcher = TOKEN_PATTERN.matcher(description);
        while (matcher.find()) {
            tokenStarts.add(matcher.group().substring(1));
        }

        for (var entry : tokenReplacements.entrySet()) {
            var token = entry.getKey();
            if (!tokenStarts.contains(token.substring(0, 3))) {
                continue;
            }
            var values = entry.getValue();
            description = replaceTokens(description, token, values);
        }

        description = replaceEffectTokens(description);

        var custom = TooltipTokens.customFor(spellId);
        if (custom != null) {
            var args = new TooltipTokens.Custom.Args(description, player, spellEntry);
            description = custom.resolve(args);
        }
        return description;
    }

    private static void tokenizeAreaImpact(SpellPower.Result primaryPower, Spell.AreaImpact area_impact, HashMap<String, List<String>> tokenReplacements) {
        if (area_impact != null) {
            var radius = area_impact.combinedRadius(primaryPower.baseValue());
            addToken(impactRangeToken, formattedNumber(radius), tokenReplacements);
        }
    }

    private static void tokenizeProjectileLaunch(Spell.LaunchProperties launchProperties, HashMap<String, List<String>> tokenReplacements) {
        var extra_launch_count = launchProperties.extra_launch_count;
        if (extra_launch_count > 0) {
            addToken("extra_launch", formattedNumber(extra_launch_count), tokenReplacements);
        }
    }

    private static void tokenizeProjectilePerks(Spell.ProjectileData.Perks perks, HashMap<String, List<String>> tokenReplacements) {
        if (perks.ricochet > 0) {
            addToken("ricochet", formattedNumber(perks.ricochet), tokenReplacements);
        }
        if (perks.bounce > 0) {
            addToken("bounce", formattedNumber(perks.bounce), tokenReplacements);
        }
        if (perks.pierce > 0) {
            addToken("pierce", formattedNumber(perks.pierce), tokenReplacements);
        }
        if (perks.chain_reaction_size > 0) {
            addToken("chain_reaction_size", formattedNumber(perks.chain_reaction_size), tokenReplacements);
        }
    }

    private static void addToken(String token, String value, Map<String, List<String>> tokenReplacements) {
        if (!tokenReplacements.containsKey(token)) {
            tokenReplacements.put(token, new ArrayList<>());
        }
        tokenReplacements.get(token).add(value);
    }

    private static MutableText indentation(int level) {
        return Text.literal(level > 0 ? " ".repeat(level) : "");
    }

    /// The charge bonus's `range_add` for a CHARGE spell — non-zero only when the charge modifier
    /// actually scales the spell's range, which is the sole case where the range line shows a span.
    private static float chargeRangeAdd(Spell spell) {
        if (spell.active != null && spell.active.cast != null
                && spell.active.cast.type == Spell.Active.Cast.Type.CHARGE
                && spell.active.cast.charge != null) {
            return spell.active.cast.charge.bonus.range_add;
        }
        return 0;
    }

    private static String replaceDamageTokens(String text, String token, List<SpellEstimation.EstimatedValue> values) {
        for (int i = 0; i < values.size(); ++i) {
            var range = values.get(i);
            var actualToken = TooltipTokens.placeholder(token, i, values.size());
            text = text.replace(actualToken, formattedRange(range.min(), range.max()));
        }
        return text;
    }

    public static String replaceTokens(String text, String token, List<String> values) {
        for (int i = 0; i < values.size(); ++i) {
            var actualToken = TooltipTokens.placeholder(token, i, values.size());
            text = text.replace(actualToken, values.get(i));
        }
        return text;
    }

    /// Replaces every `{effect|...}` token in the description with its resolved value. Guarded by a
    /// cheap `contains` check so the regex only runs when a token is actually present. Each distinct
    /// token is resolved at most once (memoised); the matcher scans the original text while
    /// replacements accumulate into the returned string.
    private static String replaceEffectTokens(String text) {
        if (!text.contains(EFFECT_TOKEN_PREFIX)) {
            return text;
        }
        var matcher = EFFECT_TOKEN_PATTERN.matcher(text);
        Set<String> replaced = null;
        while (matcher.find()) {
            var literal = matcher.group();  // "{effect|...}" — the exact substring to replace
            var body = matcher.group(1);    // "<effect_id>|<amplifier>|<attribute>|<format>"
            if (replaced == null) {
                replaced = new HashSet<>();
            }
            if (!replaced.add(literal)) {
                continue; // a repeated token: its first occurrence already replaced all copies
            }
            text = text.replace(literal, resolveEffectToken(literal, body));
        }
        return text;
    }

    /// Returns the display string for an effect token, memoised on its (canonical) body. Unresolvable
    /// tokens cache the original `literal`, so replacing it is a no-op and it stays visible for
    /// debugging rather than being re-parsed every frame.
    private static synchronized String resolveEffectToken(String literal, String body) {
        return resolvedEffectTokens.computeIfAbsent(body, key -> {
            var resolved = computeEffectToken(key);
            return resolved != null ? resolved : literal;
        });
    }

    /// Resolves an effect token body (`<effect_id>|<amplifier>|<attribute>|<format>`) into a
    /// formatted value, or `null` if it can't be resolved. Reads the configured attribute modifiers
    /// straight off the vanilla status effect — amplifier scaling (`base × (amplifier+1)`) is applied
    /// by `forEachAttributeModifier`. A blank attribute field falls back to the effect's *first*
    /// modifier, so a multi-modifier effect still resolves rather than showing the literal token.
    /// The status effect's modifier map is unordered, so "first" is not guaranteed to be the config's
    /// declared-first — fine when the modifiers share a value, but name the attribute explicitly when
    /// they differ.
    private static @Nullable String computeEffectToken(String body) {
        var parts = body.split(Pattern.quote(TooltipTokens.effectTokenSeparator), -1);
        if (parts.length == 0 || parts[0].isBlank()) {
            return null;
        }
        var effectId = Identifier.tryParse(parts[0]);
        if (effectId == null) {
            return null;
        }
        var effect = Registries.STATUS_EFFECT.get(effectId);
        if (effect == null) {
            return null;
        }
        int amplifier = 0;
        if (parts.length > 1 && !parts[1].isBlank()) {
            try {
                amplifier = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        var attributeId = (parts.length > 2 && !parts[2].isBlank()) ? parts[2] : null;
        var format = TooltipTokens.Format.parse(parts.length > 3 ? parts[3] : null);

        // LinkedHashMap so "first" is the first modifier `forEachAttributeModifier` yields.
        var modifiers = new LinkedHashMap<String, EntityAttributeModifier>();
        effect.forEachAttributeModifier(amplifier, (attribute, modifier) ->
                attribute.getKey().ifPresent(key -> modifiers.put(key.getValue().toString(), modifier)));

        EntityAttributeModifier chosen;
        if (attributeId != null) {
            chosen = modifiers.get(attributeId);
        } else {
            // Blank attribute: fall back to the first modifier rather than refusing when several exist.
            chosen = modifiers.isEmpty() ? null : modifiers.values().iterator().next();
        }
        if (chosen == null) {
            return null;
        }
        return format.render((float) chosen.value(), chosen.operation());
    }

    // Value-formatting primitives live in the dependency-free `TooltipTokens` (so the effect-token
    // format resolver can reach them without loading this client-only class). These delegates
    // preserve the existing `SpellTooltip.percent/bonus/formattedNumber` public API.
    public static String percent(float chance) {
        return TooltipTokens.percent(chance);
    }

    public static String bonus(float amount, EntityAttributeModifier.Operation operation) {
        return TooltipTokens.bonus(amount, operation);
    }

    public static String formattedRange(double min, double max) {
        if (min == max) {
            return formattedNumber((float) min);
        }
        return formattedNumber((float) min) + " - " + formattedNumber((float) max);
    }

    public static String formattedNumber(float number) {
        return TooltipTokens.formattedNumber(number);
    }

    public static String keyWithPlural(String key, float value) {
        if (value != 1) {
            return key + ".plural";
        }
        return key;
    }

    public static String spellTranslationKey(Identifier spellId) {
        return spellKeyPrefix(spellId) + ".name";
    }

    public static String spellDescriptionTranslationKey(Identifier spellId) {
        return spellKeyPrefix(spellId) + ".description";
    }

    public static String spellKeyPrefix(Identifier spellId) {
        // For example: `spell.spell_engine.fireball`
        return "spell." + spellId.getNamespace() + "." + spellId.getPath();
    }

    public static String spellGroup(String group) {
        var key = "spell.group." + group;
        if (I18n.hasTranslation(key)) {
            return I18n.translate(key);
        } else {
            return "";
        }
    }

    /// @deprecated Moved to the server-safe {@link TooltipTokens.Custom}; register via
    /// {@link TooltipTokens#registerCustom}. Kept as a bridge so existing content compiles until migrated.
    @Deprecated
    public interface DescriptionMutator {
        record Args(String description, PlayerEntity player, RegistryEntry<Spell> spellEntry) { }
        String mutate(Args args);
    }

    /// @deprecated Use {@link TooltipTokens#registerCustom} with a {@link TooltipTokens.Custom}. This
    /// bridges a legacy handler into the same registry.
    @Deprecated
    public static void addDescriptionMutator(Identifier spellId, DescriptionMutator handler) {
        TooltipTokens.registerCustom(spellId, args ->
                handler.mutate(new DescriptionMutator.Args(args.description(), args.player(), args.spellEntry())));
    }

    // Resolved spell entries depend only on the loaded spell registry, never on the player or
    // attributes, so they are stable until a datapack reload / registry resync swaps the
    // Registry<Spell> instance. We compare instance identity to invalidate, which avoids an
    // Identifier parse + registry lookup for every spell id on every tooltip render frame.
    // Negative results are cached too (Optional.empty), so unknown ids aren't re-resolved each frame.
    private static Registry<Spell> resolvedRegistry;
    private static final Map<String, Optional<RegistryEntry<Spell>>> resolvedSpells = new HashMap<>();

    // Effect-value tokens resolve to a value that depends only on the status-effect registry (the
    // configured attribute modifiers), never on the player — so, like resolved spells, each token
    // body resolves once and is reused until a registry resync. Keyed by the token *body* (canonical,
    // so byte-equality == semantic equality); an unresolvable body caches the original literal so the
    // replacement is a harmless no-op and isn't re-attempted every frame. Cleared beside resolvedSpells.
    private static final Map<String, String> resolvedEffectTokens = new HashMap<>();

    @SuppressWarnings("unchecked")
    private static synchronized @Nullable RegistryEntry<Spell> resolveSpell(World world, String idString) {
        var registry = SpellRegistry.from(world);
        if (registry != resolvedRegistry) {
            resolvedRegistry = registry;
            resolvedSpells.clear();
            resolvedEffectTokens.clear();
        }
        return resolvedSpells.computeIfAbsent(idString, key -> {
            var id = Identifier.tryParse(key);
            if (id == null) {
                return Optional.empty();
            }
            return registry.getEntry(id).map(entry -> (RegistryEntry<Spell>) entry);
        }).orElse(null);
    }

    private static @Nullable RegistryEntry<Spell> resolveSpell(World world, Identifier id) {
        return resolveSpell(world, id.toString());
    }
}
