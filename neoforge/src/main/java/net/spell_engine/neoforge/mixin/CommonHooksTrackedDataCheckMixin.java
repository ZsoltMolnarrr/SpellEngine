package net.spell_engine.neoforge.mixin;

import net.neoforged.neoforge.common.CommonHooks;
import net.spell_engine.SpellEngineMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/// NeoForge 21.11 `CommonHooks.verifyEntityDataAccessorRegistration` rejects `TrackedData` registered on a
/// vanilla entity class by a mixin: a warning in production, but a hard `IllegalStateException` while running
/// in the IDE/dev. Spell Engine registers a few such entries (`PlayerEntityMixin`, `LivingEntityStatusEffectSync`,
/// `PersistentProjectileEntityMixin`) — a pattern carried over from 1.21.1 — so this keeps dev runs behaving like
/// production for *our own* mixins only: if every synced-data field merged into the entity comes from
/// `net.spell_engine.mixin`, the check is skipped (and the warning is logged once). Other mods' mixins are left to
/// NeoForge's own verdict.
///
/// TODO(post-migration): replace these mixin-registered TrackedData entries with data attachments (NeoForge) /
/// custom sync packets, then delete this mixin.
@Mixin(value = CommonHooks.class, remap = false)
public class CommonHooksTrackedDataCheckMixin {
    private static final Set<Class<?>> spellEngine$checked = new HashSet<>();
    private static final String spellEngine$MIXIN_PACKAGE = "net.spell_engine.mixin.";

    @Inject(method = "verifyEntityDataAccessorRegistration", at = @At("HEAD"), cancellable = true, remap = false)
    private static void spellEngine$allowOwnTrackedData(Class<?> callerClass, Class<?> holderClass, CallbackInfo ci) {
        if (callerClass != holderClass) { return; }
        var mixins = Arrays.stream(callerClass.getDeclaredFields())
                .filter(field -> net.minecraft.entity.data.TrackedData.class.isAssignableFrom(field.getType()))
                .map(field -> field.getAnnotation(MixinMerged.class))
                .filter(Objects::nonNull)
                .map(MixinMerged::mixin)
                .collect(java.util.stream.Collectors.toSet());
        if (mixins.isEmpty() || !mixins.stream().allMatch(name -> name.startsWith(spellEngine$MIXIN_PACKAGE))) {
            return;
        }
        if (spellEngine$checked.add(callerClass)) {
            SpellEngineMod.LOGGER.warn("Spell Engine registers synced data on {} via mixin ({}); tolerated in dev, same as NeoForge's production behaviour",
                    holderClass.getName(), String.join(", ", mixins));
        }
        ci.cancel();
    }
}
