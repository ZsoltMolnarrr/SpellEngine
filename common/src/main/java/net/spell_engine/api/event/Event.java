package net.spell_engine.api.event;

import java.util.ArrayList;
import java.util.List;

public class Event<T> {
    public List<T> handlers = new ArrayList();
    public void register(T listener) {
        handlers.add(listener);
    }
}