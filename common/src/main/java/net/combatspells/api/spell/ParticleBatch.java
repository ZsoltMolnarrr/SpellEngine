package net.combatspells.api.spell;

public class ParticleBatch { public ParticleBatch() { }
    public String particle_id;
    public Shape shape;
    public enum Shape {
        CIRCLE, PILLAR, PIPE, SPHERE
    }
    public Origin origin = Origin.CENTER;
    public enum Origin {
        FEET, CENTER, HANDS
    }
    public float count = 1;
    public float min_speed = 0;
    public float max_speed = 1;

    public ParticleBatch(String particle_id, Shape shape, Origin origin, float count, float min_speed, float max_speed) {
        this.particle_id = particle_id;
        this.shape = shape;
        this.origin = origin;
        this.count = count;
        this.min_speed = min_speed;
        this.max_speed = max_speed;
    }
}
