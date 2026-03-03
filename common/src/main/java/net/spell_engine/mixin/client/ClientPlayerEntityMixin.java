package net.spell_engine.mixin.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.effect.EntityActionsAllowed;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.PlatformClient;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.animation.AnimatablePlayer;
import net.spell_engine.client.input.SpellHotbar;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCasterClient;
import net.spell_engine.internals.melee.Melee;
import net.spell_engine.internals.melee.OrientedBoundingBox;
import net.spell_engine.internals.target.SpellTarget;
import net.spell_engine.network.Packets;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin implements SpellCasterClient {
    @Shadow @Final public ClientPlayNetworkHandler networkHandler;
    private SpellTarget.SearchResult spellTarget = SpellTarget.SearchResult.empty();

    private ClientPlayerEntity player() {
        return (ClientPlayerEntity) ((Object) this);
    }

    private Entity firstTarget() {
        return spellTarget.entities().stream().findFirst().orElse(null);
    }

    @Override
    @Nullable public SpellCast.Process getSpellCastProcess() {
        return spellCastProcess;
    }

    @Override
    public Spell getCurrentSpell() {
        if (spellCastProcess != null) {
            return spellCastProcess.spell().value();
        }
        return null;
    }

    @Override
    public float getCurrentCastingSpeed() {
        if (spellCastProcess != null) {
            return spellCastProcess.speed();
        }
        return 1F;
    }

    public boolean isCastingSpell() {
        return spellCastProcess != null;
    }

    @Nullable private SpellCast.Process spellCastProcess;

    private void setSpellCastProcess(SpellCast.Process newValue, boolean sync) {
        var oldValue = spellCastProcess;
        spellCastProcess = newValue;
        if (sync && !Objects.equals(oldValue, newValue)) {
            Identifier id = null;
            float speed = 0;
            int length = 0;
            if (newValue != null) {
                id = newValue.spell().getKey().get().getValue();
                speed = newValue.speed();
                length = newValue.length();
            }
            ClientPlayNetworking.send(new Packets.SpellCastSync(id, speed, length));
        }
    }

    public SpellCast.Attempt startSpellCast(ItemStack itemStack, RegistryEntry<Spell> spellEntry) {
        var caster = player();
        if (caster.isSpectator()) {
            return SpellCast.Attempt.none();
        }
        if (spellEntry == null) {
            this.cancelSpellCast();
            return SpellCast.Attempt.none();
        }
        var spell = spellEntry.value();
        var spellId = spellEntry.getKey().get().getValue();
        if ((spellCastProcess != null && spellCastProcess.id().equals(spellId))
                || spell == null) {
            return SpellCast.Attempt.none();
        }
        if (EntityActionsAllowed.isImpaired(caster, EntityActionsAllowed.Player.CAST_SPELL, true)) {
            return SpellCast.Attempt.none();
        }
        var attempt = SpellHelper.attemptCasting(caster, itemStack, spellId);
        if (attempt.isSuccess()) {
            if (spellCastProcess != null) {
                // Cancel previous spell
                cancelSpellCast(false);
            }
            var instant = SpellHelper.isInstantCast(spellEntry, caster);
            if (instant) {
                // Release instant spell
                var process = new SpellCast.Process(caster, spellEntry, itemStack.getItem(), 1, 0, caster.getWorld().getTime());
                this.setSpellCastProcess(process, false);
                this.updateSpellCast();
                applyInstantGlobalCooldown();
            } else {
                // Start casting
                var details = SpellHelper.getCastTimeDetails(caster, spell);
                setSpellCastProcess(new SpellCast.Process(caster, spellEntry, itemStack.getItem(), details.speed(), details.length(), caster.getWorld().getTime()), true);
            }
        }
        return attempt;
    }

    private void applyInstantGlobalCooldown() {
        var duration = SpellEngineMod.config.spell_instant_cast_global_cooldown;
        if (duration > 0) {
            for (var slot: SpellHotbar.INSTANCE.slots) {
                var spellEntry = slot.spell();
                if (spellEntry == null) {
                    // Some slots may not have spells (such as item usage bypass slot)
                    continue;
                }
                var spell = spellEntry.value();
                if (spell.active != null && spell.active.cast != null && spell.active.cast.duration <= 0) {
                    getCooldownManager().set(spellEntry, duration, false);
                }
            }
        }
    }

    @Nullable public SpellCast.Progress getSpellCastProgress() {
        if (spellCastProcess != null) {
            var player = player();
            return spellCastProcess.progress(player.getWorld().getTime());
        }
        return null;
    }

    public void cancelSpellCast() {
        cancelSpellCast(true);
    }
    public void cancelSpellCast(boolean syncProcess) {
        var process = spellCastProcess;
        if (process != null) {
            if (SpellHelper.isChanneled(process.spell().value())) {
                var player = player();
                var progress = process.progress(player.getWorld().getTime());
                ClientPlayNetworking.send(new Packets.SpellRequest(SpellCast.Action.RELEASE, process.id(), progress.ratio(), new int[]{}, null));
            }
        }

        setSpellCastProcess(null, syncProcess);
        spellTarget = SpellTarget.SearchResult.empty();
    }

    private void updateSpellCast() {
        var process = spellCastProcess;
        if (process != null) {
            var player = player();
            if (!player().isAlive()
                    || player.getMainHandStack().getItem() != process.item()
                    || getCooldownManager().isCoolingDown(process.spell())
                    || EntityActionsAllowed.isImpaired(player, EntityActionsAllowed.Player.CAST_SPELL, true)
            ) {
                cancelSpellCast();
                return;
            }
            var spell = process.spell().value();
            var cast = spell.active.cast;
            spellTarget = SpellTarget.findTargets(player, process.spell(), spellTarget, SpellEngineClient.config.filterInvalidTargets);

            if (SpellHelper.isChanneled(spell)) {
                // System.out.println("Channeling tick: " + process.spellCastTicksSoFar(player.getWorld().getTime()) + " ticks, isDue: " + process.isDue(player.getWorld().getTime()));
                if (process.isDue(player.getWorld().getTime())) {
                    process.markDue();
                    releaseSpellCast(process, SpellCast.Action.CHANNEL);
                }
                var progress = process.progress(player.getWorld().getTime());
                if (progress.ratio() >= 1) {
                    cancelSpellCast();
                }
            } else {
                var spellCastTicks = process.spellCastTicksSoFar(player.getWorld().getTime());
                var isFinished = spellCastTicks >= process.length();
                if (isFinished) {
                    // Release spell
                    releaseSpellCast(process, SpellCast.Action.RELEASE);
                }
            }
        } else {
            spellTarget = SpellTarget.SearchResult.empty();
        }
    }

    private void releaseSpellCast(SpellCast.Process process, SpellCast.Action action) {
        var spellId = process.id();
        var player = player();
        var progress = process.progress(player.getWorld().getTime());
        var targets = spellTarget.entities();
        var location = spellTarget.location();
        int[] targetIDs = new int[targets.size()];
        int i = 0;
        for (var target : targets) {
            targetIDs[i] = target.getId();
            i += 1;
        }

        ClientPlayNetworking.send(new Packets.SpellRequest(action, spellId, progress.ratio(), targetIDs, location));
        switch (action) {
            case CHANNEL -> {
                if (progress.ratio() >= 1) {
                    cancelSpellCast();
                }
            }
            case RELEASE -> {
                cancelSpellCast();
            }
        }
    }

    public List<Entity> getCurrentTargets() {
        var targets = spellTarget.entities();
        if (targets == null) {
            return List.of();
        }
        return targets;
    }

    public Entity getCurrentFirstTarget() {
        return firstTarget();
    }


    private Melee.ActiveAttack currentAttack = null;
    private List<Melee.Attack> scheduledAttacks = new ArrayList<>();
    public void onAttacksAvailable(List<Melee.Attack> attacks) {
        scheduledAttacks.addAll(attacks);
    }
    public Melee.ActiveAttack getCurrentSkillAttack() {
        return currentAttack;
    }
    @Unique
    private void onTick_ScheduledAttacks(ClientPlayerEntity player) {
        var time = player.age;
        if (EntityActionsAllowed.isImpaired(player, EntityActionsAllowed.Player.ATTACK)) {
            currentAttack = null;
            return;
        }
        checkForNextAttack(player, time);
        if (currentAttack != null) {
            if (currentAttack.weapon != player.getMainHandStack().getItem()) {
                // Weapon changed, cancel attack
                currentAttack = null;
                return;
            }
            if (currentAttack.isDue(time)) {
                onAttackHit(currentAttack);
            }
            if (currentAttack.isFinished(time)) {
                currentAttack = null;
                checkForNextAttack(player, time);
            }
        }
    }
    private void checkForNextAttack(ClientPlayerEntity player, int time) {
        if (currentAttack == null) {
            if (!scheduledAttacks.isEmpty()) {
                var attack = scheduledAttacks.remove(0);
                currentAttack = new Melee.ActiveAttack(attack, time, player.getMainHandStack().getItem());
                onAttackActivated(attack);
            }
        }
    }

    @Unique
    private void onAttackActivated(Melee.Attack attack) {
        // On attack started

        var player = player();
        var momentum = attack.forward_momentum();
        if (momentum > 0
                && (attack.allow_momentum_airborne() || player.isOnGround()) ) {
            var direction = new Vec3d(0, 0, 1)
                    .rotateY((float) Math.toRadians((-1.0) * player.getYaw()))
                    .multiply(attack.forward_momentum());
            player.addVelocity(direction.x, direction.y, direction.z);
        }

        if (attack.context() != null) {
            var animationSpeed = attack.speed() * attack.animation().speed;
            ((AnimatablePlayer)this).playSpellAnimation(SpellCast.Animation.RELEASE, attack.animation().id, animationSpeed);
            var packet = new Packets.AttackFxBroadcast(attack.context());
            ClientPlayNetworking.send(packet);
        }
    }
    @Unique
    private void onAttackHit(Melee.ActiveAttack activeAttack) {
        var player = player();
        var attack = activeAttack.attack;
        var targets = Melee.detectTargets(player, attack);
        if (!attack.additional_hits_on_same_target()) {
            targets = targets.stream().filter(id -> !activeAttack.hitEntityIds.contains(id)).toList();
        }
        activeAttack.hitEntityIds.addAll(targets);
        if (!targets.isEmpty()) {
            var targetIds = targets.stream().mapToInt(Integer::intValue).toArray();
            var context = attack.context() != null ? attack.context() : Melee.AttackContext.EMPTY;
            var packet = new Packets.AttackPerform(context, targetIds);
            ClientPlayNetworking.send(packet);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tick_TAIL_SpellEngine(CallbackInfo ci) {
        updateSpellCast();
        var player = player();
        if (isBeaming()) {
            PlatformClient.util().sendVanillaPacket_C2S(player, new PlayerMoveC2SPacket.Full(
                    player.getX(), player.getY(), player.getZ(),
                    player.getYaw(), player.getPitch(),
                    player.isOnGround())
            );
        }
        onTick_ScheduledAttacks(player);
    }
}