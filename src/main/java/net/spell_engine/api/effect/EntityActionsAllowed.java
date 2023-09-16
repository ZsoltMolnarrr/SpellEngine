package net.spell_engine.api.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.spell_engine.client.gui.HudMessages;

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

    public static final EntityActionsAllowed ANY = new EntityActionsAllowed(true, true,
            new PlayersAllowed(true, true, true),
            new MobsAllowed(true),
            SemanticType.NONE);

    public static final EntityActionsAllowed SILENCE = new EntityActionsAllowed(true, true,
            new PlayersAllowed(true, true, false),
            new MobsAllowed(true),
            SemanticType.SILENCE);

    public static final EntityActionsAllowed INCAPACITATE = new EntityActionsAllowed(true, true,
            new PlayersAllowed(false, false, false),
            new MobsAllowed(false),
            SemanticType.INCAPACITATE);

    public static final EntityActionsAllowed STUN = new EntityActionsAllowed(false, false,
            new PlayersAllowed(false, false, false),
            new MobsAllowed(false),
            SemanticType.STUN);

    public interface ControlledEntity {
        EntityActionsAllowed actionImpairing();
        void updateEntityActionsAllowed();
    }

    public static EntityActionsAllowed fromEffects(Collection<StatusEffect> effects) {
        var initial = EntityActionsAllowed.ANY;
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

        for (var impairing: limiters) {
            canJump = canJump && impairing.canJump();
            canMove = canMove && impairing.canMove();
            canAttack = canAttack && impairing.players().canAttack();
            canUseItem = canUseItem && impairing.players().canUseItem();
            canCastSpell = canCastSpell && impairing.players().canCastSpell();
            canUseAI = canUseAI && impairing.mobs().canUseAI();
            reason = (impairing.reason().ordinal() > reason.ordinal()) ? impairing.reason() : reason;
        }

        return new EntityActionsAllowed(canJump, canMove,
                new PlayersAllowed(canAttack, canUseItem, canCastSpell),
                new MobsAllowed(canUseAI),
                reason);
    }

    public enum Common { MOVE, JUMP }
    public static boolean isImpaired(LivingEntity entity, Common action) {
        var actionsAllowed = ((ControlledEntity)entity).actionImpairing();
        var allowed = true;
        switch (action) {
            case MOVE -> {
                allowed = actionsAllowed.canMove();
            }
            case JUMP -> {
                allowed = actionsAllowed.canJump();
            }
        }
        return !allowed;
    }

    public enum Player { ATTACK, ITEM_USE, CAST_SPELL }
    public static boolean isImpaired(LivingEntity player, Player action) {
        return isImpaired(player, action, false);
    }
    public static boolean isImpaired(LivingEntity player, Player action, boolean showError) {
        var allowed = true;
        var actionsAllowed = ((ControlledEntity)player).actionImpairing();
        switch (action) {
            case ATTACK -> {
                allowed = actionsAllowed.players().canAttack();
            }
            case ITEM_USE -> {
                allowed = actionsAllowed.players().canUseItem();
            }
            case CAST_SPELL -> {
                allowed = actionsAllowed.players().canCastSpell();
            }
        }
        if (player.getWorld().isClient && showError && !allowed) {
            HudMessages.INSTANCE.actionImpaired(actionsAllowed.reason());
        }
        return !allowed;
    }

    public enum Mob { USE_AI }
    public static boolean isImpaired(LivingEntity entity, Mob action) {
        var actionsAllowed = ((ControlledEntity)entity).actionImpairing();
        var allowed = true;
        switch (action) {
            case USE_AI -> {
                allowed = actionsAllowed.mobs().canUseAI();
            }
        }
        return !allowed;
    }
}