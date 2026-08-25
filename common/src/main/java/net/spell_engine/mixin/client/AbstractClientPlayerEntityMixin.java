package net.spell_engine.mixin.client;

import com.mojang.authlib.GameProfile;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.spell_engine.api.spell.fx.Sound;
import net.spell_engine.client.animation.*;
import net.spell_engine.client.sound.SpellCastingSound;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCaster;
import net.spell_engine.mixin.entity.LivingEntityAccessor;
import net.spell_engine.fx.ParticleHelper;
import net.spell_engine.utils.AnimationHelper;
import net.spell_engine.utils.StringUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin extends PlayerEntity implements AnimatablePlayer, SpellCastingSound.Listener {
    public AbstractClientPlayerEntityMixin(World world, GameProfile gameProfile) {
        super(world, gameProfile);
    }

    // PAL owns the controllers (registered in SpellAnimationStack.registerFactories); fetched by id
    private SpellAnimationStack castingAnimation;
    private SpellAnimationStack releaseAnimation;
    private SpellAnimationStack miscAnimation;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void postInit_SpellEngine(ClientWorld world, GameProfile profile, CallbackInfo ci) {
        var player = (AbstractClientPlayerEntity) (Object) this;
        castingAnimation = (SpellAnimationStack) PlayerAnimationAccess.getPlayerAnimationLayer(player, SpellAnimationStack.CASTING_ID);
        releaseAnimation = (SpellAnimationStack) PlayerAnimationAccess.getPlayerAnimationLayer(player, SpellAnimationStack.RELEASE_ID);
        miscAnimation = (SpellAnimationStack) PlayerAnimationAccess.getPlayerAnimationLayer(player, SpellAnimationStack.MISC_ID);
    }

    @Override
    public void updateSpellCastAnimationsOnTick() {
        var instance = (Object) this;
        var player = (PlayerEntity) instance;

        String castAnimationName = null;
        Sound castSound = null;
        float speed = 1F;
        var spell = ((SpellCaster.Player)player).getCastedSpell();
        if (spell != null && spell.active != null) {
            var cast = spell.active.cast;
            castAnimationName = AnimationHelper.getAnimationId(player, cast.animation);
            castSound = cast.sound;
            // Rotate body towards look vector
            ((LivingEntityAccessor)player).spellEngine_invoke_TurnHead(player.getHeadYaw());
            for (var batch: cast.particles) {
                ParticleHelper.play(player.getEntityWorld(), player, player.getYaw(), getPitch(), batch);
            }
            speed = ((SpellCaster.Player)player).getCastingSpeed() * cast.animation.speed;
            castingAnimation.pitching = cast.animation_pitch;
        } else {
            castingAnimation.pitching = true;
        }
        updateCastingAnimation(castAnimationName, speed);
        updateCastingSound(castSound);
    }

    private String lastCastAnimationName;
    private void updateCastingAnimation(String animationName, float speed) {
        if (!StringUtil.matching(animationName, lastCastAnimationName)) {
            playSpellAnimation(SpellCast.Animation.CASTING, animationName, speed);
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
            if (lastCastSound != null) {
                MinecraftClient.getInstance().getSoundManager().stop(lastCastSound);
                lastCastSound = null;
            }
            if (castSound != null && soundId != null && !soundId.isEmpty()) {
                var id = Identifier.of(soundId);
                var sound = new SpellCastingSound(this, id, castSound.volume(), castSound.randomizedPitch());
                sound.listener = this;
                MinecraftClient.getInstance().getSoundManager().play(sound);
                lastCastSound = sound;
            }
        }
        lastCastSoundId = soundId;
    }

    public void onSpellCastingSoundDone() {
        lastCastSound = null;
        lastCastSoundId = null;
    }

    public void playSpellAnimation(SpellCast.Animation type, String name, float speed) {
        try {
            var stack = spellAnimationStackFor(type);
            if (stack == null) { return; }
            if (name != null && !name.isEmpty()) {
                var mirror = isLeftHanded_SpellEngine();
                if (type == SpellCast.Animation.MISC) {
                    mirror = getEntityWorld().getRandom().nextBoolean();
                }
                stack.play(Identifier.of(name), mirror, speed);
            } else {
                stack.stopWithFade();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private SpellAnimationStack spellAnimationStackFor(SpellCast.Animation type) {
        return switch (type) {
            case CASTING -> castingAnimation;
            case RELEASE -> releaseAnimation;
            case MISC -> miscAnimation;
        };
    }

    public boolean isLeftHanded_SpellEngine() {
        return this.getMainArm() == Arm.LEFT;
    }
}