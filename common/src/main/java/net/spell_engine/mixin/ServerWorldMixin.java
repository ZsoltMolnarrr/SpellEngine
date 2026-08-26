package net.spell_engine.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.spell_engine.utils.WorldScheduler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

@Mixin(ServerLevel.class)
public abstract class ServerWorldMixin implements WorldScheduler {

    // MARK: WorldScheduler

    @Override
    public long getSchedulerTime() {
        return ((Level)((Object)this)).getGameTime();
    }

    private Map<Long, List<Runnable>> scheduledTasks = new HashMap<>();
    @Override public Map<Long, List<Runnable>> getScheduledTasks() {
        return scheduledTasks;
    }

    private ArrayList<Runnable> immediateTasks = new ArrayList<>();
    @Override public ArrayList<Runnable> getImmediateTasks() {
        return immediateTasks;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tick_TAIL_SpellEngine(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        updateScheduledTasks();
    }


}
