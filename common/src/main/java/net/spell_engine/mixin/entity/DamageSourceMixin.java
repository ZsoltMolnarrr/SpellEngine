package net.spell_engine.mixin.entity;

import net.minecraft.entity.damage.DamageSource;
import net.spell_engine.entity.DamageSourceExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(DamageSource.class)
public class DamageSourceMixin implements DamageSourceExtension {
    @Unique
    private boolean implicitIndirect = false;
    @Override
    public void setSpellIndirect(boolean implicitIndirect) {
        this.implicitIndirect = implicitIndirect;
    }
    @Override
    public boolean isSpellIndirect() {
        return implicitIndirect;
    }
}
