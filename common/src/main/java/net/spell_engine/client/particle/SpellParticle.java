package net.spell_engine.client.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
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
public class SpellParticle extends SingleQuadParticle {
    private final SpriteSet spriteProvider;
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
    private Vec3 followDiff = Vec3.ZERO;
    private double groundY;
    private boolean skipRender = false;

    protected SpellParticle(ClientLevel world, double x, double y, double z,
                            double velocityX, double velocityY, double velocityZ,
                            SpriteSet spriteProvider,
                            SpellEngineParticles.Entry entry,
                            ParticleGroup.Appearance config,
                            @Nullable Entity sourceEntity) {
        super(world, x, y, z, spriteProvider.get(world.getRandom()));
        this.spriteProvider = spriteProvider;
        this.frameCount = entry.texture().frames();
        this.pivot = entry.pivot();

        this.facing = config.facing != null ? config.facing : ParticleGroup.Facing.CAMERA;
        this.render = config.render != null ? config.render : ParticleGroup.Render.TRANSLUCENT;
        this.glow = config.glow != null ? config.glow : true;
        var motion = config.motion != null ? config.motion : ParticleGroup.Motion.STATIC;
        this.motion = motion;

        // MARK: Motion preset (constants ported from V1 SpellUniversalParticle)

        this.hasPhysics = config.collides;
        switch (motion) {
            case STATIC -> {
                this.xd = velocityX;
                this.yd = velocityY;
                this.zd = velocityZ;
                this.friction = 1F;
                this.gravity = 0F;
            }
            case FLOAT, DECELERATE -> {
                this.friction = motion == ParticleGroup.Motion.DECELERATE ? 0.768F : 0.96F;
                this.gravity = 0F;
                this.xd = velocityX + (this.random.nextFloat() - this.random.nextFloat()) * 0.005F;
                this.yd = velocityY + (this.random.nextFloat() - this.random.nextFloat()) * 0.005F;
                this.zd = velocityZ + (this.random.nextFloat() - this.random.nextFloat()) * 0.005F;
                this.x += (this.random.nextFloat() - this.random.nextFloat()) * 0.05F;
                this.y += (this.random.nextFloat() - this.random.nextFloat()) * 0.05F;
                this.z += (this.random.nextFloat() - this.random.nextFloat()) * 0.05F;
                this.setPos(this.x, this.y, this.z);
            }
            case ASCEND -> {
                this.friction = 0.96F;
                this.gravity = -0.1F;
                this.speedUpWhenYMotionIsBlocked = true;
                this.xd = velocityX * (velocityX == 0 && velocityZ == 0 ? 0.1F : 1F)
                        + (this.random.nextFloat() - this.random.nextFloat()) * 0.005F;
                this.yd = velocityY * 0.2F + 0.02F;
                this.zd = velocityZ * (velocityX == 0 && velocityZ == 0 ? 0.1F : 1F)
                        + (this.random.nextFloat() - this.random.nextFloat()) * 0.005F;
            }
            case BURST -> {
                this.friction = 0.7F;
                this.gravity = 0.5F;
                this.xd = velocityX * 0.4F + (this.random.nextFloat() - this.random.nextFloat()) * 0.02F;
                this.yd = velocityY * 0.4F + (this.random.nextFloat() - this.random.nextFloat()) * 0.02F;
                this.zd = velocityZ * 0.4F + (this.random.nextFloat() - this.random.nextFloat()) * 0.02F;
            }
            case DRIFT -> {
                // Vanilla SnowflakeParticle's motion: a wide random scatter, gravity,
                // and per-axis damping that bleeds vertical speed faster than lateral,
                // so particles fan out as they settle rather than dropping in a line.
                this.friction = 1F;
                this.gravity = 0.225F;
                this.xd = velocityX + (Math.random() * 2.0 - 1.0) * 0.05F;
                this.yd = velocityY + (Math.random() * 2.0 - 1.0) * 0.05F;
                this.zd = velocityZ + (Math.random() * 2.0 - 1.0) * 0.05F;
            }
        }
        if (config.gravity != null) {
            this.gravity = config.gravity;
        }
        if (config.drag != null) {
            this.friction = config.drag;
        }

        // MARK: Lifetime & playback

        float playbackSpeed = config.playback_speed == 0F ? 1F : config.playback_speed;
        this.reversedPlayback = playbackSpeed < 0F;
        float lifetimeRoll = 1F + (this.random.nextFloat() * 2F - 1F) * Mth.clamp(config.lifetime_variance, 0F, 1F);
        this.lifetime = Math.max(1, Math.round(
                entry.lifetime() * motion.lifetime_factor * lifetimeRoll / Math.abs(playbackSpeed)));

        // MARK: Appearance

        float darken = 1F - this.random.nextFloat() * Mth.clamp(config.color_variance, 0F, 1F);
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

        float scaleVariance = Mth.clamp(config.scale_variance, 0F, 1F);
        this.spawnScale = config.scale * (1F + (this.random.nextFloat() * 2F - 1F) * scaleVariance);
        this.quadSize = spawnScale;
        this.scaleEasing = config.scale_easing;
        this.scaleMultiplier = config.scale_multiplier;

        // MARK: Attachment

        this.attachment = config.attachment;
        this.followEntity = attachment != ParticleGroup.Attachment.NONE ? sourceEntity : null;
        if (followEntity != null) {
            this.followDiff = new Vec3(this.x - followEntity.getX(), this.y - followEntity.getY(), this.z - followEntity.getZ());
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
        if (this.removed) {
            return;
        }
        if (motion == ParticleGroup.Motion.DRIFT) {
            // Per-axis, so it cannot be expressed through the scalar `drag` field
            this.xd *= 0.95F;
            this.yd *= 0.9F;
            this.zd *= 0.95F;
        }
        float progress = (float) this.age / (float) this.lifetime;
        updateSprite();
        this.alpha = baseOpacity * (opacityCurve != null ? opacityCurve.sample(progress) : 1F) * elevationFade();
        float eased = scaleEasing != null
                ? Mth.lerp(Easing.apply(scaleEasing, progress), 1F, scaleMultiplier)
                : 1F;
        float entityScale = attachment == ParticleGroup.Attachment.POSITION_SCALED
                && followEntity instanceof LivingEntity livingEntity
                ? livingEntity.getScale() : 1F;
        this.quadSize = spawnScale * eased * entityScale;
        updateSkipRender();
    }

    private void updateSprite() {
        if (frameCount > 1) {
            int frameAge = reversedPlayback ? (this.lifetime - this.age) : this.age;
            this.setSprite(spriteProvider.get(Mth.clamp(frameAge, 0, this.lifetime), this.lifetime));
        } else if (this.sprite == null) {
            this.setSprite(spriteProvider.get(this.random));
        }
    }

    @Override
    public void move(double dx, double dy, double dz) {
        if (followEntity != null && !followEntity.isRemoved()) {
            // Following: accumulate own motion into the offset, then track the entity
            this.followDiff = followDiff.add(dx, dy, dz);
            var position = followEntity.position().add(followDiff);
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
        var from = new Vec3(x, followEntity.getY() + GROUND_PROBE_UP, z);
        var hit = TargetHelper.findSolidBelow(followEntity, from, followEntity.level(),
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
        float fadeReach = 2F * Math.max(followEntity.getBbHeight(), 0.1F);
        float gap = (float) (followEntity.getY() - groundY);
        return 1F - Mth.clamp(gap / fadeReach, 0F, 1F);
    }

    // MARK: Rendering

    /// V1 `SpellAreaParticle.checkSkip`: a camera-facing quad attached to the camera
    /// entity would fill the screen in first person.
    private void updateSkipRender() {
        var client = Minecraft.getInstance();
        this.skipRender = facing == ParticleGroup.Facing.CAMERA
                && followEntity != null
                && followEntity == client.getCameraEntity()
                && client.options.getCameraType().isFirstPerson();
    }

    /// Render type = atlas + pipeline since 1.21.9. `LIT` had no dedicated sheet since 1.21.2; it maps to translucent.
    @Override
    protected Layer getLayer() {
        return switch (render) {
            case OPAQUE -> Layer.OPAQUE;
            case TRANSLUCENT, LIT -> Layer.TRANSLUCENT;
        };
    }

    @Override
    protected int getLightCoords(float tint) {
        return glow ? 255 : super.getLightCoords(tint);
    }

    /// -90 and not +90: particle sheets render with backface culling, and vanilla's
    /// corner winding rotated by +90 would leave the quad facing down — invisible
    /// from above. (V1 `SpellAreaParticle` used +90 with mirrored corner winding,
    /// which is the same visible face.)
    private static final FacingCameraMode GROUND_ROTATOR = (quaternion, camera, tickDelta) ->
            quaternion.rotationX((float) Math.toRadians(-90));

    private final FacingCameraMode velocityRotator = (quaternion, camera, tickDelta) -> {
        var direction = new Vector3f((float) this.xd, (float) this.yd, (float) this.zd);
        if (direction.lengthSquared() < 1.0E-6F) {
            quaternion.set(camera.rotation());
        } else {
            direction.normalize();
            quaternion.rotationTo(0F, 1F, 0F, direction.x, direction.y, direction.z);
        }
    };

    @Override
    public FacingCameraMode getFacingCameraMode() {
        return switch (facing) {
            case CAMERA -> FacingCameraMode.LOOKAT_XYZ;
            case UPRIGHT -> FacingCameraMode.LOOKAT_Y;
            case GROUND -> GROUND_ROTATOR;
            case VELOCITY -> velocityRotator;
        };
    }

    /// Vanilla resolves [#getRotator] itself before submitting the quad (queue-based particle rendering
    /// since 1.21.9), so the former Sodium workaround is no longer needed; only the first-person skip remains.
    @Override
    public void extract(QuadParticleRenderState submittable, Camera camera, float tickProgress) {
        if (skipRender) {
            return;
        }
        super.extract(submittable, camera, tickProgress);
    }

    /// Applies the entry's pivot: shifts the quad vertically in units of its size
    /// (V1 `ShiftedParticle`, used by `roots` to stand on the ground).
    ///
    /// [Facing#GROUND] quads lie flat and, being backface-culled like every particle
    /// sheet, vanish the moment the camera drops below them. Area effects read as decals
    /// on the floor, so they should be visible from underneath too — a second quad,
    /// flipped 180° about an in-plane axis, presents the opposite face.
    @Override
    protected void extractRotatedQuad(QuadParticleRenderState submittable, Quaternionf rotation, float x, float y, float z, float tickProgress) {
        float shiftedY = y + pivot * this.getQuadSize(tickProgress);
        super.extractRotatedQuad(submittable, rotation, x, shiftedY, z, tickProgress);
        if (facing == ParticleGroup.Facing.GROUND) {
            var backFace = new Quaternionf(rotation).rotateX((float) Math.PI);
            super.extractRotatedQuad(submittable, backFace, x, shiftedY, z, tickProgress);
        }
    }

    // MARK: Factory

    public static class Factory implements ParticleProvider<ParticleGroupType> {
        private final SpriteSet spriteProvider;
        private final SpellEngineParticles.Entry entry;

        public Factory(SpriteSet spriteProvider, SpellEngineParticles.Entry entry) {
            this.spriteProvider = spriteProvider;
            this.entry = entry;
        }

        @Override
        public Particle createParticle(ParticleGroupType type, ClientLevel world,
                                       double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ, RandomSource random) {
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
