package net.spell_engine.client.animation;

import com.mojang.logging.LogUtils;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.data.gson.AnimationSerializing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/// Spell Engine's own player-animation registry (as on legacy 1.20.1).
///
/// PlayerAnimator 1.x (`player-animator` / `playeranimator`, the 1.20.1-era release) scans the **singular**
/// `assets/<namespace>/player_animation` folder and keys animations by the *name written inside the JSON*,
/// while the 2.x line (1.21+) scans the plural `player_animations` folder. Rather than move every ecosystem
/// asset to match 1.x, Spell Engine loads the animations itself out of the (unchanged) plural folder and keys
/// them by `<namespace>:<file name>` — exactly the ids spells reference. Only the *loading* differs between
/// the two PlayerAnimator lines; the animation JSON format, the playback API (`KeyframeAnimation`,
/// `mutableCopy`, `KeyframeAnimationPlayer`, the modifier layers) is identical, so playback code is shared.
///
/// Loading is loader-neutral (no Fabric/Forge resource-reload API in `common`): `ReloadableResourceManagerImplMixin`
/// (re)loads on every client resource reload, and a lookup before that ever happened falls back to reading the
/// client resource manager on demand.
public class AnimationRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    /// Folder animations are read from — plural, matching the PlayerAnimator 2.x convention the
    /// ecosystem's assets already use.
    public static final String FOLDER = "player_animations";

    private static Map<String, KeyframeAnimation> animations = Map.of();
    private static boolean loaded = false;
    private static final java.util.Set<String> warnedIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /// Marks the registry stale; the next lookup re-reads the resource manager.
    public static void invalidate() {
        loaded = false;
    }

    /// The animation registered for `<namespace>:<name>`, or `null` when there is none.
    @Nullable
    public static KeyframeAnimation get(@Nullable String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        ensureLoaded();
        return animations.get(id);
    }

    /// As {@link #get(String)}, but logs once per unknown id instead of failing silently.
    @Nullable
    public static KeyframeAnimation getOrWarn(@Nullable String id) {
        var animation = get(id);
        if (animation == null && id != null && !id.isEmpty() && warnedIds.add(id)) {
            LOGGER.warn("No player animation registered for `{}` (looked in `{}` of every loaded namespace)", id, FOLDER);
        }
        return animation;
    }

    /// All currently loaded animations, keyed by `<namespace>:<name>`.
    public static Map<String, KeyframeAnimation> animations() {
        ensureLoaded();
        return animations;
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        var client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        var resourceManager = client.getResourceManager();
        if (resourceManager == null) {
            return;
        }
        load(resourceManager);
    }

    public static void load(ResourceManager resourceManager) {
        var loading = new HashMap<String, KeyframeAnimation>();
        for (var entry : resourceManager.findResources(FOLDER, fileName -> fileName.getPath().endsWith(".json")).entrySet()) {
            var identifier = entry.getKey();
            var resource = entry.getValue();
            try (var stream = resource.getInputStream()) {
                var readAnimations = AnimationSerializing.deserializeAnimation(stream);
                if (readAnimations.isEmpty()) {
                    continue;
                }
                // `spell_engine:player_animations/dodge.json` -> `spell_engine:dodge`
                var id = identifier.toString().replace(FOLDER + "/", "");
                id = id.substring(0, id.lastIndexOf('.'));
                loading.put(id, readAnimations.get(0));
            } catch (Exception e) {
                LOGGER.error("Failed to load player animation: " + identifier, e);
            }
        }
        animations = Map.copyOf(loading);
        loaded = true;
        warnedIds.clear();
        LOGGER.info("Spell Engine loaded {} player animations from `{}`", animations.size(), FOLDER);
    }
}
