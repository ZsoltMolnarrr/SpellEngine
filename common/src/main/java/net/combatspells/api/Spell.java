package net.combatspells.api;

public class Spell {
    // Structure
    public float cast_duration = 0;

    public Release on_release;
    public static class Release {
        public Action action;
        public enum Action {
            SHOOT_PROJECTILE
        }

        public ProjectileData projectile;
    }

    public static class ProjectileData {
        public String texture;
    }
}
