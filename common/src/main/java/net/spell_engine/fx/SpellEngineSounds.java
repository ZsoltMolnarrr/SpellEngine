package net.spell_engine.fx;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;

import java.util.ArrayList;
import java.util.List;

public class SpellEngineSounds {
    public static final class Entry {
        private final Identifier id;
        private final SoundEvent soundEvent;
        private RegistryEntry<SoundEvent> entry;
        private int variants = 1;

        public Entry(Identifier id, SoundEvent soundEvent) {
            this.id = id;
            this.soundEvent = soundEvent;
        }

        public Entry(String name) {
            this(Identifier.of(SpellEngineMod.ID, name));
        }

        public Entry(Identifier id) {
            this(id, SoundEvent.of(id));
        }

        public Entry travelDistance(float distance) {
            return new Entry(id, SoundEvent.of(id, distance));
        }

        public Entry variants(int variants) {
            this.variants = variants;
            return this;
        }

        public Identifier id() {
            return id;
        }

        public SoundEvent soundEvent() {
            return soundEvent;
        }

        public RegistryEntry<SoundEvent> entry() {
            return entry;
        }

        public int variants() {
            return variants;
        }

        public void register() {
            if (entry == null) {
                entry = Registry.registerReference(Registries.SOUND_EVENT, id(), soundEvent());
            }
        }
    }
    public static final List<Entry> entries = new ArrayList<>();
    public static Entry add(Entry entry) {
        entries.add(entry);
        return entry;
    }

    // MARK: Generic spell sounds

    public static final Entry GENERIC_ARCANE_CASTING = add(new Entry("generic_arcane_casting"));
    public static final Entry GENERIC_ARCANE_RELEASE = add(new Entry("generic_arcane_release"));

    public static final Entry GENERIC_FIRE_CASTING = add(new Entry("generic_fire_casting"));
    public static final Entry GENERIC_FIRE_RELEASE = add(new Entry("generic_fire_release"));
    public static final Entry GENERIC_FIRE_IGNITE = add(new Entry("generic_fire_ignite"));
    public static final Entry GENERIC_FIRE_IMPACT_1 = add(new Entry("generic_fire_impact_1"));
    public static final Entry GENERIC_FIRE_IMPACT_2 = add(new Entry("generic_fire_impact_2"));
    public static final Entry GENERIC_FIRE_IMPACT_3 = add(new Entry("generic_fire_impact_3"));

    public static final Entry GENERIC_FROST_CASTING = add(new Entry("generic_frost_casting"));
    public static final Entry GENERIC_FROST_RELEASE = add(new Entry("generic_frost_release"));
    public static final Entry GENERIC_FROST_IMPACT = add(new Entry("generic_frost_impact"));

    public static final Entry GENERIC_HEALING_CASTING = add(new Entry("generic_healing_casting"));
    public static final Entry GENERIC_HEALING_RELEASE = add(new Entry("generic_healing_release"));
    public static final Entry GENERIC_HEALING_IMPACT_1 = add(new Entry("generic_healing_impact_1"));
    public static final Entry GENERIC_HEALING_IMPACT_2 = add(new Entry("generic_healing_impact_2"));
    public static final Entry GENERIC_HEALING_IMPACT_3 = add(new Entry("generic_healing_impact_3"));
    public static final Entry GENERIC_HEALING_IMPACT_4 = add(new Entry("generic_healing_impact_4"));

    public static final Entry GENERIC_POISON_IMPACT = add(new Entry("generic_poison_impact"));

    public static final Entry GENERIC_LIGHTNING_CASTING = add(new Entry("generic_lightning_casting"));
    public static final Entry GENERIC_LIGHTNING_RELEASE = add(new Entry("generic_lightning_release"));

    public static final Entry GENERIC_SOUL_CASTING = add(new Entry("generic_soul_casting"));
    public static final Entry GENERIC_SOUL_RELEASE = add(new Entry("generic_soul_release"));
    public static final Entry GENERIC_WIND_CHARGING = add(new Entry("generic_wind_charging"));

    public static final Entry STUN_GENERIC = add(new Entry("stun_generic"));
    public static final Entry GENERIC_DISPEL_1 = add(new Entry("generic_dispel_1"));
    public static final Entry SPELL_COOLDOWN_IMPACT = add(new Entry("spell_cooldown_impact"));
    public static final Entry SPEED_BOOST = add(new Entry("speed_boost"));
    public static final Entry RADIANCE_IMPACT = add(new Entry("radiance_impact"));

    public static final Entry SIGNAL_INSTANT_CAST = add(new Entry("signal_instant_cast"));
    public static final Entry SIGNAL_SPELL_CRIT = add(new Entry("signal_spell_crit"));
    public static final Entry LEECHING_IMPACT = add(new Entry("leeching_impact"));
    public static final Entry POISON_CLOUD_SPAWN = add(new Entry("poison_cloud_spawn"));
    public static final Entry POISON_CLOUD_TICK = add(new Entry("poison_cloud_tick"));

    public static final Entry DODGE = add(new Entry("dodge").variants(2));

    // MARK: Weapon skill sounds

    public static final Entry WHIRLWIND = add(new Entry("whirlwind"));
    public static final Entry WEAPON_CLEAVE = add(new Entry("weapon_cleave"));
    public static final Entry WEAPON_DAGGER_THROW = add(new Entry("weapon_dagger_throw"));
    public static final Entry WEAPON_DAGGER_TRAVEL = add(new Entry("weapon_dagger_travel"));
    public static final Entry WEAPON_DAGGER_IMPACT = add(new Entry("weapon_dagger_impact"));
    public static final Entry WEAPON_SWORD_SWING = add(new Entry("weapon_sword_swing"));
    public static final Entry WEAPON_HAMMER_SWING = add(new Entry("weapon_hammer_swing"));
    public static final Entry WEAPON_CLAYMORE_SWING = add(new Entry("weapon_claymore_swing"));
    public static final Entry WEAPON_CLAYMORE_IMPACT = add(new Entry("weapon_claymore_impact"));
    public static final Entry WEAPON_GROUND_SLAM = add(new Entry("weapon_ground_slam"));
    public static final Entry WEAPON_THROW = add(new Entry("weapon_throw"));
    public static final Entry WEAPON_MACE_SMASH_IMPACT = add(new Entry("weapon_mace_smash_impact"));
    public static final Entry WEAPON_SPEAR_THROW = add(new Entry("weapon_spear_throw"));
    public static final Entry WEAPON_SPEAR_TRAVEL = add(new Entry("weapon_spear_travel"));
    public static final Entry WEAPON_SPEAR_STAB = add(new Entry("weapon_spear_stab"));
    public static final Entry WEAPON_SHING_A = add(new Entry("weapon_shing_a"));
    public static final Entry WEAPON_SWIPE_LAUNCH = add(new Entry("weapon_swipe_launch"));
    public static final Entry WEAPON_SICKLE_IMPACT_SMALL = add(new Entry("weapon_sickle_impact_small"));
    public static final Entry WEAPON_SICKLE_IMPACT_LARGE = add(new Entry("weapon_sickle_impact_large"));
    public static final Entry WEAPON_THRUST_LAUNCH = add(new Entry("weapon_thrust_launch"));

    // MARK: Spell binding sounds

    public static final Entry BIND_SPELL = add(new Entry("bind_spell"));
    public static final Entry UNBIND_SPELL = add(new Entry("unbind_spell"));

    // MARK: Item sounds

    public static final Entry SPELLBOOK_EQUIP = add(new Entry("spellbook_equip"));

    public static void register() {
        for (var entry: entries) {
            entry.entry = Registry.registerReference(Registries.SOUND_EVENT, entry.id(), entry.soundEvent());
        }
    }
}
