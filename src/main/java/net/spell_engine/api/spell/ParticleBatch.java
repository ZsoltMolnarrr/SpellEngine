package net.spell_engine.api.spell;

import org.jetbrains.annotations.Nullable;

public class ParticleBatch { public ParticleBatch() { }
    public String particle_id;


    public Origin origin = Origin.CENTER;
    public enum Origin {
        FEET, CENTER, LAUNCH_POINT
    }

    // null = no rotation
    public Rotation rotation = null;
    public enum Rotation {
        LOOK;

        @Nullable
        public static Rotation from(int ordinal) {
            if (ordinal < 0 || ordinal >= values().length) {
                return null;
            } else {
                return Rotation.values()[ordinal];
            }
        }
    }

    public Shape shape;
    public enum Shape {
        CIRCLE, PILLAR, PIPE, SPHERE, CONE
    }

    public float count = 1;
    public float min_speed = 0;
    public float max_speed = 1;
    public float angle = 0;
    // Static position offset
    public float extent = 0;
    // Motion based position offset
    public float pre_spawn_travel = 0;
    public boolean invert = false;

    public ParticleBatch(
            String particle_id, Shape shape, Origin origin, Rotation rotation,
            float count, float min_speed, float max_speed, float angle, float extent, float pre_spawn_travel, boolean invert) {
        this.particle_id = particle_id;
        this.shape = shape;
        this.origin = origin;
        this.rotation = rotation;
        this.count = count;
        this.min_speed = min_speed;
        this.max_speed = max_speed;
        this.angle = angle;
        this.extent = extent;
        this.pre_spawn_travel = pre_spawn_travel;
        this.invert = invert;
    }

    // Compatibility constructors

    @Deprecated
    public ParticleBatch(String particle_id, Shape shape, Origin origin, Rotation rotation,
                         float count, float min_speed, float max_speed, float angle) {
        this(particle_id, shape, origin, rotation, count, min_speed, max_speed, angle, 0);
    }

    @Deprecated
    public ParticleBatch(
            String particle_id, Shape shape, Origin origin, Rotation rotation,
            float count, float min_speed, float max_speed, float angle, float extent) {
        this(particle_id, shape, origin, rotation, count, min_speed, max_speed, angle, extent, 0, false);
    }

    // Copy

    public ParticleBatch(ParticleBatch other) {
        this(other.particle_id,
            other.shape,
            other.origin,
            other.rotation,
            other.count,
            other.min_speed,
            other.max_speed,
            other.angle,
            other.extent,
            other.pre_spawn_travel,
            other.invert);
    }
}
