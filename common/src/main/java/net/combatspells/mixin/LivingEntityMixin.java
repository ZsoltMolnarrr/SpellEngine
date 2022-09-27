package net.combatspells.mixin;

import net.combatspells.api.Spell;
import net.combatspells.internals.SpellCasterEntity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntity.class)
public class LivingEntityMixin implements SpellCasterEntity {

    private Spell currentSpell;

    @Override
    public Spell getCurrentSpell() {
        return currentSpell;
    }

    @Override
    public void setCurrentSpell(Spell spell) {
        currentSpell = spell;
    }
}
