package net.spell_engine.mixin.client.render.item;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.Transformation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ItemRenderState.LayerRenderState.class)
public interface LayerRenderStateAccessor {
    @Accessor("quads") List<BakedQuad> spellEngine_quads();
    @Accessor("renderLayer") RenderLayer spellEngine_renderLayer();
    @Accessor("transform") Transformation spellEngine_transform();
}
