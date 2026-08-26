package net.spell_engine.mixin.client.control;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(KeyMapping.class)
public interface KeybindingAccessor {
    @Invoker("release")
    void spellEngine_reset();
    @Accessor("key")
    InputConstants.Key spellEngine_getBoundKey();
}
