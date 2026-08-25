package net.spell_engine.client.compatibility;

import com.github.exopandora.shouldersurfing.api.client.event.ComputePlayerAimStateEvent;
import com.github.exopandora.shouldersurfing.api.event.IEventBus;
import com.github.exopandora.shouldersurfing.api.plugin.IShoulderSurfingPlugin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import net.spell_engine.internals.casting.SpellCaster;

/// Shoulder Surfing 5.x: the "adaptive item" registrar became an event bus; the aim-state event decides
/// whether the camera should switch to the adaptive (aiming) mode for the held item.
public class ShoulderSurfingCompatibility implements IShoulderSurfingPlugin {
    private static final int toleranceTicks = 3;
    private int lastTimeCasted = 0;

    @Override
    public void register(IEventBus eventBus) {
        eventBus.register((ComputePlayerAimStateEvent event) -> {
            if (!(event.getEntity() instanceof PlayerEntity player)) { return; }
            var itemStack = player.getMainHandStack();
            if (!SpellContainerHelper.hasUsableContainer(itemStack)) {
                itemStack = player.getOffHandStack();
                if (!SpellContainerHelper.hasUsableContainer(itemStack)) { return; }
            }
            if (SpellEngineClient.config.shoulderSurfingAdaptiveWhileUse) {
                var clientPlayer = MinecraftClient.getInstance().player;
                if (clientPlayer == null) { return; }
                var casting = ((SpellCaster.Client) clientPlayer).getSpellCastProgress() != null;
                if (casting) {
                    this.lastTimeCasted = clientPlayer.age;
                }
                if ((this.lastTimeCasted + toleranceTicks) > clientPlayer.age) {
                    event.setResult(true);
                }
            } else {
                event.setResult(true);
            }
        });
    }
}
