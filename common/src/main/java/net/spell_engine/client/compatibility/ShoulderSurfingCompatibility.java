package net.spell_engine.client.compatibility;

import com.teamderpy.shouldersurfing.api.IShoulderSurfingPlugin;
import com.teamderpy.shouldersurfing.api.IShoulderSurfingRegistrar;
import net.minecraft.client.MinecraftClient;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.internals.SpellContainerHelper;

public class ShoulderSurfingCompatibility implements IShoulderSurfingPlugin {
    private static final int toleranceTicks = 3;
    private int lastTimeCasted = 0;
    @Override
    public void register(IShoulderSurfingRegistrar registrar) {
        registrar.registerAdaptiveItemCallback(itemStack -> {
            if (SpellContainerHelper.hasUsableContainer(itemStack)) {
                var player = MinecraftClient.getInstance().player;
                if (player != null & SpellEngineClient.config.shoulderSurfingAdaptiveWhileUse) {
                    var casting = player.isUsingItem();
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
