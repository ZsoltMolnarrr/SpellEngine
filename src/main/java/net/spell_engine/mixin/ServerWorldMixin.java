package net.spell_engine.mixin;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.spell_engine.internals.WorldScheduler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin implements WorldScheduler {

    // MARK: WorldScheduler

    @Override
    public long getSchedulerTime() {
        return ((World)((Object)this)).getTime();
    }

    private Map<Long, List<Runnable>> scheduledTasks = new HashMap<>();

    @Override
    public Map<Long, List<Runnable>> getScheduledTasks() {
        return scheduledTasks;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tick_TAIL_SpellEngine(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        updateScheduledTasks();
    }


}
