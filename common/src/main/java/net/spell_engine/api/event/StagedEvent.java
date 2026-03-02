package net.spell_engine.api.event;

public final class StagedEvent<T> {
    public final Event<T> PRE = new Event<>();
    public final Event<T> POST = new Event<>();
}
