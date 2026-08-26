package net.spell_engine.api.event;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class CombatEvents {
    public static final Event<EntityAttack> ENTITY_ANY_ATTACK = new Event<EntityAttack>();
    public interface EntityAttack {
        record Args(LivingEntity attacker, Entity target) {}
        void onEntityAttack(Args args);
    }
    public static final Event<PlayerAttack> PLAYER_ANY_ATTACK = new Event<PlayerAttack>();
    public static final Event<PlayerAttack> PLAYER_MELEE_ATTACK = new Event<PlayerAttack>();
    public interface PlayerAttack {
        record Args(Player player, Entity target) {}
        void onPlayerAttack(Args args);
    }

    public static final Event<ItemUse> ITEM_USE = new Event<ItemUse>();
    public interface ItemUse {
        enum Stage { START, TICK, END }
        record Args(LivingEntity user, Stage stage) {}
        void onItemUseStart(Args args);
    }

    public static final Event<EntityDamageTaken> ENTITY_DAMAGE_INCOMING = new Event<EntityDamageTaken>();
    public static final Event<EntityDamageTaken> ENTITY_DAMAGE_TAKEN = new Event<EntityDamageTaken>();
    public interface EntityDamageTaken {
        record Args(LivingEntity entity, DamageSource source, float amount) {}
        void onDamageTaken(Args args);
    }

    public static final Event<PlayerDamageTaken> PLAYER_DAMAGE_INCOMING = new Event<PlayerDamageTaken>();
    public static final Event<PlayerDamageTaken> PLAYER_DAMAGE_TAKEN = new Event<PlayerDamageTaken>();
    public interface PlayerDamageTaken {
        record Args(Player player, DamageSource source, float amount) {}
        void onPlayerDamageTaken(Args args);
    }

    public static final Event<EntityShieldBlock> ENTITY_SHIELD_BLOCK = new Event<EntityShieldBlock>();
    public interface EntityShieldBlock {
        record Args(LivingEntity entity, DamageSource source, float amount) {}
        void onShieldBlock(Args args);
    }

    public static final Event<PlayerShieldBlock> PLAYER_SHIELD_BLOCK = new Event<PlayerShieldBlock>();
    public interface PlayerShieldBlock {
        record Args(Player player, DamageSource source, float amount) {}
        void onShieldBlock(Args args);
    }

    public static final Event<EntityEvasion> ENTITY_EVASION = new Event<EntityEvasion>();
    public interface EntityEvasion {
        record Args(LivingEntity entity, float damageAmount, DamageSource source) {}
        void onEntityEvasion(Args args);
    }
}
