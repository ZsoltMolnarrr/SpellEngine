package net.spell_engine.mixin.client.control;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(KeyBinding.class)
public interface KeybindingAccessor {
    @Invoker("reset")
    void spellEngine_reset();
    @Accessor
    InputUtil.Key getBoundKey();
}
