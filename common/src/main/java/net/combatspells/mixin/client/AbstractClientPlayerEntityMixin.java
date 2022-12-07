package net.combatspells.mixin.client;

import com.mojang.authlib.GameProfile;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.impl.IAnimatedPlayer;
import net.bettercombat.api.animation.FirstPersonAnimation;
import net.bettercombat.api.animation.FirstPersonAnimator;
import net.bettercombat.client.animation.StateCollectionHelper;
import net.combatspells.Platform;
import net.combatspells.api.spell.Sound;
import net.combatspells.client.animation.AdjustmentModifier;
import net.combatspells.client.animation.AnimatablePlayer;
import net.combatspells.client.animation.AnimationRegistry;
import net.combatspells.client.animation.AnimationSubStack;
import net.combatspells.client.sound.SpellCastingSound;
import net.combatspells.internals.SpellAnimationType;
import net.combatspells.internals.SpellCasterEntity;
import net.combatspells.mixin.LivingEntityAccessor;
import net.combatspells.utils.ParticleHelper;
import net.combatspells.utils.StringUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.encryption.PlayerPublicKey;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
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

    private final AnimationSubStack castingAnimation = new AnimationSubStack(createPitchAdjustment());
    private final AnimationSubStack releaseAnimation = new AnimationSubStack(createPitchAdjustment());

    @Inject(method = "<init>", at = @At("TAIL"))
    private void postInit(ClientWorld world, GameProfile profile, PlayerPublicKey publicKey, CallbackInfo ci) {
        var stack = ((IAnimatedPlayer) this).getAnimationStack();
        stack.addAnimLayer(950, releaseAnimation.base);
        stack.addAnimLayer(900, castingAnimation.base);
        if (Platform.isModLoaded("bettercombat")) {
            ((FirstPersonAnimator)this).addLayer(releaseAnimation.base);
            ((FirstPersonAnimator)this).addLayer(castingAnimation.base);
        }
    }

    @Override
    public void updateCastAnimationsOnTick() {
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
            ParticleHelper.play(player.world, player, spell.cast.particles);
        }
        updateCastingAnimation(castAnimationName);
        updateCastingSound(castSound);
    }

    private String lastCastAnimationName;
    private void updateCastingAnimation(String animationName) {
        if (!StringUtil.matching(animationName, lastCastAnimationName)) {
            playAnimation(SpellAnimationType.CASTING, animationName);
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

    private AdjustmentModifier createPitchAdjustment() {
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

    private void updateAnimationByCurrentActivity(KeyframeAnimation.AnimationBuilder animation) {
        if (isMounting()) {
            StateCollectionHelper.configure(animation.rightLeg, false, false);
            StateCollectionHelper.configure(animation.leftLeg, false, false);
        }
    }

    public void playAnimation(SpellAnimationType type, String name) {
        try {
            var stack = stackFor(type);
            if (name != null && !name.isEmpty()) {
                var animation = AnimationRegistry.animations.get(name);
                var copy = animation.mutableCopy();
                updateAnimationByCurrentActivity(copy);
                copy.torso.fullyEnablePart(true);
                copy.head.pitch.setEnabled(false);
                var mirror = isLeftHanded();

                var fadeIn = copy.beginTick;
                stack.mirror.setEnabled(mirror);
                stack.base.replaceAnimationWithFade(
                        AbstractFadeModifier.standardFadeIn(fadeIn, Ease.INOUTSINE),
                        new KeyframeAnimationPlayer(copy.build(), 0));
            } else {
                stack.base.replaceAnimationWithFade(
                        AbstractFadeModifier.standardFadeIn(5, Ease.INOUTSINE), null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private AnimationSubStack stackFor(SpellAnimationType type) {
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

    private boolean isMounting() {
        return this.getVehicle() != null;
    }

    public boolean isLeftHanded() {
        return this.getMainArm() == Arm.LEFT;
    }
}