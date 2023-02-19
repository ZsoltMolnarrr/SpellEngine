package net.spell_engine.api.effect;

import net.minecraft.entity.effect.StatusEffect;

import java.util.Collection;
import java.util.Objects;

public record EntityActionsAllowed(
        boolean canJump,
        boolean canMove,
        PlayersAllowed players,
        MobsAllowed mobs,
        SemanticType reason) {

    public record PlayersAllowed(boolean canAttack, boolean canUseItem, boolean canCastSpell) { }
    public record MobsAllowed(boolean canUseAI) { }
    // Additional mob limits could be added:
    // Mob configurables via Control overrides: canLook, canMove, canJump

    public enum SemanticType { // Default ordinal is used for strength ordering (resulting UI priority)
        NONE,
        SILENCE,
        INCAPACITATE,
        STUN
    }

    public static final EntityActionsAllowed any = new EntityActionsAllowed(true, true,
            new PlayersAllowed(true, true, true),
            new MobsAllowed(true),
            SemanticType.NONE);

    public static final EntityActionsAllowed silence = new EntityActionsAllowed(true, true,
            new PlayersAllowed(true, true, false),
            new MobsAllowed(true),
            SemanticType.SILENCE);

    public static final EntityActionsAllowed incapacitate = new EntityActionsAllowed(true, true,
            new PlayersAllowed(false, false, false),
            new MobsAllowed(false),
            SemanticType.INCAPACITATE);

    public static final EntityActionsAllowed stun = new EntityActionsAllowed(false, false,
            new PlayersAllowed(false, false, false),
            new MobsAllowed(false),
            SemanticType.STUN);

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
        var canMove = initial.canMove();
        var canAttack = initial.players().canAttack();
        var canUseItem = initial.players().canUseItem();
        var canCastSpell = initial.players().canCastSpell();
        var canUseAI = initial.mobs().canUseAI();
        var reason = initial.reason();

        for (var actionsAllowed: limiters) {
            canJump = canJump && actionsAllowed.canJump();
            canMove = canJump && actionsAllowed.canMove();
            canAttack = canAttack && initial.players().canAttack();
            canUseItem = canUseItem && initial.players().canUseItem();
            canCastSpell = canCastSpell && initial.players().canCastSpell();
            canUseAI = canUseAI && initial.mobs().canUseAI();
            reason = (actionsAllowed.reason().ordinal() > reason.ordinal()) ? actionsAllowed.reason() : reason;
        }

        return new EntityActionsAllowed(canJump, canMove,
                new PlayersAllowed(canAttack, canUseItem, canCastSpell),
                new MobsAllowed(canUseAI),
                reason);
    }
}