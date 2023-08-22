package net.spell_engine.internals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
public interface WorldScheduler {

    // World functions
    long getSchedulerTime();
    // Additions
    Map<Long, List<Runnable>> getScheduledTasks();

    default void schedule(int ticks, Runnable task) {
        if (ticks <= 0) {
            throw new IllegalArgumentException("Cannot schedule a task for 0 or less ticks");
        }
        long executionTime = getSchedulerTime() + ticks;
        var list = getScheduledTasks().getOrDefault(executionTime, new ArrayList<>());
        list.add(task);
        getScheduledTasks().put(executionTime, list);
    }

    default void updateScheduledTasks() {
        var taskQueue = getScheduledTasks();
        if (taskQueue.isEmpty()) {
            return;
        }
        var currentTime = getSchedulerTime();
        var currentTasks = taskQueue.get(currentTime);
        if (currentTasks != null) {
            for (Runnable task : currentTasks) {
                task.run();
            }
            taskQueue.remove(currentTime);
        }
    }
}
