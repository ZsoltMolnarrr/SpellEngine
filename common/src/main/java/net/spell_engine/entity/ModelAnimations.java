package net.spell_engine.entity;

import org.joml.Vector3fc;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;

/**
 * Shared, client-side helpers for driving keyframe animations on summoned-entity models.
 *
 * <p>Only referenced from client render code (entity models), so despite touching client-only
 * types it is never loaded on a dedicated server.
 */
public final class ModelAnimations {
    private ModelAnimations() {}

    /**
     * Seamless-loop variant of vanilla
     * {@link net.minecraft.client.animation.KeyframeAnimations#animate}.
     *
     * <p>Vanilla treats a looping clip's boundary like a dead end: at the segment touching the first
     * or last keyframe, catmull-rom <em>clamps</em> its outer neighbour ({@code max(0, start-1)} /
     * {@code min(len-1, end+1)}) instead of borrowing from the opposite end of the loop, and any
     * {@code LINEAR} final segment arrives at the seam pose with constant velocity and then instantly
     * reverses. Both produce a velocity kink at the loop seam — a visible momentary hitch each cycle,
     * most noticeable on the walk/run cycles of movable summons.
     *
     * <p>This is a verbatim port of vanilla's {@code animate} with one change: for a
     * {@code .looping()} clip, the segment that touches keyframe {@code 0} or {@code len-1} is
     * interpolated with catmull-rom whose outer neighbour <em>wraps</em> around the loop
     * (pre-neighbour of {@code kf[0]} = {@code kf[len-2]}, post-neighbour of {@code kf[len-1]} =
     * {@code kf[1]}). Because a looping clip's first and last keyframes share the same value, the
     * seam then interpolates as a smooth rounded turnaround, identical in feel to an interior
     * keyframe. Interior segments keep their authored interpolation, untouched. Non-looping clips
     * are interpolated exactly as vanilla.
     *
     * <p>Drop-in for {@code SinglePartEntityModel.animateMovement}'s inner call — map
     * {@code limbSwing} to {@code runningTime} (as {@code (long)(limbSwing * 50F)}) and
     * {@code limbSwingAmount} to {@code scale}.
     */
    public static void seamlessLoop(Model<?> model, AnimationDefinition animation, long runningTime, float scale, Vector3f temp) {
        float lengthSeconds = animation.lengthInSeconds();
        boolean looping = animation.looping();
        float f = runningTime / 1000.0F;
        if (looping) {
            f %= lengthSeconds;
        }
        final float time = f;

        for (Map.Entry<String, List<AnimationChannel>> entry : animation.boneAnimations().entrySet()) {
            List<AnimationChannel> transformations = entry.getValue();
            var part = findPart(model.root(), entry.getKey());
            if (part == null) continue;
            for (AnimationChannel transformation : transformations) {
                apply(part, transformation, time, looping, scale, temp);
            }
        }
    }

    /// Depth-first lookup by name; ModelPart no longer exposes an Optional child getter
    @org.jetbrains.annotations.Nullable
    private static ModelPart findPart(ModelPart root, String name) {
        for (var candidate : root.getAllParts()) {
            if (candidate.hasChild(name)) return candidate.getChild(name);
        }
        return null;
    }

    private static void apply(ModelPart part, AnimationChannel transformation, float time, boolean looping, float scale, Vector3f temp) {
        Keyframe[] keyframes = transformation.keyframes();
        int last = keyframes.length - 1;
        int i = Math.max(0, Mth.binarySearch(0, keyframes.length, index -> time <= keyframes[index].timestamp()) - 1);
        int j = Math.min(last, i + 1);
        Keyframe from = keyframes[i];
        Keyframe to = keyframes[j];
        float delta = 0.0F;
        if (j != i) {
            delta = Mth.clamp((time - from.timestamp()) / (to.timestamp() - from.timestamp()), 0.0F, 1.0F);
        }

        // Only the boundary segment of a looping clip diverges from vanilla; everything else is
        // interpolated exactly as authored (including LINEAR/CUBIC choice and vanilla's own clamping).
        boolean touchesSeam = looping && last >= 2 && (i == 0 || j == last);
        if (touchesSeam) {
            Vector3fc p0 = keyframes[i == 0 ? last - 1 : i - 1].postTarget();       // wrap the pre-neighbour
            Vector3fc p1 = from.postTarget();
            Vector3fc p2 = to.postTarget();
            Vector3fc p3 = keyframes[j == last ? 1 : j + 1].postTarget();           // wrap the post-neighbour
            temp.set(
                    Mth.catmullrom(delta, p0.x(), p1.x(), p2.x(), p3.x()) * scale,
                    Mth.catmullrom(delta, p0.y(), p1.y(), p2.y(), p3.y()) * scale,
                    Mth.catmullrom(delta, p0.z(), p1.z(), p2.z(), p3.z()) * scale
            );
        } else {
            to.interpolation().apply(temp, delta, keyframes, i, j, scale);
        }
        transformation.target().apply(part, temp);
    }
}
