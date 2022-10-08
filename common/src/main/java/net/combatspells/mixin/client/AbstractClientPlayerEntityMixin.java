package net.combatspells.mixin.client;

import com.mojang.authlib.GameProfile;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.impl.IAnimatedPlayer;
import net.combatspells.client.animation.AnimatablePlayer;
import net.combatspells.client.animation.AnimationRegistry;
import net.combatspells.client.animation.AnimationSubStack;
import net.combatspells.internals.SpellCasterEntity;
import net.combatspells.utils.StringUtil;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.encryption.PlayerPublicKey;
import net.minecraft.util.Arm;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin extends PlayerEntity implements AnimatablePlayer {
    public AbstractClientPlayerEntityMixin(World world, BlockPos pos, float yaw, GameProfile gameProfile, @Nullable PlayerPublicKey publicKey) {
        super(world, pos, yaw, gameProfile, publicKey);
    }

    private final AnimationSubStack castingAnimation = new AnimationSubStack(null);

    @Inject(method = "<init>", at = @At("TAIL"))
    private void postInit(ClientWorld world, GameProfile profile, PlayerPublicKey publicKey, CallbackInfo ci) {
        var stack = ((IAnimatedPlayer) this).getAnimationStack();
        stack.addAnimLayer(2100, castingAnimation.base);
    }

    @Override
    public void updateCastAnimationsOnTick() {
        var instance = (Object) this;
        var player = (PlayerEntity) instance;

        String castAnimationName = null;
        var spell = ((SpellCasterEntity)player).getCurrentSpell();
        if (spell != null) {
            castAnimationName = spell.cast.animation;

            ((LivingEntityAccessor)player).invokeTurnHead(player.getHeadYaw(), 0);
        }
        updateCastingAnimation(castAnimationName);
    }

    private String lastCastAnimationName;
    private void updateCastingAnimation(String animationName) {
        if (!StringUtil.matching(animationName, lastCastAnimationName)) {
            System.out.println("Playing animation: " + animationName);
            if (animationName != null && !animationName.isEmpty()) {
                var animation = AnimationRegistry.animations.get(animationName);
                var copy = animation.mutableCopy();
                // updateAnimationByCurrentActivity(copy);
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

    public boolean isLeftHanded() {
        return this.getMainArm() == Arm.LEFT;
    }
}