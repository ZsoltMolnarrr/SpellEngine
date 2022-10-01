package net.combatspells.api.spell;

public class Spell {
    // Structure
    public float cast_duration = 0;
    public float range = 50;

//    public Cast cast;
//    public static class Cast { public Cast() { }
//        public float duration = 0;
////        public Mode mode;
////        public enum Mode {
////            CAST, CHANNEL
////        }
//        public Sound sound;
////        public ParticleBatch[] particles;
//    }

    public Release on_release;
    public static class Release { public Release() { }
        public Action action;
        public static class Action { public Action() { }
            public Type type;
            public enum Type {
                SHOOT_PROJECTILE
            }
            public ProjectileData projectile;
        }
        public ParticleBatch[] particles;
        public Sound sound;
    }

    public Impact[] on_impact;
    public static class Impact { public Impact() { }
        public Action action;
        public static class Action { public Action() { }
            public Type type;
            public enum Type {
                DAMAGE, HEAL, STATUS_EFFECT
            }
            public Damage damage;
            public static class Damage { public Damage() { }
                public float multiplier = 1;
                public String attribute;
            }
            public Heal heal;
            public static class Heal { public Heal() { }
                public float multiplier = 1;
                public String attribute;
            }
            public StatusEffect status_effect;
            public static class StatusEffect { public StatusEffect() { }
                public float duration = 0;
                public int amplifier = 0;
                public String effect_id;
            }
        }

        public ParticleBatch[] particles;
        public Sound sound;
    }

    public static class ProjectileData { public ProjectileData() { }
        public float velocity = 1F;
        public float divergence = 0;
        public boolean inherit_shooter_velocity = false;
        public Client client_data;
        public static class Client { public Client() { }
            public ParticleBatch[] travel_particles;
            public String item_id;
            public Client(ParticleBatch[] travel_particles, String item_id) {
                this.travel_particles = travel_particles;
                this.item_id = item_id;
            }
        }
    }
}
