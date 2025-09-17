package net.spell_engine.client.render;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class CustomModelRegistry {
    public static final ArrayList<Identifier> modelIds = new ArrayList<>();

    public static List<Identifier> getModelIds() {
        return modelIds;
    }
}