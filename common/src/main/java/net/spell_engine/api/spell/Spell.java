package net.spell_engine.api.spell;

import net.spell_damage.api.MagicSchool;

public class Spell {
    // Structure
    public float range = 50;
    public MagicSchool school;

    public Cast cast = new Cast();
    public static class Cast { public Cast() { }
        public float duration = 0;
        public int channel_ticks = 0;
        public String animation;
        public Sound start_sound;
        public Sound sound;
        public ParticleBatch[] particles;
    }

    public Release on_release;
    public static class Release { public Release() { }
        public Target target;
        public static class Target { public Target() { }
            public Type type;
            public enum Type {
                AREA, BEAM, CURSOR, PROJECTILE
            }

            public Area area;
            public static class Area { public Area() { }
                public float horizontal_range_multiplier = 1F;
                public float vertical_range_multiplier = 1F;
            }

            public Beam beam;
            public static class Beam { public Beam() { }
                public String texture_id = "textures/entity/beacon_beam.png";
                public long color_rgba = 0xFFFFFFFF;
                public float width = 0.1F;
                public float flow = 1;

                public ParticleBatch[] emit_particles = new ParticleBatch[]{};
                public ParticleBatch[] block_hit_particles = new ParticleBatch[]{};
            }

            public Cursor cursor;
            public static class Cursor { public Cursor() { }
                public boolean use_caster_as_fallback = false;
            }

            public ProjectileData projectile;
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
                public float knockback = 1;
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

    public Cost cost = new Cost();
    public static class Cost { public Cost() { }
        public float exhaust = 0.1F;
        public String item_id;
        public int durability = 1;
        public float cooldown_duration = 0;
        public boolean cooldown_proportional = false;
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
            public RenderMode render = RenderMode.FLAT;
            public enum RenderMode {
                FLAT, DEEP
            }
            public Client(ParticleBatch[] travel_particles, String item_id) {
                this.travel_particles = travel_particles;
                this.item_id = item_id;
            }
        }
    }
}
