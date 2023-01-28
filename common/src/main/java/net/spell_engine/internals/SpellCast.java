package net.spell_engine.internals;

public class SpellCast {
    public enum AttemptResult {
        SUCCESS, MISSING_ITEM, ON_COOLDOWN, NONE;
        public boolean isSuccess() {
            return this == SUCCESS;
        }
        public boolean isFail() {
            return this != SUCCESS && this != NONE;
        }
    }

    public enum Action {
        START, CHANNEL, RELEASE
    }

    public enum Animation {
        CASTING, RELEASE
    }
}
