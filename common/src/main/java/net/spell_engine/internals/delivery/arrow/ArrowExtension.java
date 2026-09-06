package net.spell_engine.internals.delivery.arrow;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.spell_engine.api.spell.Spell;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ArrowExtension {
    /// `perks` is the resolved result of the spell's own `arrow_perks` merged with any modifier's,
    /// as produced by `ArrowHelper.effectiveArrowPerks` — not necessarily `spellEntry`'s own perks.
    void applyArrowPerks(RegistryEntry<Spell> spellEntry, Spell.ArrowPerks perks);
    List<RegistryEntry<Spell>> getCarriedSpells();
    boolean isInGround_SpellEngine();

    /// The ranged weapon this arrow was fired from (1.20.1 has no `PersistentProjectileEntity#getWeaponStack`).
    /// Set by the bow/crossbow shoot mixins at spawn; `null` for spell-fired or mob-fired arrows. Not persisted.
    @Nullable ItemStack getWeaponStack_SpellEngine();
    void setWeaponStack_SpellEngine(@Nullable ItemStack weaponStack);
}
