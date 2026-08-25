package net.spell_engine.client.render;

import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class CustomModelRegistry {
    // NeoForge client initializers may run parallel, so use a thread-safe collection
    public static final ConcurrentLinkedQueue<Identifier> modelIds = new ConcurrentLinkedQueue<>();

    /// Explicitly registered ids (CustomModels.registerModelIds)
    public static Collection<Identifier> getModelIds() {
        return modelIds;
    }

    /// Explicitly registered + discovered (CustomModelDiscovery) ids, without duplicates
    public static Set<Identifier> allModelIds(Collection<Identifier> discovered) {
        var all = new LinkedHashSet<Identifier>(modelIds);
        all.addAll(discovered);
        return all;
    }
}
