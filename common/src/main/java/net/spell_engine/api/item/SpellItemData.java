package net.spell_engine.api.item;

import com.mojang.serialization.Codec;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.spell_engine.api.spell.container.SpellChoice;
import net.spell_engine.api.spell.container.SpellContainer;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/// Spell Engine's per-stack item data on 1.20.1 (the 1.21 data components `spell_container`, `spell_choice`,
/// `equipment_set`, `item_model`), stored as NBT.
///
/// Layout on the stack: a `spell_engine` sub-compound with one key per value
/// (`spell_container` / `spell_choice` as codec-encoded compounds, `equipment_set` / `item_model` as identifier strings).
///
/// Values not present on the stack fall back to the **item-level defaults** registered through {@link #defaults(Item)}
/// (or {@link #defaults(Item.Settings)} before the item exists), which replaces `Item.Settings#component(...)`:
/// a fresh `new ItemStack(item)` carries no NBT and still reads its container / choice / equipment set / model.
public class SpellItemData {
    public static final String ROOT = "spell_engine";
    public static final String SPELL_CONTAINER = "spell_container";
    public static final String SPELL_CHOICE = "spell_choice";
    public static final String EQUIPMENT_SET = "equipment_set";
    public static final String ITEM_MODEL = "item_model";
    /// Translation key overriding the item's display name (replaces the 1.21 `item_name` component)
    public static final String ITEM_NAME = "item_name";
    /// Rarity name overriding the item's rarity (replaces the 1.21 `rarity` component)
    public static final String RARITY = "rarity";

    // MARK: Raw NBT access

    /// The `spell_engine` sub-compound of the stack, or `null` when the stack carries none
    @Nullable
    public static NbtCompound root(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.getSubNbt(ROOT);
    }

    public static NbtCompound rootOrCreate(ItemStack stack) {
        return stack.getOrCreateSubNbt(ROOT);
    }

    public static boolean contains(ItemStack stack, String key) {
        var root = root(stack);
        return root != null && root.contains(key);
    }

    /// Removes a key; drops the `spell_engine` compound (and an emptied stack NBT) when nothing is left in it
    public static void remove(ItemStack stack, String key) {
        var root = root(stack);
        if (root == null) {
            return;
        }
        root.remove(key);
        if (root.isEmpty()) {
            stack.removeSubNbt(ROOT);
            var nbt = stack.getNbt();
            if (nbt != null && nbt.isEmpty()) {
                stack.setNbt(null);
            }
        }
    }

    // MARK: Codec round-trips

    /// Decoded values keyed by the NBT element they were decoded from (per key), so hot readers
    /// (spell container lookups every tick) do not re-run the codec on unchanged stacks.
    /// `set` puts a fresh element, and in-place NBT edits change equality, so stale hits cannot happen.
    private static final Map<String, Map<NbtElement, Object>> decodeCache = new HashMap<>();

    private static Map<NbtElement, Object> decodeCache(String key) {
        synchronized (decodeCache) {
            return decodeCache.computeIfAbsent(key, k -> Collections.synchronizedMap(new WeakHashMap<>()));
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> T get(ItemStack stack, String key, Codec<T> codec) {
        var root = root(stack);
        if (root == null || !root.contains(key)) {
            return null;
        }
        var element = root.get(key);
        var cache = decodeCache(key);
        var cached = cache.get(element);
        if (cached != null) {
            return (T) cached;
        }
        var decoded = codec.parse(NbtOps.INSTANCE, element)
                .resultOrPartial(error -> System.err.println("Failed to read " + ROOT + "." + key + " from item stack: " + error))
                .orElse(null);
        if (decoded != null) {
            cache.put(element, decoded);
        }
        return decoded;
    }

    public static <T> void set(ItemStack stack, String key, Codec<T> codec, @Nullable T value) {
        if (value == null) {
            remove(stack, key);
            return;
        }
        var encoded = codec.encodeStart(NbtOps.INSTANCE, value)
                .resultOrPartial(error -> System.err.println("Failed to write " + ROOT + "." + key + " to item stack: " + error));
        if (encoded.isPresent()) {
            rootOrCreate(stack).put(key, encoded.get());
        }
    }

    // MARK: Spell container

    /// Container stored on the stack, else the item-level default, else `null`.
    /// (Datapack spell assignments are layered on top by `SpellContainerHelper.containerFromItemStack`.)
    @Nullable
    public static SpellContainer getSpellContainer(ItemStack stack) {
        var stored = get(stack, SPELL_CONTAINER, SpellContainer.CODEC);
        if (stored != null) {
            return stored;
        }
        return stack.isEmpty() ? null : defaultsOrNull(stack.getItem(), defaults -> defaults.spellContainer);
    }

    public static void setSpellContainer(ItemStack stack, @Nullable SpellContainer container) {
        set(stack, SPELL_CONTAINER, SpellContainer.CODEC, container);
    }

    public static boolean hasSpellContainer(ItemStack stack) {
        return getSpellContainer(stack) != null;
    }

    public static void removeSpellContainer(ItemStack stack) {
        remove(stack, SPELL_CONTAINER);
    }

    // MARK: Spell choice

    @Nullable
    public static SpellChoice getSpellChoice(ItemStack stack) {
        var stored = get(stack, SPELL_CHOICE, SpellChoice.CODEC);
        if (stored != null) {
            return stored;
        }
        return stack.isEmpty() ? null : defaultsOrNull(stack.getItem(), defaults -> defaults.spellChoice);
    }

    public static void setSpellChoice(ItemStack stack, @Nullable SpellChoice choice) {
        set(stack, SPELL_CHOICE, SpellChoice.CODEC, choice);
    }

    public static boolean hasSpellChoice(ItemStack stack) {
        return getSpellChoice(stack) != null;
    }

    public static void removeSpellChoice(ItemStack stack) {
        remove(stack, SPELL_CHOICE);
    }

    // MARK: Equipment set

    @Nullable
    public static Identifier getEquipmentSet(ItemStack stack) {
        var stored = getIdentifier(stack, EQUIPMENT_SET);
        if (stored != null) {
            return stored;
        }
        return stack.isEmpty() ? null : defaultsOrNull(stack.getItem(), defaults -> defaults.equipmentSet);
    }

    public static void setEquipmentSet(ItemStack stack, @Nullable Identifier id) {
        setIdentifier(stack, EQUIPMENT_SET, id);
    }

    public static boolean hasEquipmentSet(ItemStack stack) {
        return getEquipmentSet(stack) != null;
    }

    public static void removeEquipmentSet(ItemStack stack) {
        remove(stack, EQUIPMENT_SET);
    }

    // MARK: Item model

    @Nullable
    public static Identifier getItemModel(ItemStack stack) {
        var stored = getIdentifier(stack, ITEM_MODEL);
        if (stored != null) {
            return stored;
        }
        return stack.isEmpty() ? null : defaultsOrNull(stack.getItem(), defaults -> defaults.itemModel);
    }

    public static void setItemModel(ItemStack stack, @Nullable Identifier id) {
        setIdentifier(stack, ITEM_MODEL, id);
    }

    public static boolean hasItemModel(ItemStack stack) {
        return getItemModel(stack) != null;
    }

    public static void removeItemModel(ItemStack stack) {
        remove(stack, ITEM_MODEL);
    }

    // MARK: Item name (translation key) and rarity overrides

    @Nullable
    public static String getItemNameKey(ItemStack stack) {
        var root = root(stack);
        if (root == null || !root.contains(ITEM_NAME, NbtElement.STRING_TYPE)) {
            return null;
        }
        return root.getString(ITEM_NAME);
    }

    public static void setItemNameKey(ItemStack stack, @Nullable String translationKey) {
        if (translationKey == null || translationKey.isEmpty()) {
            remove(stack, ITEM_NAME);
        } else {
            rootOrCreate(stack).putString(ITEM_NAME, translationKey);
        }
    }

    @Nullable
    public static Rarity getRarity(ItemStack stack) {
        var root = root(stack);
        if (root == null || !root.contains(RARITY, NbtElement.STRING_TYPE)) {
            return null;
        }
        var name = root.getString(RARITY);
        for (var rarity : Rarity.values()) {
            if (rarity.name().equalsIgnoreCase(name)) {
                return rarity;
            }
        }
        return null;
    }

    public static void setRarity(ItemStack stack, @Nullable Rarity rarity) {
        if (rarity == null) {
            remove(stack, RARITY);
        } else {
            rootOrCreate(stack).putString(RARITY, rarity.name().toLowerCase());
        }
    }

    // MARK: Identifier helpers

    @Nullable
    public static Identifier getIdentifier(ItemStack stack, String key) {
        var root = root(stack);
        if (root == null || !root.contains(key, NbtElement.STRING_TYPE)) {
            return null;
        }
        return Identifier.tryParse(root.getString(key));
    }

    public static void setIdentifier(ItemStack stack, String key, @Nullable Identifier id) {
        if (id == null) {
            remove(stack, key);
        } else {
            rootOrCreate(stack).putString(key, id.toString());
        }
    }

    // MARK: Item-level defaults (replacement for `Item.Settings#component(...)`)

    /// Values a stack of an item reads when it carries no NBT of its own.
    public static class Defaults {
        @Nullable public SpellContainer spellContainer;
        @Nullable public SpellChoice spellChoice;
        @Nullable public Identifier equipmentSet;
        @Nullable public Identifier itemModel;

        public Defaults spellContainer(@Nullable SpellContainer container) {
            this.spellContainer = container;
            return this;
        }

        public Defaults spellChoice(@Nullable SpellChoice choice) {
            this.spellChoice = choice;
            return this;
        }

        public Defaults equipmentSet(@Nullable Identifier id) {
            this.equipmentSet = id;
            return this;
        }

        public Defaults itemModel(@Nullable Identifier id) {
            this.itemModel = id;
            return this;
        }

        public boolean isEmpty() {
            return spellContainer == null && spellChoice == null && equipmentSet == null && itemModel == null;
        }

        /// Writes every default onto the stack as explicit NBT (e.g. for a stack that must carry its data
        /// through channels that only see NBT, like a loot table or a recipe result)
        public void writeTo(ItemStack stack) {
            if (spellContainer != null) { setSpellContainer(stack, spellContainer); }
            if (spellChoice != null) { setSpellChoice(stack, spellChoice); }
            if (equipmentSet != null) { setEquipmentSet(stack, equipmentSet); }
            if (itemModel != null) { setItemModel(stack, itemModel); }
        }
    }

    private static final Map<Item, Defaults> itemDefaults = new HashMap<>();
    private static final Map<Item.Settings, Defaults> pendingDefaults = new WeakHashMap<>();

    /// Item-level defaults of an item, created on first access.
    public static Defaults defaults(Item item) {
        return itemDefaults.computeIfAbsent(item, key -> new Defaults());
    }

    @Nullable
    public static Defaults defaultsOf(Item item) {
        return itemDefaults.get(item);
    }

    /// Item-level defaults attached to an `Item.Settings` **before** the item exists — the 1.20.1 stand-in for
    /// `Item.Settings#component(...)`. They are adopted by the item constructed from these settings
    /// (`ItemDefaultsMixin` on `Item.<init>`).
    public static Defaults defaults(Item.Settings settings) {
        return pendingDefaults.computeIfAbsent(settings, key -> new Defaults());
    }

    /// Called by the `Item.<init>` mixin: moves settings-attached defaults to the constructed item
    public static void adoptPendingDefaults(Item item, Item.Settings settings) {
        var pending = pendingDefaults.remove(settings);
        if (pending == null || pending.isEmpty()) {
            return;
        }
        var target = defaults(item);
        if (pending.spellContainer != null) { target.spellContainer = pending.spellContainer; }
        if (pending.spellChoice != null) { target.spellChoice = pending.spellChoice; }
        if (pending.equipmentSet != null) { target.equipmentSet = pending.equipmentSet; }
        if (pending.itemModel != null) { target.itemModel = pending.itemModel; }
    }

    private interface DefaultsReader<T> {
        @Nullable T read(Defaults defaults);
    }

    @Nullable
    private static <T> T defaultsOrNull(Item item, DefaultsReader<T> reader) {
        var defaults = itemDefaults.get(item);
        return defaults != null ? reader.read(defaults) : null;
    }
}
