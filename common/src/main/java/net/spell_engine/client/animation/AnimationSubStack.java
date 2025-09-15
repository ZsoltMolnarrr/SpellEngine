package net.spell_engine.client.animation;

import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AdjustmentModifier;
import dev.kosmx.playerAnim.api.layered.modifier.MirrorModifier;
import dev.kosmx.playerAnim.api.layered.modifier.SpeedModifier;

public class AnimationSubStack {
    public final SpeedModifier speed = new SpeedModifier();
    public final MirrorModifier mirror = new MirrorModifier();
    public final ModifierLayer base = new ModifierLayer(null);
    public AdjustmentModifier adjustment = null;

    public AnimationSubStack(AdjustmentModifier adjustmentModifier) {
        mirror.setEnabled(false);
        if (adjustmentModifier != null) {
            this.adjustment = adjustmentModifier;
            base.addModifier(adjustmentModifier, 0);
        }
        base.addModifier(speed, 0);
        base.addModifier(mirror, 0);
    }
}
