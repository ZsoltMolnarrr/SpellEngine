package net.spell_engine.api.event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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
}