package net.spell_engine.neoforge.compat.rei;

import me.shedaniel.rei.forge.REIPluginCommon;
import net.spell_engine.compat.rei.SpellEngineReiCommonPlugin;

/**
 * NeoForge discovers REI plugins by scanning for these annotations, which ship only in REI's
 * NeoForge artifact, so the annotated type has to live here while the logic stays in `common`.
 */
@REIPluginCommon
public class SpellEngineReiCommonPluginNeoForge extends SpellEngineReiCommonPlugin {
}
