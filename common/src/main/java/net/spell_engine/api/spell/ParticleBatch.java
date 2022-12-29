package net.spell_engine.api.spell;

public class ParticleBatch { public ParticleBatch() { }
    public String particle_id;


    public Origin origin = Origin.CENTER;
    public enum Origin {
        FEET, CENTER, LAUNCH_POINT
    }

    // null = no rotation
    public Rotation rotation = null;
    public enum Rotation {
        LOOK
    }

    public Shape shape;
    public enum Shape {
        CIRCLE, PILLAR, PIPE, SPHERE, CONE
    }

    public float count = 1;
    public float min_speed = 0;
    public float max_speed = 1;
    public float angle = 0;

    public ParticleBatch(
            String particle_id, Shape shape, Origin origin, Rotation rotation,
            float count, float min_speed, float max_speed, float angle) {
        this.particle_id = particle_id;
        this.shape = shape;
        this.origin = origin;
        this.rotation = rotation;
        this.count = count;
        this.min_speed = min_speed;
        this.max_speed = max_speed;
        this.angle = angle;
    }
}
