package net.spell_engine.mixin.arrow;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.spell_engine.api.spell.SpellInfo;
import net.spell_engine.internals.SpellRegistry;
import net.spell_engine.internals.arrow.ArrowExtension;
import net.spell_engine.internals.casting.SpellCasterEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CrossbowItem.class)
public class CrossbowItemMixin {

    private static final String NBT_KEY_SPELL = "se_spell";

    @WrapOperation(method = "onStoppedUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/CrossbowItem;setCharged(Lnet/minecraft/item/ItemStack;Z)V"))
    private void onStoppedUsing_SpellEngine(
            // Mixin parameters
            ItemStack itemStack, boolean charged, Operation<Void> original,
            // Context parameters
            ItemStack itemStack2, World world, LivingEntity user, int remainingUseTicks) {
        if (user instanceof SpellCasterEntity caster) {
            var process = caster.getSpellCastProcess();
            if (process != null) {
                itemStack.getOrCreateNbt().putString(NBT_KEY_SPELL, process.id().toString());
            }
        }
        original.call(itemStack, charged);
    }

    @WrapOperation(method = "shoot", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z"))
    private static boolean shoot_SpellEngine(
            // Mixin parameters
            World worldParam, Entity projectileEntity, Operation<Boolean> original,
            // Context parameters
            World world, LivingEntity shooter, Hand hand, ItemStack crossbow, ItemStack projectile, float soundPitch, boolean creative, float speed, float divergence, float simulated
    ) {
        if (crossbow.getNbt() != null
                && crossbow.getNbt().contains(NBT_KEY_SPELL)
                && projectileEntity instanceof ArrowExtension arrow) {
            var id = new Identifier(crossbow.getNbt().getString(NBT_KEY_SPELL));
            var spell = SpellRegistry.getSpell(id);
            if (spell != null) {
                arrow.applyArrowPerks(new SpellInfo(spell, id));
            }
        }
        return original.call(worldParam, projectileEntity);
    }

    @Inject(method = "setCharged", at = @At("TAIL"))
    private static void setCharged_TAIL_SpellEngine(ItemStack stack, boolean charged, CallbackInfo ci) {
        if (!charged && stack.getNbt().contains(NBT_KEY_SPELL)) {
            stack.getNbt().remove(NBT_KEY_SPELL);
        }
    }
}
