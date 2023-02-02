package net.spell_engine.client.compatibility;

import com.teamderpy.shouldersurfing.api.IShoulderSurfingPlugin;
import com.teamderpy.shouldersurfing.api.IShoulderSurfingRegistrar;
import net.spell_engine.internals.SpellContainerHelper;

public class ShoulderSurfingCompatibility implements IShoulderSurfingPlugin {
    @Override
    public void register(IShoulderSurfingRegistrar registrar) {
        registrar.registerAdaptiveItemCallback(SpellContainerHelper::hasUsableContainer);
    }
}
