package net.spell_engine.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumers;
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
import net.spell_engine.api.effect.GlowingItemStatusEffect;
import net.spell_engine.api.render.CustomLayers;
import net.spell_engine.api.spell.SpellDataComponents;
import net.spell_engine.client.compatibility.ShaderCompatibility;
import net.spell_engine.client.util.Color;
import net.spell_engine.client.util.ItemGlowVertexConsumer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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

    /// The overload that decides lighting and builds the glint consumer takes no entity, so the holder
    /// is carried over from the entity aware overload it is called from. Rendering happens on a single
    /// thread, and the two calls are directly nested, so an instance field suffices.
    @Unique
    @Nullable
    private LivingEntity SpellEngine_itemGlowHolder;

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/world/World;III)V",
            at = @At("HEAD"))
    private void renderItem_HEAD_SpellEngine_captureGlowHolder(LivingEntity entity, ItemStack item, ModelTransformationMode renderMode,
                                                               boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                               World world, int light, int overlay, int seed, CallbackInfo ci) {
        SpellEngine_itemGlowHolder = entity;
    }

    /// Cleared so a held item never leaks its glow onto the next item drawn without a holder,
    /// such as one in the GUI, dropped on the ground, or hung in an item frame.
    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/world/World;III)V",
            at = @At("RETURN"))
    private void renderItem_RETURN_SpellEngine_clearGlowHolder(LivingEntity entity, ItemStack item, ModelTransformationMode renderMode,
                                                               boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                               World world, int light, int overlay, int seed, CallbackInfo ci) {
        SpellEngine_itemGlowHolder = null;
    }

    @WrapOperation(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/ItemRenderer;renderBakedItemModel(Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/item/ItemStack;IILnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;)V"))
    private void wrap_renderBakedItemModel_SpellEngine_ItemGlow(ItemRenderer instance, BakedModel model, ItemStack stack, int light, int overlay,
                                                                MatrixStack matrices, VertexConsumer vertices, Operation<Void> original,
                                                                @Local(argsOnly = true) VertexConsumerProvider vertexConsumers) {
        var holder = SpellEngine_itemGlowHolder;
        var glow = holder != null ? GlowingItemStatusEffect.resolve(holder) : null;
        if (glow == null) {
            original.call(instance, model, stack, light, overlay, matrices, vertices);
            return;
        }

        // Lit up from within, scaled by opacity, so a faint glow warms the item rather than
        // flipping it to full bright all at once. Sky light is left alone, it is not ours to raise.
        var litLight = LightmapTextureManager.pack(
                Math.max(LightmapTextureManager.getBlockLightCoordinates(light), Math.round(15 * glow.alpha())),
                LightmapTextureManager.getSkyLightCoordinates(light));

        // Drawn into the glow layer alongside the item's own, the way the glint is
        var glowing = VertexConsumers.union(vertexConsumers.getBuffer(CustomLayers.itemGlow(glow)), vertices);

        // Bloom is a shader pack's doing, not the game's, and it only blooms what it reads as emissive.
        // Without a pack, this pass would only wash the item out with a flat coat and buy nothing.
        if (ShaderCompatibility.isShaderPackInUse()) {
            // Opacity is folded into the color, as it is for the shimmer: the blend adds the source
            // outright, so alpha is not a factor in it, and stacks would otherwise not dim the coat at all.
            var tint = new Color(
                    glow.red() * glow.alpha(),
                    glow.green() * glow.alpha(),
                    glow.blue() * glow.alpha(),
                    1F);
            var emissive = new ItemGlowVertexConsumer(vertexConsumers.getBuffer(CustomLayers.itemGlowEmissive()), tint);
            glowing = VertexConsumers.union(glowing, emissive);
        }

        original.call(instance, model, stack, litLight, overlay, matrices, glowing);
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