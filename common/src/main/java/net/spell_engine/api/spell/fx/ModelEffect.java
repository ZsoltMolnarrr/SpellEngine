package net.spell_engine.api.spell.fx;

import net.spell_engine.api.render.LightEmission;

import java.util.List;

public class ModelEffect { public ModelEffect() { }
    public String model_id;
    public LightEmission light_emission = LightEmission.GLOW;
    public float scale = 1F;
    /// Duration in ticks the model persists
    public int duration = 20;
    public Positioning positioning = new Positioning();
    /// Vertical placement within the contextually relevant entity's bounding box.
    /// 0 = feet/ground, 0.5 = center, 1 = top.
    public static class Positioning { public Positioning() { }
        public float vertical = 0.5F;
    }
    /// When true, the spawned model effect entity follows the contextually relevant entity
    /// (e.g. the impact target on area_impact, the caster on release).
    public boolean follow_entity = false;

    /// Transforms applied at full effect from tick 0 (no time dimension)
    public List<Transform> initial = List.of();
    /// Animated transforms, applied additively over time
    public List<Animation> animations = List.of();

    /// Shallow copy: scalar fields are duplicated, nested transform lists are shared (treated as
    /// immutable). Use this before mutating a field (e.g. `scale`) so the shared registered spell
    /// instance is never modified.
    public ModelEffect copy() {
        var copy = new ModelEffect();
        copy.model_id = this.model_id;
        copy.light_emission = this.light_emission;
        copy.scale = this.scale;
        copy.duration = this.duration;
        copy.positioning = this.positioning;
        copy.follow_entity = this.follow_entity;
        copy.initial = this.initial;
        copy.animations = this.animations;
        return copy;
    }

    /// Base: operation + value fields
    public static class Transform { public Transform() { }
        /// Operation type identifier, looked up in the operation registry
        public String operation = "scale";
        /// Value fields — interpretation depends on operation:
        /// scale: `x`, `y`, `z`
        /// translate: `x`, `y`, `z`
        /// rotate: degrees per axis `x`, `y`, `z`
        public float x = 0;
        public float y = 0;
        public float z = 0;
    }

    /// Transform with timing parameters
    public static class Animation extends Transform { public Animation() { }
        public int start = 0;
        public int end = 20;
        public Easing easing = Easing.LINEAR;
    }
}
