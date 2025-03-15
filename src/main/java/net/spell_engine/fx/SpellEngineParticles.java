package net.spell_engine.fx;

import com.google.common.base.Suppliers;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.client.particle.TemplateParticleType;
import net.spell_engine.client.util.Color;
import net.spell_power.api.SpellSchools;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

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
    public record SimpleEntry(Identifier id, Texture texture, SimpleParticleType particleType) {
        public SimpleEntry(String name, Texture texture) {
            this(Identifier.of(SpellEngineMod.ID, name), texture);
        }
        public SimpleEntry(Identifier id, Texture texture) {
            this(id, texture, createSimple());
        }
    }
    private static final ArrayList<SimpleEntry> simples = new ArrayList<>();
    public static List<SimpleEntry> simpleEntries() {
        return simples;
    }
    private static SimpleEntry add(SimpleEntry simpleEntry) {
        simples.add(simpleEntry);
        return simpleEntry;
    }

    public record TemplateEntry(Identifier id, Texture texture, TemplateParticleType particleType) {
        public TemplateEntry(String name, Texture texture) {
            this(Identifier.of(SpellEngineMod.ID, name), texture);
        }
        public TemplateEntry(Identifier id, Texture texture) {
            this(id, texture, new TemplateParticleType());
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
    public static final MagicParticleFamily ARCANE = addMagicFamily(new MagicParticleFamily("arcane", Color.from(SpellSchools.ARCANE.color)));
    public static final MagicParticleFamily HOLY = addMagicFamily(new MagicParticleFamily("holy", Color.HOLY));
    public static final MagicParticleFamily NATURE = addMagicFamily(new MagicParticleFamily("nature", Color.NATURE));
    public static final MagicParticleFamily FROST = addMagicFamily(new MagicParticleFamily("frost", Color.FROST));
    public static final MagicParticleFamily RAGE = addMagicFamily(new MagicParticleFamily("rage", Color.RAGE));
    public static final MagicParticleFamily WHITE = addMagicFamily(new MagicParticleFamily("white", Color.WHITE));
    public static final Supplier<List<MagicParticleFamily.Variant>> MAGIC_FAMILY_VARIANTS = Suppliers.memoize(() -> {
        var variants = new ArrayList<MagicParticleFamily.Variant>();
        for(var family: MAGIC_FAMILIES) {
            variants.addAll(family.variants());
        }
        return variants;
    });

    /**
     * WARNING! This method is very slow, only to be used for data file generation!
     */
    public static MagicParticleFamily.Variant getMagicParticleVariant(MagicParticleFamily family, MagicParticleFamily.Shape shape, MagicParticleFamily.Motion motion) {
        return MAGIC_FAMILY_VARIANTS.get().stream()
                .filter(variant -> variant.familyName().equals(family.name) && variant.shape == shape && variant.motion == motion)
                .findFirst().orElse(null);
    }

    public static final SimpleEntry fire_explosion = add(new SimpleEntry("fire_explosion", Texture.of("fire_explosion", 10)));
    public static final SimpleEntry flame = add(new SimpleEntry("flame", Texture.vanilla("flame")));
    public static final SimpleEntry flame_spark = add(new SimpleEntry("flame_spark", Texture.of("flame_spark", 8) ));
    public static final SimpleEntry flame_ground = add(new SimpleEntry("flame_ground", Texture.of("flame_ground", 8)));
    public static final SimpleEntry flame_medium_a = add(new SimpleEntry("flame_medium_a", Texture.of("flame_medium_a", 8)));
    public static final SimpleEntry flame_medium_b = add(new SimpleEntry("flame_medium_b", Texture.of("flame_medium_b", 8)));
    public static final SimpleEntry frost_shard = add(new SimpleEntry("frost_shard", Texture.of("frost_shard")));
    public static final SimpleEntry snowflake = add(new SimpleEntry("snowflake", Texture.vanilla("generic", 8, true)));
    public static final SimpleEntry roots = add(new SimpleEntry("roots", Texture.of("roots", 14)));
    public static final SimpleEntry electric_arc_A = add(new SimpleEntry("electric_arc_a", Texture.of("electric_arc_a", 8)));
    public static final SimpleEntry electric_arc_B = add(new SimpleEntry("electric_arc_b", Texture.of("electric_arc_b", 8)));
    public static final SimpleEntry shield_small = add(new SimpleEntry("shield_small", Texture.of("shield_small")));

    // TODO: Template
    public static final SimpleEntry dripping_blood = add(new SimpleEntry("dripping_blood", Texture.vanilla("drip_hang")));
    // TODO: Template
    public static final SimpleEntry sign_charge = add(new SimpleEntry("sign_charge", Texture.of("sign_speed")));

    public static final TemplateEntry smoke_medium = addTemplate(new TemplateEntry("smoke_medium", Texture.of("smoke_medium", 9)));
    public static final TemplateEntry smoke_large = addTemplate(new TemplateEntry("smoke_large", Texture.vanilla("big_smoke", 12)));

    @Deprecated
    public static final SimpleEntry weakness_smoke = add(new SimpleEntry("weakness_smoke", Texture.of("smoke_medium", 9)));

    public static void register() {
        for(var entry: simples) {
            Registry.register(Registries.PARTICLE_TYPE, entry.id, entry.particleType);
        }
        for (var variant: MAGIC_FAMILY_VARIANTS.get()) {
            Registry.register(Registries.PARTICLE_TYPE, variant.id(), variant.particleType());
        }
        for (var entry: templateEntries) {
            Registry.register(Registries.PARTICLE_TYPE, entry.id, entry.particleType);
        }
    }
}