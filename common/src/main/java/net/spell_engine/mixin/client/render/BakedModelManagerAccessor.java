package net.spell_engine.mixin.client.render;

import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/// 1.20.1: `BakedModelManager` only exposes `getModel(ModelIdentifier)`, but the models both loaders
/// register as "additional" (Fabric `ModelLoadingPlugin.addModels`, Forge `ModelEvent.RegisterAdditional`)
/// are keyed by their plain `Identifier` in this map. Fabric API interface-injects a `getModel(Identifier)`
/// for it and Forge patches one in, but `common` can rely on neither — this accessor is the loader-neutral
/// lookup for `CustomModels` / `ItemRendererMixin`.
@Mixin(BakedModelManager.class)
public interface BakedModelManagerAccessor {
    @Accessor("models")
    Map<Identifier, BakedModel> SpellEngine_getModels();
}
