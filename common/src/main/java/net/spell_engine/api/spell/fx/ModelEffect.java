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

    /// Conventional easing functions from [easings.net](https://easings.net)
    public enum Easing {
        LINEAR,
        EASE_IN_SINE, EASE_OUT_SINE, EASE_IN_OUT_SINE,
        EASE_IN_QUAD, EASE_OUT_QUAD, EASE_IN_OUT_QUAD,
        EASE_IN_CUBIC, EASE_OUT_CUBIC, EASE_IN_OUT_CUBIC,
        EASE_IN_QUART, EASE_OUT_QUART, EASE_IN_OUT_QUART,
        EASE_IN_QUINT, EASE_OUT_QUINT, EASE_IN_OUT_QUINT,
        EASE_IN_EXPO, EASE_OUT_EXPO, EASE_IN_OUT_EXPO,
        EASE_IN_CIRC, EASE_OUT_CIRC, EASE_IN_OUT_CIRC,
        EASE_IN_BACK, EASE_OUT_BACK, EASE_IN_OUT_BACK,
        EASE_IN_ELASTIC, EASE_OUT_ELASTIC, EASE_IN_OUT_ELASTIC,
        EASE_IN_BOUNCE, EASE_OUT_BOUNCE, EASE_IN_OUT_BOUNCE
    }
}
