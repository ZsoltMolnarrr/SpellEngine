package net.combatspells.internals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import net.combatspells.api.spell.ParticleBatch;
import net.combatspells.api.spell.Sound;
import net.combatspells.api.spell.Spell;
import net.combatspells.api.spell.SpellAssignment;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.spelldamage.api.MagicSchool;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpellRegistry {
    private static Map<Identifier, Spell> spells = new HashMap();
    private static Map<Identifier, SpellAssignment> assignments = new HashMap();

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTED.register((minecraftServer) -> {
            loadSpells(minecraftServer.getResourceManager());
            loadAssignments(minecraftServer.getResourceManager());
            encodeContent();
        });
    }

    public static void loadSpells(ResourceManager resourceManager) {
        var gson = new Gson();
        Map<Identifier, Spell> parsed = new HashMap();
        // Reading all attribute files
        var directory = "spells";
        for (var entry : resourceManager.findResources(directory, fileName -> fileName.getPath().endsWith(".json")).entrySet()) {
            var identifier = entry.getKey();
            var resource = entry.getValue();
            try {
                // System.out.println("Checking resource: " + identifier);
                JsonReader reader = new JsonReader(new InputStreamReader(resource.getInputStream()));
                Spell container = gson.fromJson(reader, Spell.class);
                var id = identifier
                        .toString().replace(directory + "/", "");
                id = id.substring(0, id.lastIndexOf('.'));
                parsed.put(new Identifier(id), container);
                // System.out.println("loaded spell - id: " + id +  " spell: " + gson.toJson(container));
            } catch (Exception e) {
                System.err.println("Failed to parse spell: " + identifier);
                e.printStackTrace();
            }
        }
        spells = parsed;
    }

    public static void loadAssignments(ResourceManager resourceManager) {
        var gson = new Gson();
        Map<Identifier, SpellAssignment> parsed = new HashMap();
        // Reading all attribute files
        var directory = "item_spell_assignment";
        for (var entry : resourceManager.findResources(directory, fileName -> fileName.getPath().endsWith(".json")).entrySet()) {
            var identifier = entry.getKey();
            var resource = entry.getValue();
            try {
                // System.out.println("Checking resource: " + identifier);
                JsonReader reader = new JsonReader(new InputStreamReader(resource.getInputStream()));
                SpellAssignment container = gson.fromJson(reader, SpellAssignment.class);
                var id = identifier
                        .toString().replace(directory + "/", "");
                id = id.substring(0, id.lastIndexOf('.'));
                parsed.put(new Identifier(id), container);
                // System.out.println("loaded assignment - id: " + id +  " assignment: " + container.spell);
            } catch (Exception e) {
                System.err.println("Failed to parse item_spell_assignment: " + identifier);
                e.printStackTrace();
            }
        }
        assignments = parsed;
    }

    public static Spell resolveSpell(Identifier itemId) {
        var assignment = assignments.get(itemId);
        if (assignment == null || assignment.spell == null) {
            return null;
        }
        var spellId = new Identifier(assignment.spell);
        return spells.get(spellId);
    }

    public static PacketByteBuf encoded = PacketByteBufs.create();

    public static class SyncFormat { public SyncFormat() { }
        private Map<String, Spell> spells = new HashMap();
        private Map<String, SpellAssignment> assignments = new HashMap();
    }

    private static void encodeContent() {
        var gson = new Gson();
        var buffer = PacketByteBufs.create();

        var sync = new SyncFormat();
        spells.forEach((key, value) -> {
            sync.spells.put(key.toString(), value);
        });
        assignments.forEach((key, value) -> {
            sync.assignments.put(key.toString(), value);
        });
        var json = gson.toJson(sync);

        List<String> chunks = new ArrayList<>();
        var chunkSize = 10000;
        for (int i = 0; i < json.length(); i += chunkSize) {
            chunks.add(json.substring(i, Math.min(json.length(), i + chunkSize)));
        }
        buffer.writeInt(chunks.size());
        for (var chunk: chunks) {
            buffer.writeString(chunk);
        }

        System.out.println("Encoded SpellRegistry size (with package overhead): " + buffer.readableBytes()
                + " bytes (in " + chunks.size() + " string chunks with the size of "  + chunkSize + ")");

        encoded = buffer;
    }

    public static void decodeContent(PacketByteBuf buffer) {
        var chunkCount = buffer.readInt();
        String json = "";
        for (int i = 0; i < chunkCount; ++i) {
            json = json.concat(buffer.readString());
        }
        var gson = new Gson();
        SyncFormat sync = gson.fromJson(json, SyncFormat.class);
        sync.spells.forEach((key, value) -> {
            spells.put(new Identifier(key), value);
            // System.out.println("Decoded spell: " + key + " value: " + gson.toJson(value));
        });
        sync.assignments.forEach((key, value) -> {
            assignments.put(new Identifier(key), value);
            // System.out.println("Decoded assignments: " + key + " value: " + gson.toJson(value));
        });
    }

    private static void printHardCoded() {
        var gson = new GsonBuilder().setPrettyPrinting().create();

        var fireBall = new Spell();
        fireBall.cast.duration = 1;
        fireBall.cast.animation = "combatspells:one_handed_projectile_charge";
        fireBall.cast.sound = new Sound("combatspells:casting_fire");
        fireBall.cast.particles = new ParticleBatch[] {
                new ParticleBatch("flame", ParticleBatch.Shape.PIPE, ParticleBatch.Origin.FEET, 1, 0.05F, 0.1F)
        };
        fireBall.range = 64;
        fireBall.icon_id = "combatspells:textures/spells/fireball.png";
        fireBall.school = MagicSchool.FIRE;
        fireBall.on_release = new Spell.Release();
        fireBall.on_release.sound = new Sound("combatspells:release_fire");
        fireBall.on_release.animation = "combatspells:one_handed_projectile_release";
        fireBall.on_release.target = new Spell.Release.Target();
        fireBall.on_release.target.type = Spell.Release.Target.Type.PROJECTILE;
        fireBall.on_release.target.projectile = new Spell.ProjectileData();
        fireBall.on_release.target.projectile.client_data = new Spell.ProjectileData.Client(
                new ParticleBatch[] {
                        new ParticleBatch("flame", ParticleBatch.Shape.CIRCLE, ParticleBatch.Origin.CENTER, 3, 0, 0.1F)
                },
                "fire_charge"
        );

        fireBall.on_release.target.projectile.homing_angle = 1;
        fireBall.on_release.target.projectile.velocity = 0.5F;

        var firballImpact = new Spell.Impact();
        firballImpact.sound = new Sound("combatspells:impact_fireball");
        firballImpact.action = new Spell.Impact.Action();
        firballImpact.action.type = Spell.Impact.Action.Type.DAMAGE;
        firballImpact.action.damage = new Spell.Impact.Action.Damage();
        firballImpact.particles = new ParticleBatch[] {
                new ParticleBatch("lava", ParticleBatch.Shape.CIRCLE, ParticleBatch.Origin.CENTER, 30, 0.5F, 3F)
        };
        fireBall.on_impact = new Spell.Impact[] { firballImpact };
        fireBall.cost.item_id = "minecraft:coal";
        spells.put(new Identifier("minecraft", "wooden_sword"), fireBall);

        System.out.println(gson.toJson(fireBall));

        var frostbolt = new Spell();
        frostbolt.school = MagicSchool.FROST;
        frostbolt.icon_id = "combatspells:textures/spells/frostbolt.png";
        frostbolt.cast.duration = 1;
        frostbolt.cast.animation = "combatspells:one_handed_projectile_charge";
        frostbolt.cast.sound = new Sound("combatspells:casting_frost");
        frostbolt.cast.particles = new ParticleBatch[] {
                new ParticleBatch("snowflake", ParticleBatch.Shape.PILLAR, ParticleBatch.Origin.CENTER, 0.5F, 0.1F, 0.2F)
        };
        frostbolt.range = 64;
        frostbolt.on_release = new Spell.Release();
        frostbolt.on_release.animation = "combatspells:one_handed_projectile_release";
        frostbolt.on_release.sound = new Sound("combatspells:release_frost");
        frostbolt.on_release.target = new Spell.Release.Target();
        frostbolt.on_release.target.type = Spell.Release.Target.Type.PROJECTILE;
        frostbolt.on_release.target.projectile = new Spell.ProjectileData();
        frostbolt.on_release.target.projectile.client_data = new Spell.ProjectileData.Client(
                new ParticleBatch[] {
                        new ParticleBatch("snowflake", ParticleBatch.Shape.CIRCLE, ParticleBatch.Origin.CENTER, 3, 0, 0.1F)
                },
                "ice" // "snowball"
        );
        frostbolt.on_release.target.projectile.client_data.render = Spell.ProjectileData.Client.RenderMode.DEEP;
        frostbolt.on_release.target.projectile.homing_angle = 2;
        frostbolt.on_release.target.projectile.velocity = 1F;

        var frostboltImpact = new Spell.Impact();
        frostboltImpact.sound = new Sound("combatspells:impact_frostbolt");
        frostboltImpact.action = new Spell.Impact.Action();
        frostboltImpact.action.type = Spell.Impact.Action.Type.DAMAGE;
        frostboltImpact.action.damage = new Spell.Impact.Action.Damage();
        frostboltImpact.particles = new ParticleBatch[] {
                new ParticleBatch("soul_fire_flame", ParticleBatch.Shape.SPHERE, ParticleBatch.Origin.CENTER, 130, 0.2F, 0.2F)
        };
        frostbolt.on_impact = new Spell.Impact[] { frostboltImpact };
        spells.put(new Identifier("minecraft", "diamond_sword"), frostbolt);
        System.out.println(gson.toJson(frostbolt));

        var scorch = new Spell();
        scorch.school = MagicSchool.FIRE;
        scorch.cast.duration = 0.5F;
        scorch.range = 32;
        scorch.on_release = new Spell.Release();
        scorch.on_release.target = new Spell.Release.Target();
        scorch.on_release.target.type = Spell.Release.Target.Type.CURSOR;
        scorch.on_release.target.cursor = new Spell.Release.Target.Cursor();
        var scorchDamage = new Spell.Impact();
        scorchDamage.action = new Spell.Impact.Action();
        scorchDamage.action.type = Spell.Impact.Action.Type.DAMAGE;
        scorchDamage.action.damage = new Spell.Impact.Action.Damage();
        scorchDamage.particles = new ParticleBatch[] {
                new ParticleBatch("small_flame", ParticleBatch.Shape.CIRCLE, ParticleBatch.Origin.CENTER, 30, 0.2F, 0.2F)
        };
        scorch.on_impact = new Spell.Impact[] { scorchDamage };
        spells.put(new Identifier("minecraft", "golden_sword"), scorch);


        var frostNova = new Spell();
        frostNova.school = MagicSchool.FROST;
        frostNova.cast.duration = 2;
        frostNova.cooldown_duration = 3;
        frostNova.range = 10;
        frostNova.on_release = new Spell.Release();
        frostNova.on_release.target = new Spell.Release.Target();
        frostNova.on_release.target.type = Spell.Release.Target.Type.AREA;
        frostNova.on_release.target.area = new Spell.Release.Target.Area();
        frostNova.on_release.target.area.vertical_range_multiplier = 0.3F;
        frostNova.on_release.particles = new ParticleBatch[] {
                new ParticleBatch("snowflake", ParticleBatch.Shape.CIRCLE, ParticleBatch.Origin.FEET, 50, 0.2F, 0.2F),
                new ParticleBatch("snowflake", ParticleBatch.Shape.CIRCLE, ParticleBatch.Origin.FEET, 50, 0.4F, 0.4F),
                new ParticleBatch("snowflake", ParticleBatch.Shape.CIRCLE, ParticleBatch.Origin.FEET, 50, 0.6F, 0.6F),
                new ParticleBatch("soul_fire_flame", ParticleBatch.Shape.CIRCLE, ParticleBatch.Origin.FEET, 30, 0.0F, 0.4F),
        };

        var frostDamage = new Spell.Impact();
        frostDamage.action = new Spell.Impact.Action();
        frostDamage.action.type = Spell.Impact.Action.Type.DAMAGE;
        frostDamage.action.damage = new Spell.Impact.Action.Damage();
        frostDamage.particles = new ParticleBatch[] {
                new ParticleBatch("snowflake", ParticleBatch.Shape.CIRCLE, ParticleBatch.Origin.FEET, 30, 0.5F, 3F)
        };
        var frostSlow = new Spell.Impact();
        frostSlow.action = new Spell.Impact.Action();
        frostSlow.action.type = Spell.Impact.Action.Type.STATUS_EFFECT;
        frostSlow.action.status_effect = new Spell.Impact.Action.StatusEffect();
        frostSlow.action.status_effect.effect_id = "slowness";
        frostSlow.action.status_effect.amplifier = 1;
        frostSlow.action.status_effect.duration = 40;
//        frostSlow.particles = new ParticleBatch[] {
//                new ParticleBatch("snowflake", ParticleBatch.Shape.CIRCLE, 30, 0.5F, 3F)
//        };
        frostNova.on_impact = new Spell.Impact[] { frostDamage, frostSlow };

        spells.put(new Identifier("minecraft", "stone_sword"), frostNova);
    }
}
