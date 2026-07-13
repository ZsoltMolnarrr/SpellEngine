package net.spell_engine.mixin.client.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.spell_engine.api.render.CustomLayers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.SequencedMap;

/**
 * Item glow layers depth test for `EQUAL`, so they may only be drawn once the item itself has
 * written its depth. Layers without a buffer of their own end up in the shared fallback buffer,
 * which `Immediate.draw` flushes *before* the ones in `layerBuffers` - the glow would be drawn
 * underneath the item and get painted over.
 * <p>
 * Giving them a buffer here appends them to `layerBuffers`, which is ordered, so they are drawn
 * last: after the item, and after the vanilla glint.
 */
@Mixin(VertexConsumerProvider.Immediate.class)
public class ImmediateItemGlowMixin {
    @Shadow
    @Final
    protected SequencedMap<RenderLayer, BufferAllocator> layerBuffers;

    @Inject(method = "getBuffer", at = @At("HEAD"))
    private void getBuffer_HEAD_SpellEngine_bufferItemGlowLayer(RenderLayer renderLayer, CallbackInfoReturnable<VertexConsumer> cir) {
        // Not every `Immediate` can take one: those built by `VertexConsumerProvider.immediate(allocator)`
        // hold an immutable empty map, and putting into one throws. The entity provider, the only one a
        // held item is ever drawn through, is built with a populated mutable map, so an empty map here
        // means we are somewhere we do not belong. Skipping costs the glow, not the game.
        if (layerBuffers.isEmpty() || layerBuffers.containsKey(renderLayer)) {
            return;
        }
        if (CustomLayers.isItemGlowLayer(renderLayer)) {
            layerBuffers.put(renderLayer, new BufferAllocator(renderLayer.getExpectedBufferSize()));
        }
    }
}
