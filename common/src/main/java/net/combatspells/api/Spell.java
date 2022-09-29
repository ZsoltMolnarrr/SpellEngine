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
            public ParticleEffect travel_particles;
            public String item_id;

            public Client() { }
            public Client(ParticleEffect travel_particles, String item_id) {
                this.travel_particles = travel_particles;
                this.item_id = item_id;
            }
        }
    }

    public static class ParticleEffect {
        public String id;
        public enum Shape {
            CIRCLE
        }
        public int count = 1;
        public float speed = 1;
        public Shape shape;

        public ParticleEffect() { }

        public ParticleEffect(String id, Shape shape, int count, float speed) {
            this.id = id;
            this.shape = shape;
            this.count = count;
            this.speed = speed;
        }
    }
}
