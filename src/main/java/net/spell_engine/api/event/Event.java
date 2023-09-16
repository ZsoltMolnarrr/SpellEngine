package net.spell_engine.api.event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class Event<T> {
    private final List<T> handlers = new ArrayList<>();

    public void register(T listener) {
        handlers.add(listener);
    }

    public boolean isListened() {
        return !handlers.isEmpty();
    }

    public void invoke(Consumer<T> function) {
        for(var handler: handlers) {
            function.accept(handler);
        }
    }
}