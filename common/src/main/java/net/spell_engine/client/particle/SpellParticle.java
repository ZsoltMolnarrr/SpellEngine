package net.spell_engine.client.particle;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.BillboardParticleSubmittable;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.spell_engine.api.spell.fx.Easing;
import net.spell_engine.api.spell.fx.ParticleGroup;
import net.spell_engine.client.util.Color;
import net.spell_engine.fx.ParticleGroupType;
import net.spell_engine.fx.SpellEngineParticles;
import net.spell_engine.utils.TargetHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/// The single particle implementation behind every Spell Engine particle.
///
/// All appearance and behaviour comes from data: the registered
/// [SpellEngineParticles.Entry] supplies defaults, the spawning
/// [ParticleGroup.Appearance] payload overrides them, and [Factory#resolve]
/// merges the two. This replaces the V1 zoo of hand-written particle classes
/// (flame, universal, area, smoke, snowflake, explosion, shifted) and their
/// per-entry factory wiring.
public class SpellParticle extends BillboardParticle {
    private final SpriteProvider spriteProvider;
    private final int frameCount;
    private final boolean reversedPlayback;

    private final ParticleGroup.Facing facing;
    private final ParticleGroup.Render render;
    private final boolean glow;
    private final float pivot;

    @Nullable private final Easing.Curve opacityCurve;
    @Nullable private final Easing scaleEasing;
    private final float scaleMultiplier;
    private final float baseOpacity;
    private final float spawnScale;

    private final ParticleGroup.Motion motion;
    private final ParticleGroup.Attachment attachment;
    @Nullable private final Entity followEntity;
    private Vec3d followDiff = Vec3d.ZERO;
    private double groundY;
    private boolean skipRender = false;

    protected SpellParticle(ClientWorld world, double x, double y, double z,
                            double velocityX, double velocityY, double velocityZ,
                            SpriteProvider spriteProvider,
                            SpellEngineParticles.Entry entry,
                            ParticleGroup.Appearance config,
                            @Nullable Entity sourceEntity) {
        super(world, x, y, z, spriteProvider.getSprite(world.getRandom()));
        this.spriteProvider = spriteProvider;
        this.frameCount = entry.texture().frames();
        this.pivot = entry.pivot();

        this.facing = config.facing != null ? config.facing : ParticleGroup.Facing.CAMERA;
        this.render = config.render != null ? config.render : ParticleGroup.Render.TRANSLUCENT;
        this.glow = config.glow != null ? config.glow : true;
        var motion = config.motion != null ? config.motion : ParticleGroup.Motion.STATIC;
        this.motion = motion;

        // MARK: Motion preset (constants ported from V1 SpellUniversalParticle)

        this.collidesWithWorld = config.collides;
        switch (motion) {
            case STATIC -> {
                this.velocityX = velocityX;
                this.velocityY = velocityY;
                this.velocityZ = velocityZ;
                this.velocityMultiplier = 1F;
                this.gravityStrength = 0F;
            }
            case FLOAT, DECELERATE -> {
                this.velocityMultiplier = motion == ParticleGroup.Motion.DECELERATE ? 0.768F : 0.96F;
                this.gravityStrength = 0F;
                this.velocityX = velocityX + (this.random.nextFloat() - this.random.nextFloat()) * 0.005F;
                this.velocityY = velocityY + (this.random.nextFloat() - this.random.nextFloat()) * 0.005F;
                this.velocityZ = velocityZ + (this.random.nextFloat() - this.random.nextFloat()) * 0.005F;
                this.x += (this.random.nextFloat() - this.random.nextFloat()) * 0.05F;
                this.y += (this.random.nextFloat() - this.random.nextFloat()) * 0.05F;
                this.z += (this.random.nextFloat() - this.random.nextFloat()) * 0.05F;
                this.setPos(this.x, this.y, this.z);
            }
            case ASCEND -> {
                this.velocityMultiplier = 0.96F;
                this.gravityStrength = -0.1F;
                this.ascending = true;
                this.velocityX = velocityX * (velocityX == 0 && velocityZ == 0 ? 0.1F : 1F)
                        + (this.random.nextFloat() - this.random.nextFloat()) * 0.005F;
                this.velocityY = velocityY * 0.2F + 0.02F;
                this.velocityZ = velocityZ * (velocityX == 0 && velocityZ == 0 ? 0.1F : 1F)
                        + (this.random.nextFloat() - this.random.nextFloat()) * 0.005F;
            }
            case BURST -> {
                this.velocityMultiplier = 0.7F;
                this.gravityStrength = 0.5F;
                this.velocityX = velocityX * 0.4F + (this.random.nextFloat() - this.random.nextFloat()) * 0.02F;
                this.velocityY = velocityY * 0.4F + (this.random.nextFloat() - this.random.nextFloat()) * 0.02F;
                this.velocityZ = velocityZ * 0.4F + (this.random.nextFloat() - this.random.nextFloat()) * 0.02F;
            }
            case DRIFT -> {
                // Vanilla SnowflakeParticle's motion: a wide random scatter, gravity,
                // and per-axis damping that bleeds vertical speed faster than lateral,
                // so particles fan out as they settle rather than dropping in a line.
                this.velocityMultiplier = 1F;
                this.gravityStrength = 0.225F;
                this.velocityX = velocityX + (Math.random() * 2.0 - 1.0) * 0.05F;
                this.velocityY = velocityY + (Math.random() * 2.0 - 1.0) * 0.05F;
                this.velocityZ = velocityZ + (Math.random() * 2.0 - 1.0) * 0.05F;
            }
        }
        if (config.gravity != null) {
            this.gravityStrength = config.gravity;
        }
        if (config.drag != null) {
            this.velocityMultiplier = config.drag;
        }

        // MARK: Lifetime & playback

        float playbackSpeed = config.playback_speed == 0F ? 1F : config.playback_speed;
        this.reversedPlayback = playbackSpeed < 0F;
        float lifetimeRoll = 1F + (this.random.nextFloat() * 2F - 1F) * MathHelper.clamp(config.lifetime_variance, 0F, 1F);
        this.maxAge = Math.max(1, Math.round(
                entry.lifetime() * motion.lifetime_factor * lifetimeRoll / Math.abs(playbackSpeed)));

        // MARK: Appearance

        float darken = 1F - this.random.nextFloat() * MathHelper.clamp(config.color_variance, 0F, 1F);
        float colorAlpha = 1F;
        if (config.color >= 0) {
            var color = Color.fromRGBA(config.color);
            this.setColor(color.red() * darken, color.green() * darken, color.blue() * darken);
            colorAlpha = color.alpha();
        } else {
            this.setColor(darken, darken, darken);
        }
        this.baseOpacity = config.opacity * colorAlpha;
        this.opacityCurve = config.opacity_curve;
        this.alpha = baseOpacity * (opacityCurve != null ? opacityCurve.sample(0F) : 1F);

        float scaleVariance = MathHelper.clamp(config.scale_variance, 0F, 1F);
        this.spawnScale = config.scale * (1F + (this.random.nextFloat() * 2F - 1F) * scaleVariance);
        this.scale = spawnScale;
        this.scaleEasing = config.scale_easing;
        this.scaleMultiplier = config.scale_multiplier;

        // MARK: Attachment

        this.attachment = config.attachment;
        this.followEntity = attachment != ParticleGroup.Attachment.NONE ? sourceEntity : null;
        if (followEntity != null) {
            this.followDiff = new Vec3d(this.x - followEntity.getX(), this.y - followEntity.getY(), this.z - followEntity.getZ());
        }
        // Seed the ground cache with the spawn height (a GROUND-anchored batch already
        // resolves to the floor), so the first frame is correct before any re-probe.
        this.groundY = this.y;

        updateSprite();
        updateSkipRender();
    }

    // MARK: Ticking

    @Override
    public void tick() {
        super.tick();
        if (this.dead) {
            return;
        }
        if (motion == ParticleGroup.Motion.DRIFT) {
            // Per-axis, so it cannot be expressed through the scalar `drag` field
            this.velocityX *= 0.95F;
            this.velocityY *= 0.9F;
            this.velocityZ *= 0.95F;
        }
        float progress = (float) this.age / (float) this.maxAge;
        updateSprite();
        this.alpha = baseOpacity * (opacityCurve != null ? opacityCurve.sample(progress) : 1F) * elevationFade();
        float eased = scaleEasing != null
                ? MathHelper.lerp(Easing.apply(scaleEasing, progress), 1F, scaleMultiplier)
                : 1F;
        float entityScale = attachment == ParticleGroup.Attachment.POSITION_SCALED
                && followEntity instanceof LivingEntity livingEntity
                ? livingEntity.getScale() : 1F;
        this.scale = spawnScale * eased * entityScale;
        updateSkipRender();
    }

    private void updateSprite() {
        if (frameCount > 1) {
            int frameAge = reversedPlayback ? (this.maxAge - this.age) : this.age;
            this.setSprite(spriteProvider.getSprite(MathHelper.clamp(frameAge, 0, this.maxAge), this.maxAge));
        } else if (this.sprite == null) {
            this.setSprite(spriteProvider.getSprite(this.random));
        }
    }

    @Override
    public void move(double dx, double dy, double dz) {
        if (followEntity != null && !followEntity.isRemoved()) {
            // Following: accumulate own motion into the offset, then track the entity
            this.followDiff = followDiff.add(dx, dy, dz);
            var position = followEntity.getEntityPos().add(followDiff);
            double y = attachment == ParticleGroup.Attachment.POSITION_HORIZONTAL
                    ? groundBelow(position.x, position.z)
                    : position.y;
            this.setPos(position.x, y, position.z);
        } else {
            super.move(dx, dy, dz);
        }
    }

    // MARK: Ground pinning

    /// Probe distances for [ParticleGroup.Attachment#POSITION_HORIZONTAL], relative to
    /// the entity's feet. The upward slack catches ground up to a step higher than the
    /// entity; the downward reach finds the floor while the entity is airborne.
    private static final float GROUND_PROBE_UP = 1F;
    private static final float GROUND_PROBE_DOWN = 6F;
    /// Lift off the surface, matching the `GROUND` anchor's own `+0.1` in ParticleHelper.
    private static final double GROUND_LIFT = 0.1;

    /// The floor height directly under a horizontal position. A miss (a gap, an overhang,
    /// the void) holds the last known height rather than dropping the particle to y=0.
    private double groundBelow(double x, double z) {
        var from = new Vec3d(x, followEntity.getY() + GROUND_PROBE_UP, z);
        var hit = TargetHelper.findSolidBelow(followEntity, from, followEntity.getEntityWorld(),
                -(GROUND_PROBE_UP + GROUND_PROBE_DOWN));
        if (hit != null) {
            groundY = hit.y + GROUND_LIFT;
        }
        return groundY;
    }

    /// Fades a ground-pinned decal out as its entity climbs away from the floor it sits
    /// on: full opacity while grounded, reaching zero once the entity is two body
    /// heights up. Keeps a rune from lingering at full strength under a player who has
    /// jumped or been launched far overhead. Only [ParticleGroup.Attachment#POSITION_HORIZONTAL]
    /// is affected; every other particle returns `1`.
    private float elevationFade() {
        if (attachment != ParticleGroup.Attachment.POSITION_HORIZONTAL || followEntity == null) {
            return 1F;
        }
        float fadeReach = 2F * Math.max(followEntity.getHeight(), 0.1F);
        float gap = (float) (followEntity.getY() - groundY);
        return 1F - MathHelper.clamp(gap / fadeReach, 0F, 1F);
    }

    // MARK: Rendering

    /// V1 `SpellAreaParticle.checkSkip`: a camera-facing quad attached to the camera
    /// entity would fill the screen in first person.
    private void updateSkipRender() {
        var client = MinecraftClient.getInstance();
        this.skipRender = facing == ParticleGroup.Facing.CAMERA
                && followEntity != null
                && followEntity == client.getCameraEntity()
                && client.options.getPerspective().isFirstPerson();
    }

    /// Render type = atlas + pipeline since 1.21.9. `LIT` had no dedicated sheet since 1.21.2; it maps to translucent.
    @Override
    protected RenderType getRenderType() {
        return switch (render) {
            case OPAQUE -> RenderType.PARTICLE_ATLAS_OPAQUE;
            case TRANSLUCENT, LIT -> RenderType.PARTICLE_ATLAS_TRANSLUCENT;
        };
    }

    @Override
    public int getBrightness(float tint) {
        return glow ? 255 : super.getBrightness(tint);
    }

    /// -90 and not +90: particle sheets render with backface culling, and vanilla's
    /// corner winding rotated by +90 would leave the quad facing down — invisible
    /// from above. (V1 `SpellAreaParticle` used +90 with mirrored corner winding,
    /// which is the same visible face.)
    private static final Rotator GROUND_ROTATOR = (quaternion, camera, tickDelta) ->
            quaternion.rotationX((float) Math.toRadians(-90));

    private final Rotator velocityRotator = (quaternion, camera, tickDelta) -> {
        var direction = new Vector3f((float) this.velocityX, (float) this.velocityY, (float) this.velocityZ);
        if (direction.lengthSquared() < 1.0E-6F) {
            quaternion.set(camera.getRotation());
        } else {
            direction.normalize();
            quaternion.rotationTo(0F, 1F, 0F, direction.x, direction.y, direction.z);
        }
    };

    @Override
    public Rotator getRotator() {
        return switch (facing) {
            case CAMERA -> Rotator.ALL_AXIS;
            case UPRIGHT -> Rotator.Y_AND_W_ONLY;
            case GROUND -> GROUND_ROTATOR;
            case VELOCITY -> velocityRotator;
        };
    }

    /// Vanilla resolves [#getRotator] itself before submitting the quad (queue-based particle rendering
    /// since 1.21.9), so the former Sodium workaround is no longer needed; only the first-person skip remains.
    @Override
    public void render(BillboardParticleSubmittable submittable, Camera camera, float tickProgress) {
        if (skipRender) {
            return;
        }
        super.render(submittable, camera, tickProgress);
    }

    /// Applies the entry's pivot: shifts the quad vertically in units of its size
    /// (V1 `ShiftedParticle`, used by `roots` to stand on the ground).
    ///
    /// [Facing#GROUND] quads lie flat and, being backface-culled like every particle
    /// sheet, vanish the moment the camera drops below them. Area effects read as decals
    /// on the floor, so they should be visible from underneath too — a second quad,
    /// flipped 180° about an in-plane axis, presents the opposite face.
    @Override
    protected void renderVertex(BillboardParticleSubmittable submittable, Quaternionf rotation, float x, float y, float z, float tickProgress) {
        float shiftedY = y + pivot * this.getSize(tickProgress);
        super.renderVertex(submittable, rotation, x, shiftedY, z, tickProgress);
        if (facing == ParticleGroup.Facing.GROUND) {
            var backFace = new Quaternionf(rotation).rotateX((float) Math.PI);
            super.renderVertex(submittable, backFace, x, shiftedY, z, tickProgress);
        }
    }

    // MARK: Factory

    public static class Factory implements ParticleFactory<ParticleGroupType> {
        private final SpriteProvider spriteProvider;
        private final SpellEngineParticles.Entry entry;

        public Factory(SpriteProvider spriteProvider, SpellEngineParticles.Entry entry) {
            this.spriteProvider = spriteProvider;
            this.entry = entry;
        }

        @Override
        public Particle createParticle(ParticleGroupType type, ClientWorld world,
                                       double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ, Random random) {
            var resolved = resolve(entry, type.payload());
            return new SpellParticle(world, x, y, z, velocityX, velocityY, velocityZ,
                    spriteProvider, entry, resolved, type.sourceEntity());
        }

        /// Merges the entry's defaults with a spawn payload.
        /// Multiplicative fields (`scale`, `opacity`, `playback_speed`) compose;
        /// nullable and sentinel fields override when set.
        public static ParticleGroup.Appearance resolve(SpellEngineParticles.Entry entry,
                                                           @Nullable ParticleGroup.Appearance payload) {
            var base = entry.defaults();
            if (payload == null) {
                return base;
            }
            var resolved = base.copy();
            resolved.playback_speed = base.playback_speed * (payload.playback_speed == 0F ? 1F : payload.playback_speed);
            resolved.opacity = base.opacity * payload.opacity;
            resolved.scale = base.scale * payload.scale;
            if (payload.color != -1) { resolved.color = payload.color; }
            if (payload.color_variance != 0F) { resolved.color_variance = payload.color_variance; }
            if (payload.opacity_curve != null) { resolved.opacity_curve = payload.opacity_curve; }
            if (payload.scale_variance != 0F) { resolved.scale_variance = payload.scale_variance; }
            if (payload.lifetime_variance != 0F) { resolved.lifetime_variance = payload.lifetime_variance; }
            if (payload.scale_easing != null) {
                resolved.scale_easing = payload.scale_easing;
                resolved.scale_multiplier = payload.scale_multiplier;
            }
            if (payload.facing != null) { resolved.facing = payload.facing; }
            if (payload.glow != null) { resolved.glow = payload.glow; }
            if (payload.render != null) { resolved.render = payload.render; }
            if (payload.motion != null) { resolved.motion = payload.motion; }
            if (payload.gravity != null) { resolved.gravity = payload.gravity; }
            if (payload.drag != null) { resolved.drag = payload.drag; }
            if (payload.collides) { resolved.collides = true; }
            if (payload.attachment != ParticleGroup.Attachment.NONE) { resolved.attachment = payload.attachment; }
            return resolved;
        }
    }
}
