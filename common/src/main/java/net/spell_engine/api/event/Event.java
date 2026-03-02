package net.spell_engine.api.event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

public final class Event<T> {
    private boolean executing = false;
    private final List<T> handlers = new ArrayList<>();

    public void register(T listener) {
        handlers.add(listener);
    }

    public boolean isListened() {
        return !handlers.isEmpty();
    }

    public void invoke(Consumer<T> function) {
        if (executing) {
            return;
        }
        executing = true;
        for(var handler: handlers) {
            function.accept(handler);
        }
        executing = false;
    }

    public <R> @Nullable R invokeWithResult(Function<T, R> function) {
        if (executing) {
            return null;
        }
        executing = true;
        R result = null;
        for (var handler : handlers) {
            result = function.apply(handler);
            if (result != null) {
                break;
            }
        }
        executing = false;
        return result;
    }
}