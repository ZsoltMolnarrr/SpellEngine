package net.combatspells.api;

public class Spell {
    // Structure
    public float cast_duration = 0;
    public float range = 50;

    public Release on_release;
    public static class Release {
        public Action action;
        public enum Action {
            SHOOT_PROJECTILE
        }

        public ProjectileData projectile;
    }

    public static class ProjectileData {
        public float velocity = 1F;
        public float divergence = 0;
        public boolean inherit_shooter_velocity = false;
        public Client client_data;
        public static class Client {
            public ParticleBatch[] travel_particles;

            public ParticleBatch[] impact_particles;
            public String item_id;

            public Client() { }

            public Client(ParticleBatch[] travel_particles, ParticleBatch[] impact_particles, String item_id) {
                this.travel_particles = travel_particles;
                this.impact_particles = impact_particles;
                this.item_id = item_id;
            }
        }
    }

    public static class ParticleBatch {
        public String particle_id;
        public Shape shape;
        public enum Shape {
            CIRCLE
        }
        public int count = 1;
        public float min_speed = 0;
        public float max_speed = 1;

        public ParticleBatch() { }
        public ParticleBatch(String particle_id, Shape shape, int count, float min_speed, float max_speed) {
            this.particle_id = particle_id;
            this.shape = shape;
            this.count = count;
            this.min_speed = min_speed;
            this.max_speed = max_speed;
        }
    }
}
