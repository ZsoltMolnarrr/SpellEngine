package net.spell_engine.mixin.entity;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.spell_engine.internals.casting.SpellBatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(Player.class)
public class PlayerEntitySpellBatching implements SpellBatcher {
    @Unique
    private final Map<Identifier, Batch> spellBatches = new HashMap<>();
    @Override
    public Map<Identifier, Batch> getSpellBatches() {
        return spellBatches;
    }

    @Inject(at = @At("TAIL"), method = "tick")
    private void tick_TAIL_spell_engine_spell_batching(CallbackInfo ci) {
        spellBatches.clear();
    }
}
