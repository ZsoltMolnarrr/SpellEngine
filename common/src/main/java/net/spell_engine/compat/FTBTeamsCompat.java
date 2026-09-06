package net.spell_engine.compat;
import net.spell_engine.Platform;

import net.minecraft.entity.Entity;
import net.spell_engine.internals.target.EntityRelations;
import org.jetbrains.annotations.Nullable;

/// FTB Teams integration — **stubbed on 1.20.1**.
///
/// The 1.21 line resolved party/ally relations through `dev.ftb.mods.ftbteams.api` (FTBTeamsAPI, Team,
/// KnownClientPlayer). There is no FTB Teams / FTB Library compile dependency on the 1.20.1 line
/// (see `spellengine-skeleton-notes.md` §1), so no matcher is registered: the vanilla scoreboard-team matcher
/// in {@link EntityRelations} remains the only team source. The `isModLoaded` gate and the matcher name are
/// kept so a future 1.20.1 pin can restore the matcher without touching call sites.
public class FTBTeamsCompat {
    public static final String MOD_ID = "ftbteams";
    public static final String MATCHER_NAME = "ftb";

    public static void init() {
        if (Platform.util().isModLoaded(MOD_ID)) {
            // TODO 1.20.1: no FTB Teams API on the classpath. Registering the "no team" matcher below keeps the
            // matcher slot reserved and makes the intent explicit; it never claims a relation.
            EntityRelations.registerTeamMatcher(MATCHER_NAME, FTBTeamsCompat::noRelation);
        }
    }

    /// Always defers to the other matchers ("no team information").
    @Nullable
    private static EntityRelations.TeamRelation noRelation(Entity attacker, Entity target) {
        return null;
    }
}
