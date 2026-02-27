package net.spell_engine.mixin.client;

import net.minecraft.client.render.item.ItemModels;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.world.World;
import net.spell_engine.Platform;
import net.spell_engine.api.spell.SpellDataComponents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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