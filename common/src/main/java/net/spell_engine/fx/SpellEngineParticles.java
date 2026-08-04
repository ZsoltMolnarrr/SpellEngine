package net.spell_engine.fx;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.fx.Easing;
import net.spell_engine.api.spell.fx.ParticleGroupEffect;
import net.spell_engine.api.spell.fx.ParticleGroupEffect.Facing;
import net.spell_engine.api.spell.fx.ParticleGroupEffect.Motion;
import net.spell_engine.api.spell.fx.ParticleGroupEffect.Render;
import net.spell_engine.client.util.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/// Registry of every Spell Engine particle.
///
/// Each [Entry] is one texture plus the natural defaults of the particle rendered
/// from it — its lifetime, render sheet, base scale, colouring and motion. A
/// [ParticleGroupEffect] references an entry by id and overrides only what the
/// call site needs; the single generic factory ([net.spell_engine.client.particle.SpellParticle])
/// resolves entry defaults + effect payload into the final appearance.
///
/// This replaces the V1 split of `Entry` (simple) vs `TemplateEntry` (customizable),
/// the category lists (`areaEffects`, `signEffects`) that drove per-category factory
/// wiring, the `aura_*` twin registrations (same texture, second orientation — now
/// `Facing.CAMERA` at the call site), and the 32 `magic_<shape>_<motion>` variants
/// (now 8 `magic_<shape>` entries with motion in the effect payload).
public class SpellEngineParticles {

    public record Texture(Identifier id, int frames, boolean reverseOrder) {
        public Texture(Identifier id, int frames) {
            this(id, frames, false);
        }
        public Texture(Identifier id) {
            this(id, 1, false);
        }
        public static Texture vanilla(String name) {
            return new Texture(Identifier.ofVanilla(name));
        }
        public static Texture vanilla(String name, int frames) {
            return new Texture(Identifier.ofVanilla(name), frames);
        }
        public static Texture vanilla(String name, int frames, boolean reverseOrder) {
            return new Texture(Identifier.ofVanilla(name), frames, reverseOrder);
        }
        public static Texture of(String name) {
            return new Texture(Identifier.of(SpellEngineMod.ID, name));
        }
        public static Texture of(String name, int frames) {
            return new Texture(Identifier.of(SpellEngineMod.ID, name), frames);
        }
        public static Texture of(String name, int frames, boolean reverseOrder) {
            return new Texture(Identifier.of(SpellEngineMod.ID, name), frames, reverseOrder);
        }
    }

    /// One registered particle: a texture and the defaults of the particle rendered
    /// from it. Effect payloads override these per spawn — see
    /// [net.spell_engine.client.particle.SpellParticle] for the resolution rules.
    public static class Entry {
        private final Identifier id;
        private final Texture texture;
        /// Lifetime in ticks for single-frame textures; animated textures always
        /// live for their frame count (see [#lifetime()]). Stretch or shorten an
        /// animated entry's life via `defaults(p -> p.playbackSpeed(...))` instead.
        private int lifetime = 20;
        /// Vertical render offset of the sprite quad, in units of particle size.
        /// `1` makes a quad stand on its position instead of being centered on it.
        private float pivot = 0F;
        private final ParticleGroupEffect.Particle defaults = new ParticleGroupEffect.Particle();
        private final ParticleGroupEffectType type = new ParticleGroupEffectType();

        Entry(String name, Texture texture) {
            this(Identifier.of(SpellEngineMod.ID, name), texture);
        }
        Entry(Identifier id, Texture texture) {
            this.id = id;
            this.texture = texture;
            this.type.bind(this);
        }

        public Identifier id() { return id; }
        public Texture texture() { return texture; }
        public ParticleGroupEffectType type() { return type; }
        /// The entry's natural lifetime in ticks: the texture's frame count when
        /// animated, otherwise the statically configured value (default 20).
        public int lifetime() { return texture.frames() > 1 ? texture.frames() : lifetime; }
        public float pivot() { return pivot; }
        public ParticleGroupEffect.Particle defaults() { return defaults; }

        Entry lifetime(int lifetime) { this.lifetime = lifetime; return this; }
        Entry pivot(float pivot) { this.pivot = pivot; return this; }
        Entry defaults(Consumer<ParticleGroupEffect.Particle> configure) { configure.accept(this.defaults); return this; }
    }

    private static final ArrayList<Entry> entries = new ArrayList<>();
    public static List<Entry> entries() {
        return entries;
    }
    private static Entry add(Entry entry) {
        entries.add(entry);
        return entry;
    }

    /// `SpellSchools.FROST.color` — noticeably paler than SpellEngine's own
    /// [Color#FROST] (`0x66ccff`). V1's snowflake factory used this one.
    private static final Color FROST_PALE = Color.from(0xccffff);

    // MARK: - Elemental

    public static final Entry fire_explosion = add(new Entry("fire_explosion", Texture.of("elemental/fire_explosion", 10))
            .defaults(p -> p.render(Render.LIT).scale(1.2F)));
    public static final Entry flame = add(new Entry("flame", Texture.vanilla("flame")).lifetime(16)
            .defaults(p -> p.render(Render.LIT).scale(0.15F, 0.33F).drag(0.96F).collides(true)));
    public static final Entry flame_spark = add(new Entry("flame_spark", Texture.of("elemental/flame_spark", 8))
            .defaults(p -> p.render(Render.LIT).scale(0.15F, 0.33F).drag(0.96F).collides(true).playbackSpeed(0.5F)));
    public static final Entry flame_ground = add(new Entry("flame_ground", Texture.of("elemental/flame_ground", 8))
            .defaults(p -> p.render(Render.LIT).scale(0.15F, 0.33F).drag(0.96F).collides(true).playbackSpeed(0.5F)));
    public static final Entry flame_medium_a = add(new Entry("flame_medium_a", Texture.of("elemental/flame_medium_a", 8))
            .defaults(p -> p.render(Render.LIT).scale(0.5F, 0.33F).drag(0.96F).collides(true)));
    public static final Entry flame_medium_b = add(new Entry("flame_medium_b", Texture.of("elemental/flame_medium_b", 8))
            .defaults(p -> p.render(Render.LIT).scale(0.5F, 0.33F).drag(0.96F).collides(true)));
    public static final Entry frost_shard = add(new Entry("frost_shard", Texture.of("elemental/frost_shard")).lifetime(7)
            .defaults(p -> p.render(Render.LIT).scale(0.15F, 0.33F).drag(0.96F).collides(true)
                    .color(Color.FROST.toRGBA()).colorVariance(0.65F)));
    public static final Entry snowflake = add(new Entry("snowflake", Texture.vanilla("generic", 8, true))
            .defaults(p -> p.motion(Motion.DRIFT).scale(0.15F, 0.33F).color(FROST_PALE.toRGBA())
                    .opacity(0.75F).collides(true).playbackSpeed(0.2F).lifetimeVariance(0.5F)
                    // +10% over DRIFT's vanilla 0.225 — falls a touch harder than snow
                    .gravity(0.2475F)));
    public static final Entry lightning_arc_A = add(new Entry("lightning_arc_a", Texture.of("elemental/lightning_arc_a", 8))
            .defaults(p -> p.render(Render.LIT).scale(1F)));
    public static final Entry lightning_arc_B = add(new Entry("lightning_arc_b", Texture.of("elemental/lightning_arc_b", 8))
            .defaults(p -> p.render(Render.LIT).scale(1F)));
    public static final Entry electric_arc_A = add(new Entry("electric_arc_a", Texture.of("electric_arc_a", 8))
            .defaults(p -> p.render(Render.LIT).scale(0.75F).drag(0.96F).collides(true).color(Color.ELECTRIC.toRGBA())));
    public static final Entry electric_arc_B = add(new Entry("electric_arc_b", Texture.of("electric_arc_b", 8))
            .defaults(p -> p.render(Render.LIT).scale(0.75F).drag(0.96F).collides(true).color(Color.ELECTRIC.toRGBA())));

    // MARK: - Physical / misc

    public static final Entry smoke_medium = add(new Entry("smoke_medium", Texture.of("smoke_medium", 9))
            .defaults(p -> p.render(Render.LIT).glow(false).scale(0.15F, 0.33F).colorVariance(0.65F).opacity(0.8F)
                    .drag(0.8F).gravity(-0.01F).collides(true).playbackSpeed(0.5F)));
    public static final Entry smoke_large = add(new Entry("smoke_large", Texture.vanilla("big_smoke", 12))
            .defaults(p -> p.glow(false).scale(3F).opacity(0.9F).gravity(0F).drag(1F).collides(true).playbackSpeed(0.12F)));
    public static final Entry roots = add(new Entry("roots", Texture.of("roots", 14)).pivot(1F)
            .defaults(p -> p.render(Render.OPAQUE).glow(false).scale(0.25F).gravity(0.225F).drag(0.95F).collides(true).playbackSpeed(0.6F)));
    public static final Entry shield_small = add(new Entry("shield_small", Texture.of("shield_small")).lifetime(16)
            .defaults(p -> p.motion(Motion.DECELERATE).scale(0.15F, 0.33F).colorVariance(0.3F)));
    public static final Entry dripping_blood = add(new Entry("dripping_blood", Texture.vanilla("drip_hang"))
            .defaults(p -> p.glow(false).scale(0.11F, 0.33F).color(Color.from(0x590000).toRGBA()).gravity(0.8F).collides(true)));

    @Deprecated
    public static final Entry weakness_smoke = add(new Entry("weakness_smoke", Texture.of("smoke_medium", 9))
            .defaults(p -> p.render(Render.LIT).glow(false).scale(0.15F, 0.33F).color(Color.from(0x993333).toRGBA()).colorVariance(0.65F)
                    .opacity(0.7F).drag(0.8F).gravity(0.01F).collides(true).playbackSpeed(0.5F)));

    // MARK: - Signs

    private static Entry sign(String name) {
        return add(new Entry("sign_" + name, Texture.of("sign/" + name)).lifetime(40)
                .defaults(p -> p.scale(0.4F).opacity(0.9F).drag(0.7F).collides(true)));
    }
    public static final Entry sign_aggro = sign("aggro");
    public static final Entry sign_arrow = sign("arrow");
    public static final Entry sign_cast = sign("cast");
    public static final Entry sign_crit = sign("crit");
    public static final Entry sign_fist = sign("fist");
    public static final Entry sign_hourglass = sign("hourglass");
    public static final Entry sign_roll = sign("roll");
    public static final Entry sign_shield = sign("shield");
    public static final Entry sign_speed = sign("speed");
    public static final Entry sign_wand = sign("wand");

    // MARK: - Area effects
    // Default facing is GROUND (V1 `area_*` behaviour); spawn with
    // `facing(Facing.CAMERA)` for the V1 `aura_*` look — the aura twins are gone.

    public static final Entry ground_glow = add(new Entry("ground_glow", Texture.of("zone/ground_glow")).lifetime(16)
            .defaults(p -> p.facing(Facing.GROUND).scale(1F)
                    .opacityCurve(Easing.Curve.fadeInOut(Easing.LINEAR, Easing.LINEAR, 0.4F))));
    public static final Entry area_circle_1 = add(new Entry("area_circle_1", Texture.of("zone/circle_1")).lifetime(16)
            .defaults(p -> p.facing(Facing.GROUND).scale(1F)
                    .opacityCurve(Easing.Curve.fadeOut(Easing.LINEAR, 0.7F))));
    public static final Entry area_swirl = add(new Entry("area_swirl", Texture.of("zone/swirl", 16))
            .defaults(p -> p.facing(Facing.GROUND).scale(1F)));

    private static Entry areaEffect(int index, int frames) {
        return add(new Entry("area_effect_" + index, Texture.of("zone/effect_" + index, frames))
                .defaults(p -> p.facing(Facing.GROUND).scale(1F)));
    }
    public static final Entry area_effect_293 = areaEffect(293, 9);
    public static final Entry area_effect_307 = areaEffect(307, 8);
    public static final Entry area_effect_409 = areaEffect(409, 10);
    public static final Entry area_effect_415 = areaEffect(415, 9);
    public static final Entry area_effect_473 = areaEffect(473, 11);
    public static final Entry area_effect_474 = areaEffect(474, 11);
    public static final Entry area_effect_480 = areaEffect(480, 12);
    public static final Entry area_effect_538 = areaEffect(538, 13);
    public static final Entry area_effect_553 = areaEffect(553, 13);
    public static final Entry area_effect_574 = areaEffect(574, 13);
    public static final Entry area_effect_609 = areaEffect(609, 13);
    public static final Entry area_effect_619 = areaEffect(619, 15);
    public static final Entry area_effect_622 = areaEffect(622, 14);
    public static final Entry area_effect_637 = areaEffect(637, 15);
    public static final Entry area_effect_642 = areaEffect(642, 14);
    public static final Entry area_effect_649 = areaEffect(649, 13);
    public static final Entry area_effect_658 = areaEffect(658, 16);
    public static final Entry area_effect_668 = areaEffect(668, 16);
    public static final Entry area_effect_676 = areaEffect(676, 17);
    public static final Entry area_effect_678 = areaEffect(678, 17);
    public static final Entry area_effect_691 = areaEffect(691, 16);
    public static final Entry area_effect_700 = areaEffect(700, 22);
    public static final Entry area_effect_714 = areaEffect(714, 22);
    public static final Entry area_effect_715 = areaEffect(715, 22);
    public static final Entry area_effect_716 = areaEffect(716, 23);
    public static final Entry area_effect_728 = areaEffect(728, 23);
    public static final Entry area_effect_741 = areaEffect(741, 23);
    public static final Entry area_effect_748 = areaEffect(748, 24);

    // MARK: - Magic
    // V1 registered shape x motion = 32 types; V2 registers the 8 shapes and takes
    // motion from the effect payload — pick one with
    // `ParticleGroupBuilder.magic(entry, motion)`.

    private static Entry magic(String name, Texture texture, float opacity, float playbackSpeed) {
        return add(new Entry("magic_" + name, texture).lifetime(16)
                .defaults(p -> p.motion(Motion.FLOAT)
                        .scale(0.11F, 0.33F).colorVariance(0.65F).opacity(opacity)
                        .playbackSpeed(playbackSpeed).lifetimeVariance(0.5F)));
    }
    public static final Entry magic_spell = magic("spell", Texture.vanilla("spell", 8, true), 1F, 0.5F);
    public static final Entry magic_spark = magic("spark", Texture.vanilla("generic_0"), 0.75F, 1F);
    public static final Entry magic_stripe = magic("stripe", Texture.of("magic/vertical_stripe", 8), 1F, 0.5F);
    public static final Entry magic_arcane = magic("arcane", Texture.of("magic/arcane"), 0.75F, 1F);
    public static final Entry magic_frost = magic("frost", Texture.of("magic/frost"), 0.75F, 1F);
    public static final Entry magic_holy = magic("holy", Texture.of("magic/holy"), 0.75F, 1F);
    public static final Entry magic_heal = magic("heal", Texture.of("magic/heal"), 0.75F, 1F);
    public static final Entry magic_skull = magic("skull", Texture.of("magic/skull"), 0.75F, 1F);

    public static void register() {
        for (var entry: entries) {
            Registry.register(Registries.PARTICLE_TYPE, entry.id(), entry.type());
        }
    }
}
