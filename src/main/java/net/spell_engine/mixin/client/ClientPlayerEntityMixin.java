package net.spell_engine.mixin.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.spell_engine.api.effect.EntityActionsAllowed;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.gui.HudMessages;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.SpellRegistry;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCasterClient;
import net.spell_engine.network.Packets;
import net.spell_engine.utils.TargetHelper;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin implements SpellCasterClient {
    @Shadow @Final public ClientPlayNetworkHandler networkHandler;
    private List<Entity> targets = List.of();

    private ClientPlayerEntity player() {
        return (ClientPlayerEntity) ((Object) this);
    }

    private Entity firstTarget() {
        return targets.stream().findFirst().orElse(null);
    }


    @Override
    public Identifier getCurrentSpellId() {
        if (spellCastProcess != null) {
            return spellCastProcess.id();
        }
        return null;
    }

    @Override
    public Spell getCurrentSpell() {
        if (spellCastProcess != null) {
            return spellCastProcess.spell();
        }
        return null;
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
                id = newValue.id();
                speed = newValue.speed();
                length = newValue.length();
            }
            ClientPlayNetworking.send(
                    Packets.SpellCastSync.ID,
                    new Packets.SpellCastSync(id, speed, length).write()
            );
        }
    }

    public SpellCast.Attempt startSpellCast(ItemStack itemStack, Identifier spellId) {
        var caster = player();
        if (spellId == null) {
            this.cancelSpellCast();
            return SpellCast.Attempt.none();
        }
        var spell = SpellRegistry.getSpell(spellId);
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
                v2_cancelSpellCast(false);
            }
            var instant = spell.cast.duration <= 0;
            if (instant) {
                // Release instant spell
                this.v2_releaseSpellCast(new SpellCast.Process(spellId, spell, itemStack, 1, 0, caster.getWorld().getTime()),
                        SpellCast.Action.RELEASE);
            } else {
                // Start casting
                var details = SpellHelper.getCastTimeDetails(caster, spell);
                setSpellCastProcess(new SpellCast.Process(spellId, spell, itemStack, details.speed(), details.length(), caster.getWorld().getTime()), true);
            }
        }
        if (attempt.isFail()) {
            HudMessages.INSTANCE.castAttemptError(attempt);
        }
        return attempt;
    }

    @Override
    @Nullable public SpellCast.Process getSpellCastProcess() {
        return spellCastProcess;
    }

    @Nullable public SpellCast.Progress getSpellCastProgress() {
        if (spellCastProcess != null) {
            var player = player();
            return spellCastProcess.progress(player.getWorld().getTime());
        }
        return null;
    }

    public void cancelSpellCast() {
        v2_cancelSpellCast(true);
    }
    public void v2_cancelSpellCast(boolean syncProcess) {
        var process = spellCastProcess;
        if (process != null) {
            if (SpellHelper.isChanneled(process.spell())) {
                var player = player();
                var slot = findSlot(player, process.itemStack());
                var progress = process.progress(player.getWorld().getTime());
                ClientPlayNetworking.send(
                        Packets.SpellRequest.ID,
                        new Packets.SpellRequest(Hand.MAIN_HAND, SpellCast.Action.RELEASE, process.id(), slot, progress.ratio(), new int[]{}).write());
            }
        }

        setSpellCastProcess(null, syncProcess);
        targets = List.of();
    }

    private void v2_updateSpellCast() {
        var process = spellCastProcess;
        if (process != null) {
            var player = player();
            if (!player().isAlive()
                    || player.getMainHandStack() != process.itemStack()
                    || getCooldownManager().isCoolingDown(process.id())
                    || EntityActionsAllowed.isImpaired(player, EntityActionsAllowed.Player.CAST_SPELL, true)
            ) {
                cancelSpellCast();
                return;
            }

            targets = findTargets(process.spell());
            var spell = process.spell();
            var spellCastTicks = process.spellCastTicksSoFar(player.getWorld().getTime());
            if (SpellHelper.isChanneled(spell)) {
                // Is channel tick due?
                var offset = Math.round(spell.cast.channel_ticks * 0.5F);
                var currentTick = spellCastTicks + offset;
                var isDue = currentTick >= spell.cast.channel_ticks
                        && (currentTick % spell.cast.channel_ticks) == 0;
                if (isDue) {
                    // Channel spell
                    v2_releaseSpellCast(process, SpellCast.Action.CHANNEL);
                }
            } else {
                var isFinished = spellCastTicks >= process.length();
                if (isFinished) {
                    // Release spell
                    v2_releaseSpellCast(process, SpellCast.Action.RELEASE);
                }
            }
        } else {
            targets = List.of();
        }
    }

    private void v2_releaseSpellCast(SpellCast.Process process, SpellCast.Action action) {
        var caster = player();
        var spellId = process.id();
        var spell = process.spell();
        var slot = findSlot(caster, process.itemStack());
        var player = player();
        var progress = process.progress(player.getWorld().getTime());
        var release = spell.release.target;
        int[] targetIDs = new int[]{};
        switch (release.type) {
            case PROJECTILE, CURSOR, METEOR -> {
                var firstTarget = firstTarget();
                if (firstTarget != null) {
                    targetIDs = new int[]{ firstTarget.getId() };
                }
            }
            case AREA, BEAM -> {
                targetIDs = new int[targets.size()];
                int i = 0;
                for (var target : targets) {
                    targetIDs[i] = target.getId();
                    i += 1;
                }
            }
            case SELF -> {
            }
        }
        ClientPlayNetworking.send(
                Packets.SpellRequest.ID,
                new Packets.SpellRequest(Hand.MAIN_HAND, action, spellId, slot, progress.ratio(), targetIDs).write());
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
        if (targets == null) {
            return List.of();
        }
        return targets;
    }

    public Entity getCurrentFirstTarget() {
        return firstTarget();
    }

    private int findSlot(PlayerEntity player, ItemStack stack) {
        for(int i = 0; i < player.getInventory().size(); ++i) {
            ItemStack itemStack = player.getInventory().getStack(i);
            if (stack == itemStack) {
                return i;
            }
        }
        return -1;
    }

    private List<Entity> findTargets(Spell currentSpell) {
        var caster = player();
        var previousTargets = this.targets;
        List<Entity> targets = List.of();
        if (currentSpell == null) {
            return targets;
        }
        boolean fallbackToPreviousTargets = false;
        var targetingMode = SpellHelper.selectionTargetingMode(currentSpell);
        var targetType = currentSpell.release.target.type;
        var intents = SpellHelper.intents(currentSpell);
        Predicate<Entity> selectionPredicate = (target) -> {
            boolean intentAllows = false;
            for (var intent: intents) {
                intentAllows = intentAllows || TargetHelper.actionAllowed(targetingMode, intent, caster, target);
            }
            return !SpellEngineClient.config.filterInvalidTargets || intentAllows;
        };
        switch (targetType) {
            case AREA -> {
                targets = TargetHelper.targetsFromArea(caster, currentSpell.range, currentSpell.release.target.area, selectionPredicate);
                var area = currentSpell.release.target.area;
                if (area != null && area.include_caster) {
                    targets.add(caster);
                }
            }
            case BEAM -> {
                targets = TargetHelper.targetsFromRaycast(caster, currentSpell.range, selectionPredicate);
            }
            case CURSOR, PROJECTILE, METEOR -> {
                fallbackToPreviousTargets = targetType != Spell.Release.Target.Type.PROJECTILE; // All of these except `PROJECTILE`
                var target = TargetHelper.targetFromRaycast(caster, currentSpell.range, selectionPredicate);
                if (target != null) {
                    targets = List.of(target);
                } else {
                    targets = List.of();
                }
            }
            case SELF -> {
                // Nothing to do
            }
        }
        if (fallbackToPreviousTargets && SpellEngineClient.config.stickyTarget
                && targets.isEmpty()) {
            targets = previousTargets.stream()
                    .filter(entity -> {
                        return TargetHelper.isInLineOfSight(caster, entity);
                    })
                    .toList();
        }

        var cursor = currentSpell.release.target.cursor;
        if (cursor != null) {
            if (cursor.use_caster_as_fallback && targets.isEmpty()) {
                targets = List.of(caster);
            }
        }

        return targets;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void tick_TAIL_SpellEngine(CallbackInfo ci) {
        v2_updateSpellCast();
        var player = player();
        if (isBeaming()) {
            networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(
                    player.getX(), player.getY(), player.getZ(),
                    player.getYaw(), player.getPitch(),
                    player.isOnGround())
            );
        }
    }
}