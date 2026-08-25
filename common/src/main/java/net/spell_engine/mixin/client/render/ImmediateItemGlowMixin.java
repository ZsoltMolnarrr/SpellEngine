package net.spell_engine.mixin.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
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
 * its depth. Still true on 1.21.11: the render command queue orders *submissions*, but everything ends up
 * in one {@code Immediate}, whose {@code draw()} flushes the shared fallback buffer FIRST and the fixed
 * {@code layerBuffers} after it. A layer without a buffer of its own lands in that fallback and is drawn
 * before the item's entity layers - against a depth buffer the item has not touched yet, so `EQUAL`
 * rejects every fragment and only the lightmap bump of the glow is visible.
 * <p>
 * Giving the glow layer a buffer here appends it to {@code layerBuffers}, which is ordered, so it is
 * drawn last: after the item, and after the vanilla glint.
 */
@Mixin(VertexConsumerProvider.Immediate.class)
public class ImmediateItemGlowMixin {
    @Shadow @Final protected SequencedMap<RenderLayer, BufferAllocator> layerBuffers;

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
        // Only the entity provider: the GUI item path also draws through an `Immediate` with a populated map,
        // and a glow buffer appended there would flush the streaks over hotbar/inventory items.
        var entityProvider = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        if ((Object) this != entityProvider) {
            return;
        }
        layerBuffers.put(renderLayer, new BufferAllocator(renderLayer.getExpectedBufferSize()));
    }
}
