package net.spell_engine.client.compatibility;

import com.github.exopandora.shouldersurfing.api.plugin.IShoulderSurfingPlugin;
import com.github.exopandora.shouldersurfing.api.plugin.IShoulderSurfingRegistrar;
import net.minecraft.client.MinecraftClient;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import net.spell_engine.internals.casting.SpellCaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Shoulder Surfing Reloaded integration: spell caster items drive the adaptive (bow-like) crosshair.
///
/// Discovered by Shoulder Surfing itself through `shouldersurfing_plugin.json` at the jar root, so there is
/// no runtime gate to write and nothing is loaded when Shoulder Surfing is absent.
///
/// Compiled against Shoulder Surfing `1.20.1-4.3.1` (the same API generation the 1.21.1 line uses). The 5.0.x
/// line replaced `IShoulderSurfingPlugin#register(IShoulderSurfingRegistrar)` with `register(IEventBus)`, but
/// ships an `api-legacy` mixin (`LegacyPluginAdapter`) that adapts 4.x-shaped plugins — so this class stays
/// compatible with both. Compiling against 5.0.x instead would break every 4.x user.
public class ShoulderSurfingCompatibility implements IShoulderSurfingPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("SpellEngine/ShoulderSurfingCompat");
    private static final int toleranceTicks = 3;
    private int lastTimeCasted = 0;
    @Override
    public void register(IShoulderSurfingRegistrar registrar) {
        LOGGER.info("Shoulder Surfing detected, registering adaptive item callback for spell caster items");
        registrar.registerAdaptiveItemCallback(itemStack -> {
            if (SpellContainerHelper.hasUsableContainer(itemStack)) {
                var player = MinecraftClient.getInstance().player;
                if (player != null & SpellEngineClient.config.shoulderSurfingAdaptiveWhileUse) {
                    var casting = ((SpellCaster.Client)player).getSpellCastProgress() != null;
                    if (casting) {
                        this.setTicks(player.age);
                    }
                    return (this.getTicks() + toleranceTicks) > player.age;
                } else {
                    return true;
                }
            }
            return false;
        });
    }

    private int getTicks() {
        return lastTimeCasted;
    }
    private void setTicks(int ticks) {
        this.lastTimeCasted = ticks;
    }
}
