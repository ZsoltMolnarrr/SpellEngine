package net.spell_engine.api.client;

import net.minecraft.util.Identifier;
import net.spell_engine.client.render.ProjectileModels;

import java.util.List;

public class Projectiles {
    public static void registerModelIds(List<Identifier> ids) {
        ProjectileModels.modelIds.addAll(ids);
    }
}
