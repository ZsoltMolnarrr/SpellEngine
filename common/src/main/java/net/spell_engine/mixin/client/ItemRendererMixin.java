package net.spell_engine.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.item.ItemModels;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.spell_engine.item.SpellEngineItems;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemRenderer.class)
public class ItemRendererMixin {
    @Shadow
    @Final
    private ItemModels models;

    @WrapOperation(method = "getModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/ItemModels;getModel(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/client/render/model/BakedModel;"))
    private BakedModel wrap_getModel(ItemModels instance, ItemStack stack, Operation<BakedModel> original) {
        if (stack.getItem() == SpellEngineItems.SCROLL.get()) {
            // var oriModel = original.call(instance, stack);
            var model = models.getModelManager().getModel(ModelIdentifier.ofInventoryVariant(Identifier.of("wizards:item/spell_scroll/fire")));
            return model;
        } else {
            return original.call(instance, stack);
        }
    }
}