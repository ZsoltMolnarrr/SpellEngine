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
 * Item glow layers depth test for `EQUAL`, so they may only be drawn once the item itself has written
 * its depth. A layer without a buffer of its own ends up in the shared fallback, which `Immediate.draw`
 * flushes *before* the ones in `layerBuffers` - the glow would be drawn against a depth buffer the item
 * has not touched yet, and `EQUAL` would reject every fragment of it.
 * <p>
 * Giving them a buffer here appends them to `layerBuffers`, which is ordered, so they are drawn last:
 * after the item, and after the vanilla glint.
 * <p>
 * This is the vanilla path only. Iris substitutes a buffer source that overrides `getBuffer` outright,
 * so none of this runs under a shader pack, and the ordering has to be spelled out for it in its own
 * terms instead - see `IrisCompatibility`.
 */
@Mixin(VertexConsumerProvider.Immediate.class)
public class ImmediateItemGlowMixin {
    @Shadow
    @Final
    protected SequencedMap<RenderLayer, BufferAllocator> layerBuffers;

    @Inject(method = "getBuffer", at = @At("HEAD"))
    private void getBuffer_HEAD_SpellEngine_bufferItemGlowLayer(RenderLayer renderLayer, CallbackInfoReturnable<VertexConsumer> cir) {
        // An `Immediate` built by `VertexConsumerProvider.immediate(allocator)` holds an immutable empty
        // map, and putting into one throws. The entity provider, the only one a held item is drawn
        // through, is built with a populated mutable map, so an empty map here means we do not belong.
        if (layerBuffers.isEmpty()
                || layerBuffers.containsKey(renderLayer)
                || !CustomLayers.isItemGlowLayer(renderLayer)) {
            return;
        }
        layerBuffers.put(renderLayer, new BufferAllocator(renderLayer.getExpectedBufferSize()));
    }
}
