package net.spell_engine.api.event;

import java.util.ArrayList;
import java.util.List;

public abstract class Event<T> {
    public abstract void register(T listener);

    public static class Proxy<T> extends Event<T> {
        public List<T> handlers = new ArrayList();
        @Override
        public void register(T listener) {
            handlers.add(listener);
        }
    }
}