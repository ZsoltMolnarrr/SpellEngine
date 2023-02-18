package net.spell_engine.api.effect;

import net.minecraft.entity.effect.StatusEffect;

import java.util.Collection;
import java.util.Objects;

public record EntityActionsAllowed(
        boolean canJump,
        boolean canAttack,
        boolean canUseItem,
        boolean canCastSpell,
        SemanticType reason) {

    public enum SemanticType { // Default ordinal is used for strength ordering
        NONE,
        SILENCE,
        INCAPACITATE,
        STUN
    }

    public static final EntityActionsAllowed any = new EntityActionsAllowed(true, true, true, true, SemanticType.NONE);

    public static final EntityActionsAllowed silence = new EntityActionsAllowed(true, true, true, false, SemanticType.SILENCE);

    public static final EntityActionsAllowed incapacitate = new EntityActionsAllowed(true, false, false, false, SemanticType.INCAPACITATE);

    public static final EntityActionsAllowed stun = new EntityActionsAllowed(false, false, false, false, SemanticType.STUN);

    public interface ControlledEntity {
        EntityActionsAllowed actionImpairing();
    }

    public static EntityActionsAllowed fromEffects(Collection<StatusEffect> effects) {
        var initial = EntityActionsAllowed.any;
        var limiters = effects.stream()
                .map(effect -> ((ActionImpairing)effect).actionsAllowed())
                .filter(Objects::nonNull)
                .toList();
        if (limiters.size() == 0) {
            return initial;
        }
        var canJump = initial.canJump();
        var canAttack = initial.canAttack();
        var canUseItem = initial.canUseItem();
        var canCastSpell = initial.canCastSpell();
        var reason = initial.reason();

        for (var actionsAllowed: limiters) {
            canJump = canJump && actionsAllowed.canJump();
            canAttack = canAttack && actionsAllowed.canAttack();
            canUseItem = canUseItem && actionsAllowed.canUseItem();
            canCastSpell = canCastSpell && actionsAllowed.canCastSpell();
            reason = (actionsAllowed.reason().ordinal() > reason.ordinal()) ? actionsAllowed.reason() : reason;
        }

        return new EntityActionsAllowed(canJump, canAttack, canUseItem, canCastSpell, reason);
    }
}