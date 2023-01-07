package net.spell_engine.mixin.client;

import com.mojang.authlib.GameProfile;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.impl.IAnimatedPlayer;
import net.bettercombat.api.animation.FirstPersonAnimation;
import net.bettercombat.client.animation.StateCollectionHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.encryption.PlayerPublicKey;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.spell_engine.Platform;
import net.spell_engine.api.spell.Sound;
import net.spell_engine.client.animation.AdjustmentModifier;
import net.spell_engine.client.animation.AnimatablePlayer;
import net.spell_engine.client.animation.AnimationRegistry;
import net.spell_engine.client.animation.AnimationSubStack;
import net.spell_engine.client.sound.SpellCastingSound;
import net.spell_engine.internals.SpellAnimationType;
import net.spell_engine.internals.SpellCasterEntity;
import net.spell_engine.mixin.LivingEntityAccessor;
import net.spell_engine.particle.ParticleHelper;
import net.spell_engine.utils.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin extends PlayerEntity implements AnimatablePlayer {
    public AbstractClientPlayerEntityMixin(World world, BlockPos pos, float yaw, GameProfile gameProfile, @Nullable PlayerPublicKey publicKey) {
        super(world, pos, yaw, gameProfile, publicKey);
    }

    private final AnimationSubStack castingAnimation = new AnimationSubStack(createPitchAdjustment_SpellEngine());
    private final AnimationSubStack releaseAnimation = new AnimationSubStack(createPitchAdjustment_SpellEngine());

    @Inject(method = "<init>", at = @At("TAIL"))
    private void postInit_SpellEngine(ClientWorld world, GameProfile profile, PlayerPublicKey publicKey, CallbackInfo ci) {
        var stack = ((IAnimatedPlayer) this).getAnimationStack();
        stack.addAnimLayer(950, releaseAnimation.base);
        stack.addAnimLayer(900, castingAnimation.base);
        if (Platform.isModLoaded("bettercombat")) {
            var player = (AbstractClientPlayerEntity) ((Object) this);
            FirstPersonAnimation.addLayer(player, releaseAnimation.base);
            FirstPersonAnimation.addLayer(player, castingAnimation.base);
        }
    }

    @Override
    public void updateSpellCastAnimationsOnTick() {
        var instance = (Object) this;
        var player = (PlayerEntity) instance;

        String castAnimationName = null;
        Sound castSound = null;
        var spell = ((SpellCasterEntity)player).getCurrentSpell();
        if (spell != null) {
            castAnimationName = spell.cast.animation;
            castSound = spell.cast.sound;
            // Rotate body towards look vector
            ((LivingEntityAccessor)player).invokeTurnHead(player.getHeadYaw(), 0);
            for (var batch: spell.cast.particles) {
                ParticleHelper.play(player.world, player, player.getYaw(), getPitch(), batch);
            }
        }
        updateCastingAnimation(castAnimationName);
        updateCastingSound(castSound);
    }

    private String lastCastAnimationName;
    private void updateCastingAnimation(String animationName) {
        if (!StringUtil.matching(animationName, lastCastAnimationName)) {
            playSpellAnimation(SpellAnimationType.CASTING, animationName);
        }
        lastCastAnimationName = animationName;
    }

    private String lastCastSoundId;
    private SpellCastingSound lastCastSound;
    private void updateCastingSound(Sound castSound) {
        String soundId = null;
        if (castSound != null) {
            soundId = castSound.id();
        }
        if (!StringUtil.matching(soundId, lastCastSoundId)) {
            if (castSound != null && soundId != null && !soundId.isEmpty()) {
                var id = new Identifier(soundId);
                var sound = new SpellCastingSound(this, id, castSound.volume(), castSound.randomizedPitch());
                MinecraftClient.getInstance().getSoundManager().play(sound);
                lastCastSound = sound;
            } else {
                if (lastCastSound != null) {
                    MinecraftClient.getInstance().getSoundManager().stop(lastCastSound);
                    lastCastSound = null;
                }
            }
        }
        lastCastSoundId = soundId;
    }

    private AdjustmentModifier createPitchAdjustment_SpellEngine() {
        var player = (PlayerEntity)this;
        var useFirstPersonAnimationAPI = Platform.isModLoaded("bettercombat");
        return new AdjustmentModifier((partName) -> {
            // System.out.println("Player pitch: " + player.getPitch());
            float rotationX = 0;
            float rotationY = 0;
            float rotationZ = 0;
            float offsetX = 0;
            float offsetY = 0;
            float offsetZ = 0;

            if (useFirstPersonAnimationAPI && FirstPersonAnimation.isRenderingAttackAnimationInFirstPerson()) {
                var pitch = player.getPitch();
                pitch = (float) Math.toRadians(pitch);
                switch (partName) {
                    case "rightArm", "leftArm" -> {
                        rotationX = pitch;
                    }
                    default -> {
                        return Optional.empty();
                    }
                }
            } else {
                var pitch = player.getPitch() / 2F;
                pitch = (float) Math.toRadians(pitch);
                switch (partName) {
                    case "body" -> {
                        rotationX = (-1F) * pitch;
                    }
                    case "rightArm", "leftArm" -> {
                        rotationX = pitch;
                    }
                    case "rightLeg", "leftLeg" -> {
                        rotationX = (-1F) * pitch;
                    }
                    default -> {
                        return Optional.empty();
                    }
                }
            }

            return Optional.of(new AdjustmentModifier.PartModifier(
                    new Vec3f(rotationX, rotationY, rotationZ),
                    new Vec3f(offsetX, offsetY, offsetZ))
            );
        });
    }

    private void updateAnimationByCurrentActivity_SpellEngine(KeyframeAnimation.AnimationBuilder animation) {
        if (isMounting_SpellEngine()) {
            StateCollectionHelper.configure(animation.rightLeg, false, false);
            StateCollectionHelper.configure(animation.leftLeg, false, false);
        }
    }

    public void playSpellAnimation(SpellAnimationType type, String name) {
        try {
            var stack = spellAnimationStackFor(type);
            if (name != null && !name.isEmpty()) {
                var animation = AnimationRegistry.animations.get(name);
                var copy = animation.mutableCopy();
                updateAnimationByCurrentActivity_SpellEngine(copy);
                copy.torso.fullyEnablePart(true);
                copy.head.pitch.setEnabled(false);
                copy.head.yaw.setEnabled(false);
                var mirror = isLeftHanded_SpellEngine();

                var fadeIn = copy.beginTick;
                stack.mirror.setEnabled(mirror);
                stack.base.replaceAnimationWithFade(
                        AbstractFadeModifier.standardFadeIn(fadeIn, Ease.INOUTSINE),
                        new KeyframeAnimationPlayer(copy.build(), 0));
            } else {
                int fadeOutLength = 5;
                stack.base.replaceAnimationWithFade(
                        AbstractFadeModifier.standardFadeIn(fadeOutLength, Ease.INOUTSINE), null);
                stack.adjustment.fadeOut(fadeOutLength);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private AnimationSubStack spellAnimationStackFor(SpellAnimationType type) {
        switch (type) {
            case CASTING -> {
                return castingAnimation;
            }
            case RELEASE -> {
                return releaseAnimation;
            }
        }
        assert true;
        return null;
    }

    private boolean isMounting_SpellEngine() {
        return this.getVehicle() != null;
    }

    public boolean isLeftHanded_SpellEngine() {
        return this.getMainArm() == Arm.LEFT;
    }
}