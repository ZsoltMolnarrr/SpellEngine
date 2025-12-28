package net.spell_engine.fx;

import net.minecraft.particle.ParticleType;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.client.particle.TemplateParticleType;
import net.spell_engine.client.util.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SpellEngineParticles {
    private static class Helper extends SimpleParticleType {
        protected Helper(boolean alwaysShow) {
            super(alwaysShow);
        }
    }
    private static SimpleParticleType createSimple() {
        return new Helper(false);
    }

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
    public record Entry(Identifier id, Texture texture, SimpleParticleType particleType) {
        public Entry(String name, Texture texture) {
            this(Identifier.of(SpellEngineMod.ID, name), texture);
        }
        public Entry(Identifier id, Texture texture) {
            this(id, texture, createSimple());
        }
    }
    private static final ArrayList<Entry> simples = new ArrayList<>();
    public static List<Entry> simpleEntries() {
        return simples;
    }
    private static Entry add(Entry simpleEntry) {
        simples.add(simpleEntry);
        return simpleEntry;
    }

    public enum Fading { NONE, IN, OUT, IN_OUT }
    public enum Orientation { HORIZONTAL, VERTICAL }
    public record TemplateEntry(Identifier id, Texture texture, TemplateParticleType particleType, Fading fading, Orientation orientation) {
        public TemplateEntry(String name, Texture texture) {
            this(Identifier.of(SpellEngineMod.ID, name), texture);
        }
        public TemplateEntry(Identifier id, Texture texture) {
            this(id, texture, new TemplateParticleType(), Fading.NONE, Orientation.HORIZONTAL);
        }
        public TemplateEntry fading(Fading fading) {
            return new TemplateEntry(id, texture, particleType, fading, orientation);
        }
        public TemplateEntry orientation(Orientation orientation) {
            return new TemplateEntry(id, texture, particleType, fading, orientation);
        }
    }
    private static final ArrayList<TemplateEntry> templateEntries = new ArrayList<>();
    public static List<TemplateEntry> templateEntries() {
        return templateEntries;
    }
    private static TemplateEntry addTemplate(TemplateEntry entry) {
        templateEntries.add(entry);
        return entry;
    }


    public static final List<MagicParticleFamily> MAGIC_FAMILIES = new ArrayList<>();
    public static MagicParticleFamily addMagicFamily(MagicParticleFamily family) {
        MAGIC_FAMILIES.add(family);
        return family;
    }
    public record MagicParticleFamily(String name, Color color) {
        public enum Shape { SPELL, IMPACT, SPARK, STRIPE }
        public enum Motion { FLOAT, ASCEND, DECELERATE, BURST }
        public List<Variant> variants() {
            var variants = new ArrayList<Variant>();
            for(var motion: Motion.values()) {
                for(var shape: Shape.values()) {
                    variants.add(new Variant(this, shape, motion, createSimple()));
                }
            }
            return variants;
        }
        public static final String prefix = "magic";
        public record Variant(MagicParticleFamily family, Shape shape, Motion motion, ParticleType particleType) {
            public Identifier id() {
                return Identifier.of(SpellEngineMod.ID, name());
            }
            public String familyName() {
                return family.name;
            }
            public Color color() {
                return family.color;
            }
            public String name() {
                return String.format("%s_%s_%s_%s", prefix,
                        familyName().toLowerCase(Locale.ENGLISH),
                        shape.toString().toLowerCase(Locale.ENGLISH),
                        motion.toString().toLowerCase(Locale.ENGLISH));
            }
            public int frameCount() {
                return switch (shape) {
                    case SPELL -> 8;
                    default -> 1;
                };
            }
        }
    }

    public record MagicParticles(String name, Color color) {
        public enum Shape {
            SPELL(8), SPARK, STRIPE(8, true),
            ARCANE, FROST, HOLY, HEAL, SKULL;

            public boolean animated = false;
            int frameCount = 1;

            Shape() { }
            Shape(int frameCount) { this.frameCount = frameCount; }
            Shape(int frameCount, boolean animated) {
                this.frameCount = frameCount;
                this.animated = animated;
            }
        }
        public enum Motion { FLOAT, ASCEND, DECELERATE, BURST }

        public static final String prefix = "magic";
        public record Variant(Shape shape, Motion motion, TemplateEntry entry) {
            public static Variant of(Shape shape, Motion motion) {
                var id = id(name(shape, motion));
                var texture = texture(shape);
                return new Variant(shape, motion, new TemplateEntry(id, texture));
            }
            public static Identifier id(String name) {
                return Identifier.of(SpellEngineMod.ID, name);
            }
            public static String name(Shape shape, Motion motion) {
                return String.format("%s_%s_%s", prefix,
                        shape.toString().toLowerCase(Locale.ENGLISH),
                        motion.toString().toLowerCase(Locale.ENGLISH));
            }
            public static Texture texture(Shape shape) {
                var folder = "magic/";
                switch (shape) {
                    case SPELL -> { return Texture.vanilla("spell", shape.frameCount, true); }
                    case SPARK -> { return Texture.vanilla("generic_0", shape.frameCount); }
                    case STRIPE -> { return Texture.of(folder + "vertical_stripe", shape.frameCount); }
                }
                var name = folder + shape.toString().toLowerCase(Locale.ENGLISH);
                return Texture.of(name, shape.frameCount);
            }
        }

        public static final List<Variant> all = variants();
        public static List<Variant> variants() {
            var variants = new ArrayList<Variant>();
            for(var motion: Motion.values()) {
                for(var shape: Shape.values()) {
                    variants.add(Variant.of(shape, motion));
                }
            }
            return variants;
        }
        public static TemplateEntry get(Shape shape, Motion motion) {
            return all.stream()
                    .filter(variant -> variant.shape == shape && variant.motion == motion)
                    .map(variant -> variant.entry)
                    .findFirst().orElse(null);
        }
    }

    private static final ArrayList<TemplateEntry> area_effects = new ArrayList<>();
    public static List<TemplateEntry> areaEffects() {
        return area_effects;
    }
    public static TemplateEntry addAreaEffect(TemplateEntry entry) {
        addTemplate(entry);
        area_effects.add(entry);
        return entry;
    }

    private static final ArrayList<TemplateEntry> sign_effects = new ArrayList<>();
    public static List<TemplateEntry> signEffects() {
        return sign_effects;
    }
    public static TemplateEntry addSignEffect(TemplateEntry entry) {
        addTemplate(entry);
        sign_effects.add(entry);
        return entry;
    }

    public static final TemplateEntry fire_explosion = addTemplate(new TemplateEntry("fire_explosion", Texture.of("fire_explosion", 10)));
    public static final Entry flame = add(new Entry("flame", Texture.vanilla("flame")));
    public static final Entry flame_spark = add(new Entry("flame_spark", Texture.of("flame_spark", 8) ));
    public static final Entry flame_ground = add(new Entry("flame_ground", Texture.of("flame_ground", 8)));
    public static final Entry flame_medium_a = add(new Entry("flame_medium_a", Texture.of("flame_medium_a", 8)));
    public static final Entry flame_medium_b = add(new Entry("flame_medium_b", Texture.of("flame_medium_b", 8)));
    public static final Entry frost_shard = add(new Entry("frost_shard", Texture.of("frost_shard")));
    public static final Entry snowflake = add(new Entry("snowflake", Texture.vanilla("generic", 8, true)));
    public static final Entry roots = add(new Entry("roots", Texture.of("roots", 14)));
    public static final Entry electric_arc_A = add(new Entry("electric_arc_a", Texture.of("electric_arc_a", 8)));
    public static final Entry electric_arc_B = add(new Entry("electric_arc_b", Texture.of("electric_arc_b", 8)));
    public static final Entry shield_small = add(new Entry("shield_small", Texture.of("shield_small")));
    public static final Entry dripping_blood = add(new Entry("dripping_blood", Texture.vanilla("drip_hang")));

    public static final TemplateEntry smoke_medium = addTemplate(new TemplateEntry("smoke_medium", Texture.of("smoke_medium", 9)));
    public static final TemplateEntry smoke_large = addTemplate(new TemplateEntry("smoke_large", Texture.vanilla("big_smoke", 12)));

    public static final TemplateEntry sign_aggro = addSignEffect(new TemplateEntry("sign_aggro", Texture.of("sign/aggro")));
    public static final TemplateEntry sign_arrow = addSignEffect(new TemplateEntry("sign_arrow", Texture.of("sign/arrow")));
    public static final TemplateEntry sign_cast = addSignEffect(new TemplateEntry("sign_cast", Texture.of("sign/cast")));
    public static final TemplateEntry sign_fist = addSignEffect(new TemplateEntry("sign_fist", Texture.of("sign/fist")));
    public static final TemplateEntry sign_hourglass = addSignEffect(new TemplateEntry("sign_hourglass", Texture.of("sign/hourglass")));
    public static final TemplateEntry sign_roll = addSignEffect(new TemplateEntry("sign_roll", Texture.of("sign/roll")));
    public static final TemplateEntry sign_shield = addSignEffect(new TemplateEntry("sign_shield", Texture.of("sign/shield")));
    public static final TemplateEntry sign_speed = addSignEffect(new TemplateEntry("sign_speed", Texture.of("sign/speed")));
    public static final TemplateEntry sign_wand = addSignEffect(new TemplateEntry("sign_wand", Texture.of("sign/wand")));

    // Hand made
    public static final TemplateEntry ground_glow = addAreaEffect(new TemplateEntry("ground_glow", Texture.of("area/ground_glow")).fading(Fading.IN_OUT));
    public static final TemplateEntry area_circle_1 = addAreaEffect(new TemplateEntry("area_circle_1", Texture.of("area/circle_1")).fading(Fading.OUT));

    // Area Effects

    // area effect #48 (for swirling)
    public static final TemplateEntry area_swirl = addAreaEffect(new TemplateEntry("area_swirl", Texture.of("area/swirl", 16)));
    public static final TemplateEntry area_effect_293 = addAreaEffect(new TemplateEntry("area_effect_293", Texture.of("area/effect_293", 9)));
    public static final TemplateEntry area_effect_480 = addAreaEffect(new TemplateEntry("area_effect_480", Texture.of("area/effect_480", 12)));
    public static final TemplateEntry area_effect_609 = addAreaEffect(new TemplateEntry("area_effect_609", Texture.of("area/effect_609", 13)));
    public static final TemplateEntry area_effect_658 = addAreaEffect(new TemplateEntry("area_effect_658", Texture.of("area/effect_658", 16)));
    public static final TemplateEntry area_effect_714 = addAreaEffect(new TemplateEntry("area_effect_714", Texture.of("area/effect_714", 22)));
    public static final TemplateEntry area_effect_715 = addAreaEffect(new TemplateEntry("area_effect_715", Texture.of("area/effect_715", 22)));
    public static final TemplateEntry area_effect_741 = addAreaEffect(new TemplateEntry("area_effect_741", Texture.of("area/effect_741", 23)));

    // Aura Effects

    public static final TemplateEntry aura_effect_409 = addAreaEffect(new TemplateEntry("aura_effect_409", Texture.of("aura/effect_409", 10)).orientation(Orientation.VERTICAL));
    public static final TemplateEntry aura_effect_415 = addAreaEffect(new TemplateEntry("aura_effect_415", Texture.of("aura/effect_415", 9)).orientation(Orientation.VERTICAL));
    public static final TemplateEntry aura_effect_538 = addAreaEffect(new TemplateEntry("aura_effect_538", Texture.of("aura/effect_538", 13)).orientation(Orientation.VERTICAL));
    public static final TemplateEntry aura_effect_553 = addAreaEffect(new TemplateEntry("aura_effect_553", Texture.of("aura/effect_553", 13)).orientation(Orientation.VERTICAL));
    public static final TemplateEntry aura_effect_619 = addAreaEffect(new TemplateEntry("aura_effect_619", Texture.of("aura/effect_619", 15)).orientation(Orientation.VERTICAL));
    public static final TemplateEntry aura_effect_622 = addAreaEffect(new TemplateEntry("aura_effect_622", Texture.of("aura/effect_622", 14)).orientation(Orientation.VERTICAL));
    public static final TemplateEntry aura_effect_642 = addAreaEffect(new TemplateEntry("aura_effect_642", Texture.of("aura/effect_642", 14)).orientation(Orientation.VERTICAL));
    public static final TemplateEntry aura_effect_649 = addAreaEffect(new TemplateEntry("aura_effect_649", Texture.of("aura/effect_649", 13)).orientation(Orientation.VERTICAL));
    public static final TemplateEntry aura_effect_668 = addAreaEffect(new TemplateEntry("aura_effect_668", Texture.of("aura/effect_668", 16)).orientation(Orientation.VERTICAL));
    public static final TemplateEntry aura_effect_691 = addAreaEffect(new TemplateEntry("aura_effect_691", Texture.of("aura/effect_691", 16)).orientation(Orientation.VERTICAL));
    public static final TemplateEntry aura_effect_716 = addAreaEffect(new TemplateEntry("aura_effect_716", Texture.of("aura/effect_716", 23)).orientation(Orientation.VERTICAL));
    public static final TemplateEntry aura_effect_728 = addAreaEffect(new TemplateEntry("aura_effect_728", Texture.of("aura/effect_728", 23)).orientation(Orientation.VERTICAL));

    static {
        for (var variant: MagicParticles.all) {
            addTemplate(variant.entry());
        }
    }

    @Deprecated
    public static final Entry weakness_smoke = add(new Entry("weakness_smoke", Texture.of("smoke_medium", 9)));

    public static void register() {
        for(var entry: simples) {
            Registry.register(Registries.PARTICLE_TYPE, entry.id, entry.particleType);
        }
        for (var entry: templateEntries) {
            Registry.register(Registries.PARTICLE_TYPE, entry.id, entry.particleType);
        }
    }
}