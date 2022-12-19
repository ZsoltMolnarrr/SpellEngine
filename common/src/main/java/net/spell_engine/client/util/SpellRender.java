package net.spell_engine.client.util;

import net.minecraft.util.Identifier;

public class SpellRender {
    // Example: `spell_engine:fireball` -> `spell_engine:textures/spell/fireball.png`
    public static Identifier iconTexture(Identifier spellId) {
        return new Identifier(spellId.getNamespace(), "textures/spell/" + spellId.getPath() + ".png");
    }
}
