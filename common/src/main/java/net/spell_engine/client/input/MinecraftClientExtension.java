package net.spell_engine.client.input;

public interface MinecraftClientExtension {
    boolean isSpellCastLockActive();
    void onSpellHotbarInputHandled(SpellHotbar.Handle handled);
}
