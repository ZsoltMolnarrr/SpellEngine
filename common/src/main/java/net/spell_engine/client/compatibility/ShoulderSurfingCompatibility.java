package net.spell_engine.client.compatibility;

import com.github.exopandora.shouldersurfing.api.client.event.ComputePlayerAimStateEvent;
import com.github.exopandora.shouldersurfing.api.client.event.handler.ComputePlayerAimStateEventHandler;
import com.github.exopandora.shouldersurfing.api.event.IEventBus;
import com.github.exopandora.shouldersurfing.api.plugin.IShoulderSurfingPlugin;
import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.internals.casting.SpellCaster;

/// Makes Shoulder Surfing treat spell casting exactly like drawing a bow.
///
/// Shoulder Surfing keeps a single `isAiming` flag, recomputed every client tick in
/// `ShoulderSurfing.tick()` from `ComputePlayerAimStateEvent` (fired via `EventHooks.isAiming`
/// for the current camera entity). That one flag drives the whole "bow drawn" presentation:
/// the ADAPTIVE crosshair turns dynamic, the aiming camera offset modifiers kick in, the
/// camera re-couples to the player, the player model turns transparent, and — for the
/// aiming-decoupled (STATIC) crosshair types — the player look follows the crosshair target.
/// Vanilla bows reach that same flag through Shoulder Surfing's own built-in handler, which
/// matches the *active* item's use animation (`BOW`) against its config list. Reporting `true`
/// here is therefore the identical code path, not an approximation of it.
///
/// IMPORTANT — plugin registration: `shouldersurfing_plugin.json` must use the `entrypoints`
/// ARRAY key. Shoulder Surfing 5.x still accepts the singular `entrypoint` key, but routes it
/// through `LegacyPluginAdapter`, which calls the 4.x-era `register(IShoulderSurfingRegistrar)`
/// method — a no-op default mixed into `IShoulderSurfingPlugin`. With the legacy key nothing
/// below is ever registered, and it fails silently.
public class ShoulderSurfingCompatibility implements IShoulderSurfingPlugin {
    /// Keeps the aiming state up for a few ticks after a cast ends, so that back-to-back casts
    /// (and the gap between predicted and server-confirmed processes) don't flicker the camera.
    private static final int toleranceTicks = 3;

    private int lastCastTick = Integer.MIN_VALUE;

    @Override
    public void register(IEventBus eventBus) {
        eventBus.register((ComputePlayerAimStateEventHandler) this::computeAimState);
    }

    private void computeAimState(ComputePlayerAimStateEvent event) {
        if (event.getResult()) {
            // Already aiming for another reason (bow, spyglass, another plugin)
            return;
        }
        LivingEntity entity = event.getEntity();
        // `SpellCaster.Client` is only implemented by the local player entity
        if (!(entity instanceof SpellCaster.Client caster)) { return; }
        if (!holdsSpellContainer(entity)) { return; }

        var config = SpellEngineClient.config;
        if (config == null || !config.shoulderSurfingAdaptiveWhileUse) {
            // Adaptive purely from holding a spell casting item
            event.setResult(true);
            return;
        }

        int age = entity.tickCount;
        if (age < lastCastTick) {
            // Player entity was replaced (respawn / world change), its age restarted
            lastCastTick = Integer.MIN_VALUE;
        }
        if (caster.getSpellCastProgress() != null) {
            lastCastTick = age;
        }
        if (lastCastTick != Integer.MIN_VALUE && (lastCastTick + toleranceTicks) > age) {
            event.setResult(true);
        }
    }

    private static boolean holdsSpellContainer(LivingEntity entity) {
        return SpellContainerHelper.hasUsableContainer(entity.getMainHandItem())
                || SpellContainerHelper.hasUsableContainer(entity.getOffhandItem());
    }
}
