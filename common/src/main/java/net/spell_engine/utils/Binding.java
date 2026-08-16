package net.spell_engine.utils;

import java.util.function.Consumer;
import java.util.function.Supplier;

/// A two-way lens onto a value stored elsewhere (SwiftUI-Binding style): the holder of a
/// Binding reads and writes the value without knowing where it lives. Used e.g. by
/// `SpellCastInteractor` to store its synced state through the player's tracked data slots.
public record Binding<T>(Supplier<T> read, Consumer<T> write) {
    public T get() {
        return read.get();
    }

    public void set(T value) {
        write.accept(value);
    }
}
