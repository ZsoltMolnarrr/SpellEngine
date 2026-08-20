package net.spell_engine.utils;

import java.util.function.Consumer;
import java.util.function.Supplier;

/// A two-way accessor onto a value stored elsewhere: the holder of a Binding reads and
/// writes the value without knowing where it lives.
public record Binding<T>(Supplier<T> read, Consumer<T> write) {
    public T get() {
        return read.get();
    }

    public void set(T value) {
        write.accept(value);
    }
}
