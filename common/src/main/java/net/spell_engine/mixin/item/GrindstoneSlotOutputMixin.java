package net.spell_engine.mixin.item;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.api.tags.SpellEngineItemTags;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// GrindstoneScreenHandler$4
//
// Anonymous mixin class to modify GrindstoneScreenHandler `new Slot(...) { ... }` instance
// Using `targets = ...` here due to being an anonymous class
// Class name found by clicking into the class, and using `Top menu > View > Show Bytecode`
@Mixin(targets = "net.minecraft.world.inventory.GrindstoneMenu$4")
public class GrindstoneSlotOutputMixin {

    @Nullable private Level world;

    @Inject(method = "getExperienceAmount(Lnet/minecraft/world/level/Level;)I", at = @At("HEAD"), cancellable = true)
    private void getExperience_enter_SpellEngine(Level world, CallbackInfoReturnable<Integer> cir) {
        this.world = world;
    }

    @Inject(method = "getExperienceFromItem(Lnet/minecraft/world/item/ItemStack;)I", at = @At("HEAD"), cancellable = true)
    private void getExperience_SpellEngine(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (this.world != null && stack.is(SpellEngineItemTags.GRINDABLE) ) {
            var container = SpellContainerHelper.containerFromItemStack(stack);
            int experience = 0;
            if (container != null) {
                var registry = SpellRegistry.from(world);
                for (var idString: container.spell_ids()) {
                    var id = Identifier.parse(idString);
                    var spellEntry = registry.get(id);
                    if (spellEntry.isPresent()) {
                        var spell = spellEntry.get().value();
                        if (spell.learn != null) {
                            experience += spell.tier * spell.learn.level_cost_per_tier;
                        }
                    }
                }
            }
            if (experience > 0) {
                cir.setReturnValue(experience);
                cir.cancel();
                this.world = null;
            }
        }
    }
}
