package net.spell_engine.client.gui;

import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;

import java.util.Map;

public class HudKeyVisuals {
    private static final Drawable.Texture mouseTexture = new Drawable.Texture(new Identifier(SpellEngineMod.ID, "textures/gui/widgets.png"), 256, 256);
    public static Map<String, Drawable.Component> custom = Map.ofEntries(
            Map.entry("key.mouse.left", new Drawable.Component(
                    new Drawable.Draw(0, 0, 10, 12),
                    mouseTexture
            )),
            Map.entry("key.mouse.right", new Drawable.Component(
                    new Drawable.Draw(16, 0, 10, 12),
                    mouseTexture
            )),
            Map.entry("key.mouse.middle", new Drawable.Component(
                    new Drawable.Draw(32, 0, 10, 12),
                    mouseTexture
            )),
            Map.entry("key.mouse.4", new Drawable.Component(
                    new Drawable.Draw(0, 16, 10, 12),
                    mouseTexture
            )),
            Map.entry("key.mouse.5", new Drawable.Component(
                    new Drawable.Draw(16, 16, 10, 12),
                    mouseTexture
            ))
    );

    private static final int keyboardV = 32;
    public static final Drawable.Component buttonLeading = new Drawable.Component(
            new Drawable.Draw(0, keyboardV, 3, 12),
            mouseTexture
    );
    public static final Drawable.Component buttonCenter = new Drawable.Component(
            new Drawable.Draw(16, keyboardV, 16, 12),
            mouseTexture
    );
    public static final Drawable.Component buttonTrailing= new Drawable.Component(
            new Drawable.Draw(16-3, keyboardV, 3, 12),
            mouseTexture
    );
}
