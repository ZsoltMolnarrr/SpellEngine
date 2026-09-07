package net.spell_engine.mixin.client;

import net.minecraft.resource.ReloadableResourceManagerImpl;
import net.minecraft.resource.ResourceType;
import net.spell_engine.client.animation.AnimationRegistry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/// Reloads Spell Engine's player animations (see `AnimationRegistry`) whenever client resources reload,
/// so resource packs are honoured and the animation set is ready before the first cast.
///
/// Injected at TAIL: `reload` swaps in the new active manager *before* it starts the (asynchronous)
/// reloaders, so reading resources here already sees the new pack set.
///
/// The `type` guard matters in single player: the integrated server's data-pack manager is the same class
/// in the same JVM, and reloading against it would wipe the client's animations (assets are not visible to it).
@Mixin(ReloadableResourceManagerImpl.class)
public class ReloadableResourceManagerImplMixin {
    @Shadow @Final private ResourceType type;

    @Inject(method = "reload", at = @At("TAIL"))
    private void spellEngine_reloadAnimations(CallbackInfoReturnable<?> cir) {
        if (this.type != ResourceType.CLIENT_RESOURCES) {
            return;
        }
        AnimationRegistry.load((ReloadableResourceManagerImpl) (Object) this);
    }
}
