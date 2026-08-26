package net.spell_engine.mixin.client.control;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientInput.class)
public interface InputAccessor {
    @Accessor("moveVector")
    Vec2 spellEngine_getMovementVector();

    @Accessor("moveVector")
    void spellEngine_setMovementVector(Vec2 vector);
}
