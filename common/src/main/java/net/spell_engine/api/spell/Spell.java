package net.spell_engine.api.spell;

import net.spell_power.api.MagicSchool;

public class Spell {
    // Structure
    public MagicSchool school;
    public float range = 50;

    public Learn learn = new Learn();
    public static class Learn { public Learn() {}
        public int tier = 1;
        public int level_cost_per_tier = 3;
        public int level_requirement_per_tier = 10;
    }

    public Cast cast = new Cast();
    public static class Cast { public Cast() { }
        public float duration = 0;
        public int channel_ticks = 0;
        public String animation;
        public Sound start_sound;
        public Sound sound;
        public ParticleBatch[] particles = new ParticleBatch[]{};
    }

    public Release release;
    public static class Release { public Release() { }
        public Target target;
        public static class Target { public Target() { }
            public Type type;
            public enum Type {
                AREA, BEAM, CURSOR, PROJECTILE, METEOR, SELF
            }

            public Area area;
            public static class Area { public Area() { }
                public enum DropoffCurve { NONE, SQUARED }
                public DropoffCurve distance_dropoff = DropoffCurve.NONE;
                public float horizontal_range_multiplier = 1F;
                public float vertical_range_multiplier = 1F;
                public float angle_degrees = 0F;
            }

            public Beam beam;
            public static class Beam { public Beam() { }
                public String texture_id = "textures/entity/beacon_beam.png";
                public long color_rgba = 0xFFFFFFFF;
                public float width = 0.1F;
                public float flow = 1;
                public ParticleBatch[] block_hit_particles = new ParticleBatch[]{};
            }

            public Cursor cursor;
            public static class Cursor { public Cursor() { }
                public boolean use_caster_as_fallback = false;
            }

            public ProjectileData projectile;

            public Meteor meteor;
            public static class Meteor {
                public float launch_height = 10;
                public float impact_range = 10;
                public ParticleBatch[] impact_particles = new ParticleBatch[]{};
                public Sound impact_sound;
            }
        }
        public String animation;
        public ParticleBatch[] particles;
        public Sound sound;
    }

    public boolean allow_mixed_intents = false;
    public Impact[] impact;
    public static class Impact { public Impact() { }
        public Action action;
        public static class Action { public Action() { }
            public Type type;
            public enum Type {
                DAMAGE, HEAL, STATUS_EFFECT, FIRE
            }
            public Damage damage;
            public static class Damage { public Damage() { }
                public float spell_power_coefficient = 1;
                public float knockback = 1;
            }
            public Heal heal;
            public static class Heal { public Heal() { }
                public float spell_power_coefficient = 1;
            }
            public StatusEffect status_effect;
            public static class StatusEffect { public StatusEffect() { }
                public String effect_id;
                public float duration = 10;
                public int amplifier = 0;
                public boolean apply_to_caster = false;
                public ApplyMode apply_mode = ApplyMode.SET;
                public enum ApplyMode { SET, ADD }
                public ApplyLimit apply_limit;
                public static class ApplyLimit { public ApplyLimit() { }
                    public float health_base = 0;
                    public float spell_power_multiplier = 0;
                }
                public boolean show_particles = true;
            }
            public Fire fire;
            public static class Fire { public Fire() { }
                // Entity.java - Notice `% 20` - tick offset is used to avoid instant hits
                // if (this.fireTicks % 20 == 0 && !this.isInLava()) {
                //    this.damage(DamageSource.ON_FIRE, 1.0f);
                // }
                public int duration = 2;
                public int tick_offset = 10;
            }
        }

        public ParticleBatch[] particles = new ParticleBatch[]{};
        public Sound sound;
    }

    public Cost cost = new Cost();
    public static class Cost { public Cost() { }
        public float exhaust = 0.1F;
        public String item_id;
        public String effect_id;
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
            public String model_id;
            public float scale = 1F;
            public float rotate_degrees_per_tick = 2F;
            public RenderMode render = RenderMode.FLAT;
            public enum RenderMode {
                FLAT, DEEP
            }
            public Client(ParticleBatch[] travel_particles, String model_id) {
                this.travel_particles = travel_particles;
                this.model_id = model_id;
            }
        }
    }
}
