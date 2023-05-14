package net.spell_engine.internals;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.SpellContainer;
import net.spell_engine.api.spell.SpellPool;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class SpellContainerHelper {
    public static Identifier getPoolId(SpellContainer container) {
        if (container != null && container.pool != null) {
            return new Identifier(container.pool);
        }
        return null;
    }

    public static SpellPool getPool(SpellContainer container) {
        if (container != null && container.pool != null) {
            var id = new Identifier(container.pool);
            return SpellRegistry.spellPool(id);
        }
        return SpellPool.empty;
    }

    public static SpellContainer containerFromItemStack(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return null;
        }
        var object = (Object)itemStack;
        if (object instanceof SpellCasterItemStack stack) {
            var container = stack.getSpellContainer();
            if (container != null && container.isValid()) {
                return container;
            }
        }
        return null;
    }

    public static void addContainerToItemStack(SpellContainer container, ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return;
        }
        var nbt = itemStack.getOrCreateNbt();
        var nbtContainer = SpellContainerHelper.toNBT(container);
        nbt.put(SpellContainerHelper.NBT_KEY_CONTAINER, nbtContainer);
    }

    @Nullable
    public static Identifier spellId(SpellContainer container, int selectedIndex) {
        if (container == null || !container.isUsable()) {
            return null;
        }
        return new Identifier(container.spellId(selectedIndex));
    }

    public static SpellContainer addSpell(Identifier spellId, SpellContainer container) {
        var spellIds = new ArrayList<String>(container.spell_ids);
        spellIds.add(spellId.toString());

        // Creating a map just for the sake of sorting
        HashMap<Identifier, Spell> spells = new HashMap<>();
        for(var idString: spellIds) {
            var id = new Identifier(idString);
            var spell = SpellRegistry.getSpell(id);
            if (spell != null) {
                spells.put(id, spell);
            }
        }
        var sortedSpellIds = spells.entrySet().stream()
                .sorted(SpellContainerHelper.spellSorter)
                .map(entry -> entry.getKey().toString())
                .collect(Collectors.toList());

        var newContainer = container.copy();
        newContainer.spell_ids = sortedSpellIds;

        return newContainer;
    }

    public static void addSpell(Identifier spellId, ItemStack itemStack) {
        var container = containerFromItemStack(itemStack);
        if (container == null || !container.isValid()) {
            System.err.println("Trying to add spell: " + spellId  + " to an ItemStack without valid spell container");
            return;
        }
        var modifiedContainer = addSpell(spellId, container);
        var nbtContainer = SpellContainerHelper.toNBT(modifiedContainer);
        var nbt = itemStack.getOrCreateNbt();
        nbt.put(SpellContainerHelper.NBT_KEY_CONTAINER, nbtContainer);
    }

    public static final Comparator<Map.Entry<Identifier, Spell>> spellSorter = (spell1, spell2) -> {
        if (spell1.getValue().learn.tier > spell2.getValue().learn.tier) {
            return 1;
        }  else if (spell1.getValue().learn.tier < spell2.getValue().learn.tier) {
            return -1;
        } else {
            return spell1.getKey().toString().compareTo(spell2.getKey().toString());
        }
    };

    public static boolean hasValidContainer(ItemStack itemStack) {
        return containerFromItemStack(itemStack) != null;
    }

    public static boolean hasUsableContainer(ItemStack itemStack) {
        var container = containerFromItemStack(itemStack);
        return container != null && container.isUsable();
    }

    // MARK: NBT Codec

    public static final String NBT_KEY_CONTAINER = "spell_container";
    private static final String NBT_KEY_POOL = "pool";
    private static final String NBT_KEY_MAX_SPELL_COUNT = "max_spell_count";
    private static final String NBT_KEY_SPELL_IDS = "spell_ids";
    public static NbtCompound toNBT(SpellContainer container) {
        var object = new NbtCompound();
        if (container.pool != null) {
            object.putString(NBT_KEY_POOL, container.pool);
        }
        object.putInt(NBT_KEY_MAX_SPELL_COUNT, container.max_spell_count);
        var spellList = new NbtList();
        for (var spellId: container.spell_ids) {
            var element = NbtString.of(spellId);
            spellList.add(element);
        }
        object.put(NBT_KEY_SPELL_IDS, spellList);
        return object;
    }

    public static SpellContainer fromNBT(NbtCompound nbt) {
        var container = nbt.getCompound(NBT_KEY_CONTAINER);
        if (container == null
                || !container.contains(NBT_KEY_MAX_SPELL_COUNT)
                || !container.contains(NBT_KEY_SPELL_IDS)) {
            return null;
        }
        try {
            String pool = null;
            if (container.contains(NBT_KEY_POOL)) {
                pool = container.getString(NBT_KEY_POOL);
            }
            var max_spell_count = container.getInt(NBT_KEY_MAX_SPELL_COUNT);
            var spellIds = new ArrayList<String>();
            var spellList = container.getList(NBT_KEY_SPELL_IDS, NbtElement.STRING_TYPE);
            for (int i = 0; i < spellList.size(); i++) {
                spellIds.add(spellList.getString(i));
            }
            return new SpellContainer(pool, max_spell_count, spellIds);
        } catch (Exception e) {
            System.err.println("Failed to decode spell container from NBT: " + e.getMessage());
        }
        return null;
    }
}
