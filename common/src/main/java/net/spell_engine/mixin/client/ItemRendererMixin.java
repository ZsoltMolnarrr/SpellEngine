package net.spell_engine.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemModels;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.world.World;
import net.spell_engine.Platform;
import net.spell_engine.api.spell.SpellDataComponents;
import net.spell_engine.client.render.ItemGlowRendering;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemRenderer.class)
public class ItemRendererMixin {
    @Shadow
    @Final
    private ItemModels models;

//    @WrapOperation(method = "getModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/ItemModels;getModel(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/client/render/model/BakedModel;"))
//    private BakedModel wrap_getModel(ItemModels instance, ItemStack stack, Operation<BakedModel> original) {
//        if (stack.getItem() == SpellEngineItems.SCROLL.get()) {
//            // var oriModel = original.call(instance, stack);
//            var model = models.getModelManager().getModel(ModelIdentifier.ofInventoryVariant(Identifier.of("wizards:item/spell_scroll/fire")));
//            return model;
//        } else {
//            return original.call(instance, stack);
//        }
//    }

    // MARK: Item glow

    /// The glow is resolved here, at the only point that sees both the item and who holds it, and parked
    /// in [ItemGlowRendering] for the rest of the render to pick up.
    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/world/World;III)V",
            at = @At("HEAD"))
    private void renderItem_HEAD_SpellEngine_beginItemGlow(LivingEntity entity, ItemStack item, ModelTransformationMode renderMode,
                                                           boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                           World world, int light, int overlay, int seed, CallbackInfo ci) {
        ItemGlowRendering.begin(entity, item);
    }

    /// Cleared so a held item never leaks its glow onto the next item drawn without a holder,
    /// such as one in the GUI, dropped on the ground, or hung in an item frame.
    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/world/World;III)V",
            at = @At("RETURN"))
    private void renderItem_RETURN_SpellEngine_endItemGlow(LivingEntity entity, ItemStack item, ModelTransformationMode renderMode,
                                                           boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                           World world, int light, int overlay, int seed, CallbackInfo ci) {
        ItemGlowRendering.end();
    }

    /// Raised before anything reads it, so that whichever path draws the item is lit by it. Vanilla
    /// carries this argument down to `renderBakedItemModel`; the Fabric Rendering API render context,
    /// which takes the item over from here (see below), reads the same argument as its base lightmap.
    @ModifyVariable(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int renderItem_HEAD_SpellEngine_litItemGlow(int light) {
        return ItemGlowRendering.light(light);
    }

    /// The glow rides on the consumer the item is drawn through, the way the vanilla glint does, and is
    /// attached where that consumer is built rather than where it is used.
    ///
    /// These two factories are the one thing every item render path has in common. Vanilla calls them
    /// from `renderItem` just before `renderBakedItemModel`, and `BuiltinModelItemRenderer` calls them
    /// for the models it draws itself. Crucially, so does Indigo, the Fabric Rendering API renderer: a
    /// model that answers `false` to `BakedModel.isVanillaAdapter()` is taken over by `ItemRenderContext`,
    /// which cancels vanilla's `renderItem` outright and buffers the quads itself - `renderBakedItemModel`
    /// is never reached, and a hook on it never runs. Continuity's `EmissiveBakedModel` answers `false`
    /// for every wrapped model whenever its emissive textures feature is on, and it wraps every model in
    /// the game once any texture in the pack has an emissive counterpart, so under it the glow used to
    /// disappear entirely. Hooking the consumer instead covers both paths, and any other renderer that
    /// respects them.
    ///
    /// Attaching the glow more than once per item is harmless: a quad passes through exactly one consumer,
    /// so it still reaches the glow layer exactly once. Indigo caches up to four of these per item, one
    /// per blend mode and glint combination.
    @ModifyReturnValue(method = "getItemGlintConsumer(Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/render/RenderLayer;ZZ)Lnet/minecraft/client/render/VertexConsumer;",
            at = @At("RETURN"))
    private static VertexConsumer getItemGlintConsumer_RETURN_SpellEngine_ItemGlow(
            VertexConsumer original, @Local(argsOnly = true) VertexConsumerProvider vertexConsumers) {
        return ItemGlowRendering.glowing(vertexConsumers, original);
    }

    @ModifyReturnValue(method = "getDirectItemGlintConsumer(Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/render/RenderLayer;ZZ)Lnet/minecraft/client/render/VertexConsumer;",
            at = @At("RETURN"))
    private static VertexConsumer getDirectItemGlintConsumer_RETURN_SpellEngine_ItemGlow(
            VertexConsumer original, @Local(argsOnly = true) VertexConsumerProvider vertexConsumers) {
        return ItemGlowRendering.glowing(vertexConsumers, original);
    }

    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    private void getModel_HEAD(ItemStack stack, World world, LivingEntity entity, int seed, CallbackInfoReturnable<BakedModel> cir){
        var modelId = stack.get(SpellDataComponents.ITEM_MODEL);
        if (modelId != null) {
            BakedModel model;
            if (Platform.Fabric) { // Not outsourcing to Platform, to avoid dedicated server issues
                model = models.getModelManager().getModel(modelId);
            } else {
                model = models.getModelManager().getModel(new ModelIdentifier(modelId, "standalone"));
            }
            if (model == null) {
                var item = Registries.ITEM.getEntry(modelId);
                if (item.isPresent()) {
                    model = models.getModel(item.get().value());
                }
            }
            if (model != null && model != models.getModelManager().getMissingModel()) {
                cir.setReturnValue(model);
                cir.cancel();
            }
        }
    }
}