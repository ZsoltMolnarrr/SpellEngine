package net.spell_engine.internals;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.spell_power.api.MagicSchool;
import net.spell_engine.api.spell.SpellContainer;

import java.util.ArrayList;

public class SpellContainerHelper {
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

    // MARK: NBT Codec

    public static boolean hasValidContainer(ItemStack itemStack) {
        return containerFromItemStack(itemStack) != null;
    }

    public static final String NBT_KEY_CONTAINER = "spell_container";
    private static final String NBT_KEY_SCHOOL = "school";
    private static final String NBT_KEY_MAX_SPELL_COUNT = "max_spell_count";
    private static final String NBT_KEY_SPELL_IDS = "spell_ids";
    public static NbtCompound toNBT(SpellContainer container) {
        var object = new NbtCompound();
        object.putString(NBT_KEY_SCHOOL, container.school.toString());
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
                || !container.contains(NBT_KEY_SCHOOL)
                || !container.contains(NBT_KEY_MAX_SPELL_COUNT)
                || !container.contains(NBT_KEY_SPELL_IDS)) {
            return null;
        }
        try {
            var schoolString = container.getString(NBT_KEY_SCHOOL);
            var school = MagicSchool.valueOf(schoolString);
            var max_spell_count = container.getInt(NBT_KEY_MAX_SPELL_COUNT);
            var spellIds = new ArrayList<String>();
            var spellList = container.getList(NBT_KEY_SPELL_IDS, NbtElement.STRING_TYPE);
            for (int i = 0; i < spellList.size(); i++) {
                spellIds.add(spellList.getString(i));
            }
            return new SpellContainer(school, max_spell_count, spellIds);
        } catch (Exception e) {
            System.err.println("Failed to decode spell container from NBT: " + e.getMessage());
        }
        return null;
    }
}
