package net.combatspells.api.spell;

import net.spelldamage.api.MagicSchool;

public class Spell {
    // Structure
    public float range = 50;
    public float cooldown_duration = 0;
    public String icon_id;
    public MagicSchool school;

    public Cast cast = new Cast();
    public static class Cast { public Cast() { }
        public float duration = 0;
//        public Mode mode;
//        public enum Mode {
//            CAST, CHANNEL
//        }
        public String animation;
        public Sound sound;
//        public ParticleBatch[] particles;
    }

    public Release on_release;
    public static class Release { public Release() { }
        public Target target;
        public static class Target { public Target() { }
            public Type type;
            public enum Type {
                PROJECTILE, CURSOR, AREA
            }
            public ProjectileData projectile;
            public Cursor cursor;
            public static class Cursor { public Cursor() { }
                public boolean use_caster_as_fallback = false;
            }
            public Area area;
            public static class Area { public Area() { }
                public float horizontal_range_multiplier = 1F;
                public float vertical_range_multiplier = 1F;
            }
        }
        public String animation;
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
            }
            public Heal heal;
            public static class Heal { public Heal() { }
                public float multiplier = 1;
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
        public float homing_angle = 1F;
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
