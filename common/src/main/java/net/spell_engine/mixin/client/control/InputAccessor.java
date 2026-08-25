package net.spell_engine.mixin.client.control;

import net.minecraft.client.input.Input;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Input.class)
public interface InputAccessor {
    @Accessor("movementVector")
    Vec2f spellEngine_getMovementVector();

    @Accessor("movementVector")
    void spellEngine_setMovementVector(Vec2f vector);
}
