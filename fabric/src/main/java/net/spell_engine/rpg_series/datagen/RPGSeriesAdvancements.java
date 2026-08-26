package net.spell_engine.rpg_series.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricAdvancementProvider;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.criterion.PlayerTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.spell_engine.misc.criteria.EnchantmentSpecificCriteria;
import net.spell_engine.spellbinding.SpellBinding;
import net.spell_engine.spellbinding.SpellBindingCriteria;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Generates the shared {@code rpg_series} advancement trunk — the root ("Spells" tab) and the two
 * hub advancements every content mod hangs its own tree off of ({@code classes}, {@code misc_items}).
 * <p>
 * The class mods (Wizards / Paladins / Archers / Rogues) each generate their own leaves into the same
 * {@code rpg_series} namespace with the same {@link FabricAdvancementProvider} pattern; this provider
 * owns only what SpellEngine itself defines, which used to be hand-written JSON under
 * {@code common/src/main/resources/data/rpg_series/advancement/}.
 * <p>
 * Translations are NOT generated here: unlike the class mods, SpellEngine's {@code en_us.json} is a
 * hand-authored source asset, and it already carries the {@code advancements.rpg_series.<path>.title} /
 * {@code .description} keys (plus every shipped translation of them).
 */
public class RPGSeriesAdvancements extends FabricAdvancementProvider {
    public static final String NAMESPACE = "rpg_series";

    /**
     * Background of the "Spells" tab, tiled behind the advancement tree.
     * <p>
     * This is a bare texture ASSET ID, not a texture path: the display codec reads it as an
     * {@code AssetInfo.TextureAssetInfo}, which derives the file location as
     * {@code <namespace>:textures/<path>.png} — so this resolves to
     * {@code assets/minecraft/textures/block/chiseled_quartz_block.png}. Writing the full
     * {@code minecraft:textures/....png} form (the pre-1.21.9 syntax) makes the game look for
     * {@code textures/textures/....png.png} and render the missing-texture checkerboard instead.
     */
    public static final Identifier TAB_BACKGROUND = Identifier.fromNamespaceAndPath("minecraft", "block/chiseled_quartz_block");

    public RPGSeriesAdvancements(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(output, registryLookup);
    }

    @Override
    public void generateAdvancement(HolderLookup.Provider registryLookup, Consumer<AdvancementHolder> consumer) {
        entries().forEach(consumer);
    }

    // MARK: Definitions

    public static List<AdvancementHolder> entries() {
        var list = new ArrayList<AdvancementHolder>();

        // Tab root — granted on the first tick, so the tab is always visible.
        var root = root("root", item("spell_engine:spell_binding"),
                "always", PlayerTrigger.TriggerInstance.tick());
        list.add(root);

        // Hub: class progression. Parent of every class mod's `path_choose_*` advancement.
        list.add(advancement("classes", root, new ItemStack(Items.BOOK), AdvancementType.GOAL,
                true, true, false,
                "book", spellBinding(SpellBinding.ADVANCEMENT_VISIT_ID.toString(), true)));

        // Hub: miscellaneous items. Parent of the villager-trade and item advancements.
        var miscItems = advancement("misc_items", root, new ItemStack(Items.CHEST), AdvancementType.TASK,
                false, false, false,
                "always", PlayerTrigger.TriggerInstance.tick());
        list.add(miscItems);

        list.add(advancement("enchant_spell_infinity", miscItems, new ItemStack(Items.ENCHANTED_BOOK),
                AdvancementType.CHALLENGE, true, true, false,
                "enchant", enchantment("spell_engine:spell_infinity")));

        return list;
    }

    // MARK: Builder shorthands

    private static AdvancementHolder root(String idPath, ItemStack icon,
                                         String criterionName, Criterion<?> criterion) {
        var id = Identifier.fromNamespaceAndPath(NAMESPACE, idPath);
        return builder(id, icon, TAB_BACKGROUND, AdvancementType.TASK, false, false, false)
                .addCriterion(criterionName, criterion)
                .build(id);
    }

    private static AdvancementHolder advancement(String idPath, AdvancementHolder parent, ItemStack icon, AdvancementType frame,
                                                boolean showToast, boolean announceToChat, boolean hidden,
                                                String criterionName, Criterion<?> criterion) {
        var id = Identifier.fromNamespaceAndPath(NAMESPACE, idPath);
        return builder(id, icon, null, frame, showToast, announceToChat, hidden)
                .parent(parent)
                .addCriterion(criterionName, criterion)
                .build(id);
    }

    private static Advancement.Builder builder(Identifier id, ItemStack icon, Identifier background, AdvancementType frame,
                                               boolean showToast, boolean announceToChat, boolean hidden) {
        // The original data-pack advancements did not send telemetry events (vanilla default is off),
        // so keep them untelemetered rather than using the telemetered Advancement.Builder.create().
        return Advancement.Builder.recipeAdvancement()
                .display(
                        icon,
                        Component.translatable(translationKey(id, "title")),
                        Component.translatable(translationKey(id, "description")),
                        background,
                        frame,
                        showToast,
                        announceToChat,
                        hidden);
    }

    static String translationKey(Identifier id, String suffix) {
        return "advancements." + id.getNamespace() + "." + id.getPath() + "." + suffix;
    }

    // MARK: Icon helpers

    private static ItemStack item(String itemId) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId)));
    }

    // MARK: Criterion helpers

    private static Criterion<?> spellBinding(String spellPool, boolean complete) {
        return SpellBindingCriteria.INSTANCE.createCriterion(
                new SpellBindingCriteria.Condition(Optional.empty(), Optional.of(spellPool), Optional.of(complete)));
    }

    private static Criterion<?> enchantment(String enchantmentId) {
        return EnchantmentSpecificCriteria.INSTANCE.createCriterion(
                new EnchantmentSpecificCriteria.Condition(Optional.empty(), Optional.of(enchantmentId)));
    }
}
