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
import net.bettercombat.client.animation.AdjustmentModifier;
import net.bettercombat.client.animation.StateCollectionHelper;
import net.combatspells.Platform;
import net.combatspells.client.sound.SpellCastingSound;
import net.combatspells.client.animation.AnimatablePlayer;
import net.combatspells.client.animation.AnimationRegistry;
import net.combatspells.client.animation.AnimationSubStack;
import net.combatspells.internals.SpellCasterEntity;
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

    @Inject(method = "<init>", at = @At("TAIL"))
    private void postInit(ClientWorld world, GameProfile profile, PlayerPublicKey publicKey, CallbackInfo ci) {
        var stack = ((IAnimatedPlayer) this).getAnimationStack();
        stack.addAnimLayer(2100, castingAnimation.base);
        if (Platform.isModLoaded("bettercombat")) {
            ((FirstPersonAnimator)this).addLayer(castingAnimation.base);
        }
    }

    @Override
    public void updateCastAnimationsOnTick() {
        var instance = (Object) this;
        var player = (PlayerEntity) instance;

        String castAnimationName = null;
        String castSoundId = null;
        var spell = ((SpellCasterEntity)player).getCurrentSpell();
        if (spell != null) {
            castAnimationName = spell.cast.animation;
            if (spell.cast.sound != null) {
                castSoundId = spell.cast.sound.id();
            }
            // Rotate body towards look vector
            ((LivingEntityAccessor)player).invokeTurnHead(player.getHeadYaw(), 0);
        }
        updateCastingAnimation(castAnimationName);
        updateCastingSound(castSoundId);
    }

    private String lastCastAnimationName;
    private void updateCastingAnimation(String animationName) {
        if (!StringUtil.matching(animationName, lastCastAnimationName)) {
            if (animationName != null && !animationName.isEmpty()) {
                var animation = AnimationRegistry.animations.get(animationName);
                var copy = animation.mutableCopy();
                updateAnimationByCurrentActivity(copy);
                copy.torso.fullyEnablePart(true);
                copy.head.pitch.setEnabled(false);
                var mirror = isLeftHanded();

                var fadeIn = copy.beginTick;
                // castingAnimation.speed.speed = speed;
                castingAnimation.mirror.setEnabled(mirror);
                castingAnimation.base.replaceAnimationWithFade(
                        AbstractFadeModifier.standardFadeIn(fadeIn, Ease.INOUTSINE),
                        new KeyframeAnimationPlayer(copy.build(), 0));
            } else {
                castingAnimation.base.replaceAnimationWithFade(
                        AbstractFadeModifier.standardFadeIn(5, Ease.INOUTSINE), null);
            }
        }
        lastCastAnimationName = animationName;
    }

    private String lastCastSoundId;
    private SpellCastingSound lastCastSound;
    private void updateCastingSound(String soundId) {
        if (!StringUtil.matching(soundId, lastCastSoundId)) {
            System.out.println("Playing sound: " + soundId);
            if (soundId != null && !soundId.isEmpty()) {
                var id = new Identifier(soundId);
                var sound = new SpellCastingSound(this, id, 1 ,1);
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

    private boolean isMounting() {
        return this.getVehicle() != null;
    }

    public boolean isLeftHanded() {
        return this.getMainArm() == Arm.LEFT;
    }
}