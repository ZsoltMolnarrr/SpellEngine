package net.spell_engine.entity;

public interface DamageSourceExtension {
    void setSpellIndirect(boolean implicitIndirect);
    boolean isSpellIndirect();
}
