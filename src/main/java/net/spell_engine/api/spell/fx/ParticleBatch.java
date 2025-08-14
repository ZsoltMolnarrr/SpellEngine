package net.spell_engine.api.spell.fx;

import net.spell_engine.api.util.AlwaysGenerate;
import org.jetbrains.annotations.Nullable;

public class ParticleBatch { public ParticleBatch() { }
    public String particle_id;

    @AlwaysGenerate
    public Origin origin = Origin.CENTER;
    public enum Origin {
        FEET, CENTER, LAUNCH_POINT, GROUND
    }
    @AlwaysGenerate
    public Shape shape;
    public enum Shape {
        CIRCLE,
        PILLAR,
        PIPE,
        WIDE_PIPE, /// Same as PIPE but with double the radius
        SPHERE,
        CONE,
        LINE,
        LINE_VERTICAL,
    }

    // null = no rotation
    public Rotation rotation = null;
    // SHOULD BE CALLED `Orientation`
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
    // Rotation offset (degrees)
    public float roll = 0; // TODO: Remove
    public float roll_offset = 0;

    @AlwaysGenerate
    public float count = 1;
    public float min_speed = 0;
    public float max_speed = 1;
    public float angle = 0;

    public static final float EXTENT_TRESHOLD = 1000;
    // Static position offset, if absolute value greater than 1000, entity width is ignored
    public float extent = 0;
    // Motion based position offset
    public float pre_spawn_travel = 0;
    public boolean invert = false;

    // Available for template particles
    public long color_rgba = -1;
    // Available for template particles
    public float scale = 1F;
    // Available for template particles
    public boolean follow_entity = false;
    // Available for template particles
    public float max_age = 1F;

    public ParticleBatch(
            String particle_id, Shape shape, Origin origin,
            Rotation rotation, float roll, float roll_offset,
            float count, float min_speed, float max_speed, float angle, float extent, float pre_spawn_travel, boolean invert,
            long color_rgba, float scale, boolean follow_entity, float max_age) {
        this.particle_id = particle_id;
        this.shape = shape;
        this.origin = origin;
        this.rotation = rotation;
        this.roll = roll;
        this.roll_offset = roll_offset;
        this.count = count;
        this.min_speed = min_speed;
        this.max_speed = max_speed;
        this.angle = angle;
        this.extent = extent;
        this.pre_spawn_travel = pre_spawn_travel;
        this.invert = invert;

        this.color_rgba = color_rgba;
        this.scale = scale;
        this.follow_entity = follow_entity;
        this.max_age = max_age;
    }

    // Compatibility constructors

    public ParticleBatch(String particle_id, Shape shape, Origin origin, float count, float min_speed, float max_speed) {
        this(particle_id, shape, origin, null, count, min_speed, max_speed, 0);
    }

    public ParticleBatch(String particle_id, Shape shape, Origin origin, Rotation rotation,
                         float count, float min_speed, float max_speed, float angle) {
        this(particle_id, shape, origin, rotation, count, min_speed, max_speed, angle, 0);
    }

    public ParticleBatch(
            String particle_id, Shape shape, Origin origin, Rotation rotation,
            float count, float min_speed, float max_speed, float angle, float extent) {
        this(particle_id, shape, origin, rotation, 0, 0, count,
                min_speed, max_speed, angle, extent, 0, false,
                -1, 1, false, 1F);
    }

    // Copy

    public ParticleBatch(ParticleBatch other) {
        this(other.particle_id,
            other.shape,
            other.origin,
            other.rotation,
            other.roll,
            other.roll_offset,
            other.count,
            other.min_speed,
            other.max_speed,
            other.angle,
            other.extent,
            other.pre_spawn_travel,
            other.invert,
            other.color_rgba,
            other.scale,
            other.follow_entity,
            other.max_age
        );
    }

    public ParticleBatch copy() {
        return new ParticleBatch(this);
    }

    public ParticleBatch origin(Origin origin) {
        this.origin = origin;
        return this;
    }

    public ParticleBatch shape(Shape shape) {
        this.shape = shape;
        return this;
    }

    public ParticleBatch rotate(Rotation rotation) {
        this.rotation = rotation;
        return this;
    }

    public ParticleBatch invert() {
        this.invert = !this.invert;
        return this;
    }

    public ParticleBatch preSpawnTravel(float pre_spawn_travel) {
        this.pre_spawn_travel = pre_spawn_travel;
        return this;
    }

    public ParticleBatch roll(float roll) {
        this.roll = roll;
        return this;
    }

    public ParticleBatch rollOffset(float roll_offset) {
        this.roll_offset = roll_offset;
        return this;
    }

    public ParticleBatch extent(float extent) {
        this.extent = extent;
        return this;
    }

    public ParticleBatch color(long color) {
        this.color_rgba = color;
        return this;
    }

    public ParticleBatch scale(float scale) {
        this.scale = scale;
        return this;
    }

    public ParticleBatch followEntity(boolean follow_entity) {
        this.follow_entity = follow_entity;
        return this;
    }

    public ParticleBatch maxAge(float max_age) {
        this.max_age = max_age;
        return this;
    }
}
