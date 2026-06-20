package net.spell_engine.entity.goal;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.spell_engine.entity.SummonedEntity;

import java.util.function.Predicate;

/// ActiveTargetGoal whose detection range is the behaviour's `detection_range` rather than the
/// GENERIC_FOLLOW_RANGE attribute. getFollowRange() drives both the candidate search box and the
/// predicate's max-distance filter, and is used by TrackTargetGoal.shouldContinue for retention —
/// so overriding it scopes the whole acquire/keep lifecycle to detection_range.
public class DetectionRangeTargetGoal<T extends LivingEntity> extends ActiveTargetGoal<T> {

    public DetectionRangeTargetGoal(SummonedEntity entity, Class<T> targetClass, int reciprocalChance,
                                    boolean checkVisibility, boolean checkCanNavigate,
                                    Predicate<LivingEntity> predicate) {
        super(entity, targetClass, reciprocalChance, checkVisibility, checkCanNavigate, predicate);
    }

    @Override
    protected double getFollowRange() {
        // Read the summon via the inherited `mob` field, NOT a constructor-assigned field:
        // ActiveTargetGoal's constructor calls getFollowRange() while building its target predicate,
        // before this subclass's constructor body runs. `mob` is already set by the TrackTargetGoal
        // super-constructor at that point, so it is safe; a own field would still be null.
        return ((SummonedEntity) this.mob).detectionRange();
    }
}
