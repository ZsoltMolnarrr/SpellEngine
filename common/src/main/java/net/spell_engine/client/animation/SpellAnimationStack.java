package net.spell_engine.client.animation;

import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranimcore.animation.ExtraAnimationData;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.AdjustmentModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.MirrorModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.SpeedModifier;
import com.zigythebird.playeranimcore.easing.EasingType;
import com.zigythebird.playeranimcore.enums.PlayState;
import com.zigythebird.playeranimcore.math.Vec3f;
import net.minecraft.client.model.geom.PartNames;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.client.compatibility.FirstPersonAnimationCompatibility;
import net.spell_engine.internals.casting.SpellCast;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * One Player Animation Library controller per spell animation channel (casting, release, misc).
 * Registered by id with {@code PlayerAnimationFactory.ANIMATION_DATA_FACTORY} (see {@link #registerFactories()}),
 * so PAL owns the instances; the player mixin fetches them through {@code PlayerAnimationAccess}.
 * Composition mirrors Better Combat's {@code AttackAnimationStack}: mirror → speed → pitch adjustment modifiers,
 * with per-part enabling done in the post-animation-setup consumer (formerly {@code StateCollectionHelper}).
 */
public class SpellAnimationStack extends PlayerAnimationController {
    public static final Identifier CASTING_ID = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "casting");
    public static final Identifier RELEASE_ID = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "release");
    public static final Identifier MISC_ID = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "misc");

    public static Identifier idFor(SpellCast.Animation type) {
        return switch (type) {
            case CASTING -> CASTING_ID;
            case RELEASE -> RELEASE_ID;
            case MISC -> MISC_ID;
        };
    }

    /// Call once from client init, before any player entity is constructed
    public static void registerFactories() {
        // Priorities: layer order among all PAL layers on the player. Formerly 900 (casting), 950 (release), 200 (misc).
        com.zigythebird.playeranim.api.PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(RELEASE_ID, 950,
                player -> new SpellAnimationStack(player, SpellCast.Animation.RELEASE));
        com.zigythebird.playeranim.api.PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(CASTING_ID, 900,
                player -> new SpellAnimationStack(player, SpellCast.Animation.CASTING));
        com.zigythebird.playeranim.api.PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(MISC_ID, 200,
                player -> new SpellAnimationStack(player, SpellCast.Animation.MISC));
    }

    public final SpellCast.Animation type;
    public final SpeedModifier speed = new SpeedModifier(1F);
    public final MirrorModifier mirror = new MirrorModifier();
    public final AdjustmentModifier adjustment;
    /// Whether the current animation should follow the player's pitch (see `Spell.Active.Cast.animation_pitch`)
    public boolean pitching = true;

    public SpellAnimationStack(Avatar player, SpellCast.Animation type) {
        super(player, (controller, state, animSetter) -> PlayState.STOP);
        this.type = type;
        this.adjustment = createPitchAdjustment();
        this.mirror.enabled = false;
        this.addModifier(mirror, 0);
        this.addModifier(speed, 0);
        this.addModifierLast(adjustment);
        this.firstPersonMode = (controller) -> FirstPersonAnimationCompatibility.firstPersonMode();
        this.setPostAnimationSetupConsumer((bone) -> {
            // Formerly: copy.torso.fullyEnablePart(true); copy.head.pitch disabled, yaw enabled
            bone.apply("torso").setEnabled(true);
            var head = bone.apply(PartNames.HEAD);
            head.rotXEnabled = false;
            head.rotYEnabled = true;
            // Formerly StateCollectionHelper: no leg animation while mounted (evaluated every frame now)
            if (this.getAvatar().getVehicle() != null) {
                bone.apply(PartNames.RIGHT_LEG).setEnabled(false);
                bone.apply(PartNames.LEFT_LEG).setEnabled(false);
            }
        });
    }

    /// Plays the animation with a fade-in taken from its `beginTick` (playerAnimator format), mirrored if requested
    public void play(Identifier animationId, boolean mirrored, float speed) {
        var animation = PlayerAnimResources.getAnimation(animationId);
        if (animation == null) {
            SpellEngineMod.LOGGER.warn("Player animation not found: " + animationId);
            return;
        }
        var fadeIn = animation.data().<Float>get(ExtraAnimationData.BEGIN_TICK_KEY).map(Math::round).orElse(0);
        this.mirror.enabled = mirrored;
        this.speed.speed = speed;
        this.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(fadeIn, EasingType.EASE_IN_OUT_SINE), animationId);
    }

    /// Fades the current animation out over `fadeOutLength` ticks.
    /// PAL only renders a controller while it is active, so stopping it outright (or triggering a null animation,
    /// which empties the queue) drops the fade modifier on the floor and the pose snaps. Instead a `wait` stage of the
    /// fade length keeps the controller running on an identity pose while the FADE_IN modifier blends the snapshot
    /// of the last pose into it; afterwards the queue empties and the controller stops by itself.
    /// (The `Identifier` overload with null is a silent no-op — never use it to stop.)
    public void stopWithFade() {
        int fadeOutLength = 5;
        this.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(fadeOutLength, EasingType.EASE_IN_OUT_SINE),
                RawAnimation.begin().thenWait(fadeOutLength));
        this.adjustment.fadeOut(fadeOutLength);
        this.speed.speed = 1F;
    }

    private AdjustmentModifier createPitchAdjustment() {
        return new AdjustmentModifier((partName, data) -> {
            float rotationX = 0;
            if (!this.pitching) {
                return Optional.empty();
            }
            var player = this.getAvatar();
            // Sign convention follows Better Combat's PAL AttackAnimationStack (verified in-game): PAL's `body`
            // bone rotates the whole body, so body pitches +X and legs are counter-rotated -X to stay upright.
            // (playerAnimator used body -X; keeping that here made legs swing backwards when looking up.)
            if (data.isFirstPersonPass()) {
                var pitch = (float) Math.toRadians(player.getXRot());
                if (partName.equals(PartNames.BODY)) {
                    rotationX = pitch;
                } else {
                    return Optional.empty();
                }
            } else {
                var pitch = (float) Math.toRadians(player.getXRot() / 2F);
                if (partName.equals(PartNames.BODY)) {
                    rotationX = pitch;
                } else if (isArm(partName)) {
                    rotationX = pitch;
                } else if (isLeg(partName)) {
                    rotationX = (-1F) * pitch;
                } else {
                    return Optional.empty();
                }
            }
            return Optional.of(new AdjustmentModifier.PartModifier(
                    new Vec3f(rotationX, 0, 0),
                    new Vec3f(0, 0, 0))
            );
        });
    }

    private static boolean isArm(String partName) {
        return partName.equals(PartNames.RIGHT_ARM) || partName.equals(PartNames.LEFT_ARM);
    }

    private static boolean isLeg(String partName) {
        return partName.equals(PartNames.RIGHT_LEG) || partName.equals(PartNames.LEFT_LEG);
    }
}
