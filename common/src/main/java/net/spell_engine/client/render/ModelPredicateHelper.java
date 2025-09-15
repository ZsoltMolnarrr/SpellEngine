package net.spell_engine.client.render;

import net.minecraft.client.item.ModelPredicateProvider;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCasterEntity;
import net.spell_engine.mixin.client.render.ModelPredicateProviderRegistryAccessor;

import java.util.Map;

public class ModelPredicateHelper {
    public static void injectBowSkillUsePredicate(Item item) {
        var itemSpecificPredicates = ModelPredicateProviderRegistryAccessor.itemSpecificPredicates_SpellEngine();
        if (itemSpecificPredicates == null || itemSpecificPredicates.isEmpty()) {
            System.err.println("Spell Engine: Failed to inject vanilla model predicates.");
            return;
        }

        injectModelPredicate(itemSpecificPredicates, item, Identifier.of("pull"), (stack, world, entity, seed) -> {
            var progress = getItemStackRangedSkillProgress(stack, entity);
            if (progress != null) {
                return progress.ratio();
            }
            return -1F; // Negative value to fall back to vanilla
        });
        injectModelPredicate(itemSpecificPredicates, item, Identifier.of("pulling"), (stack, world, entity, seed) -> {
            if (isItemStackUsedForRangedSkill(stack, entity)) {
                return 1F;
            }
            return -1F; // Negative value to fall back to vanilla
        });
    }

    public static void injectCrossBowSkillUsePredicate(Item item) {
        var itemSpecificPredicates = ModelPredicateProviderRegistryAccessor.itemSpecificPredicates_SpellEngine();
        if (itemSpecificPredicates == null || itemSpecificPredicates.isEmpty()) {
            System.err.println("Spell Engine: Failed to inject vanilla model predicates.");
            return;
        }

        injectModelPredicate(itemSpecificPredicates, item, Identifier.of("pull"), (stack, world, entity, seed) -> {
            var progress = getItemStackRangedSkillProgress(stack, entity);
            if (progress != null) {
                return progress.ratio();
            }
            return -1F; // Negative value to fall back to vanilla
        });
        injectModelPredicate(itemSpecificPredicates, item, Identifier.of("pulling"), (stack, world, entity, seed) -> {
            if (isItemStackUsedForRangedSkill(stack, entity)) {
                return 1F;
            }
            return -1F; // Negative value to fall back to vanilla
        });
        injectModelPredicate(itemSpecificPredicates, item, Identifier.of("charged"), (stack, world, entity, seed) -> {
            var progress = getItemStackRangedSkillProgress(stack, entity);
            if (progress != null && progress.ratio() > 0.94F) {
                return 1F;
            }
            return -1F; // Negative value to fall back to vanilla
        });
    }

    private static void injectModelPredicate(Map<Item, Map<Identifier, ModelPredicateProvider>> all, Item item, Identifier id, ModelPredicateProvider customPredicate) {
        var itemSpecific = all.get(item);
        if (itemSpecific == null) {
            return;
        }
        var existingPredicate = itemSpecific.get(id);
        if (existingPredicate == null) {
            return;
        }
        ModelPredicateProviderRegistry.register(item, id, (stack, world, entity, seed) -> {
            if (customPredicate != null) {
                var result = customPredicate.call(stack, world, entity, seed);
                if (result >= 0.0f) {
                    return result;
                }
            }
            return existingPredicate.call(stack, world, entity, seed);
        });
    }

    private static SpellCast.Progress getItemStackRangedSkillProgress(ItemStack itemStack, LivingEntity entity) {
        if (entity instanceof SpellCasterEntity caster && entity.getMainHandStack() == itemStack) {
            var process = caster.getSpellCastProcess();
            // Watch out! This mode check is duplicated
            if (process != null && process.spell().value().active.cast.animates_ranged_weapon) {
                return process.progress(entity.getWorld().getTime());
            }
        }
        return null;
    }

    private static boolean isItemStackUsedForRangedSkill(ItemStack itemStack, LivingEntity entity) {
        if (entity instanceof SpellCasterEntity caster && entity.getMainHandStack() == itemStack) {
            var process = caster.getSpellCastProcess();
            // Watch out! This mode check is duplicated
            if (process != null && process.spell().value().active.cast.animates_ranged_weapon) {
                return true;
            }
        }
        return false;
    }
}
