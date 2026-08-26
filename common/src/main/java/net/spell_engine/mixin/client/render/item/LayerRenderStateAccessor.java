package net.spell_engine.mixin.client.render.item;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface LayerRenderStateAccessor {
    @Accessor("quads") List<BakedQuad> spellEngine_quads();
    @Accessor("renderType") RenderType spellEngine_renderLayer();
    @Accessor("transform") ItemTransform spellEngine_transform();
}
