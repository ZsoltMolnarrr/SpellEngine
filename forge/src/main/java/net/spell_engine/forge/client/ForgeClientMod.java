package net.spell_engine.forge.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.util.Identifier;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.gui.ConfigMenuScreen;
import net.spell_engine.client.gui.HudRenderHelper;
import net.spell_engine.client.input.GuiKeyBinding;
import net.spell_engine.client.input.Keybindings;
import net.spell_engine.client.render.BeamRenderer;
import net.spell_engine.client.render.CustomModelRegistry;
import net.spell_engine.client.render.SpellCloudRenderer;
import net.spell_engine.client.render.SpellModelEffectRenderer;
import net.spell_engine.client.render.SpellProjectileRenderer;
import net.spell_engine.entity.SpellCloud;
import net.spell_engine.entity.SpellModelEffect;
import net.spell_engine.entity.SpellProjectile;
import net.spell_engine.spellbinding.SpellBindingScreen;
import net.spell_engine.spellbinding.SpellBindingScreenHandler;
import net.spell_engine.spellbinding.spellchoice.SpellChoiceScreen;
import net.spell_engine.spellbinding.spellchoice.SpellChoiceScreenHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

/// Client-only wiring for Forge 47; only touched from {@link net.spell_engine.forge.ForgeMod} behind a
/// `Dist.CLIENT` check. Mod-bus listeners are registered explicitly (no `@EventBusSubscriber` scanning).
///
/// 1.20.1 port: the NeoForge `RegisterGuiLayersEvent` / `RegisterMenuScreensEvent` / `IConfigScreenFactory`
/// usages are mapped to their Forge 47 equivalents (`RegisterGuiOverlaysEvent`, `HandledScreens.register` in
/// client setup, `ConfigScreenHandler.ConfigScreenFactory`).
///
/// Integrator wiring (in `ForgeMod`'s constructor, behind `FMLEnvironment.dist == Dist.CLIENT`):
/// `ForgeClientMod.register(FMLJavaModLoadingContext.get().getModEventBus());` — that single call installs the
/// client-setup, HUD overlay (`registerGuiOverlays` → `RegisterGuiOverlaysEvent.registerAbove(HOTBAR)`),
/// key mapping, particle provider, entity renderer and additional-model listeners.
public final class ForgeClientMod {
    public static void register(IEventBus modBus) {
        modBus.addListener(EventPriority.NORMAL, false, FMLClientSetupEvent.class, ForgeClientMod::onClientSetup);
        modBus.addListener(EventPriority.NORMAL, false, RegisterGuiOverlaysEvent.class, ForgeClientMod::registerGuiOverlays);
        modBus.addListener(EventPriority.NORMAL, false, RegisterKeyMappingsEvent.class, ForgeClientMod::registerKeys);
        modBus.addListener(EventPriority.NORMAL, false, RegisterParticleProvidersEvent.class, ForgeClientMod::registerParticleProviders);
        modBus.addListener(EventPriority.NORMAL, false, EntityRenderersEvent.RegisterRenderers.class, ForgeClientMod::registerEntityRenderers);
        modBus.addListener(EventPriority.NORMAL, false, ModelEvent.RegisterAdditional.class, ForgeClientMod::registerAdditionalModels);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        SpellEngineClient.init();

        // Game-bus client events (tooltip lines, beam world-render pass); the callbacks live in
        // loader-neutral common code.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ItemTooltipEvent.class, tooltip ->
                SpellEngineClient.addTooltipLines(tooltip.getItemStack(), tooltip.getFlags(), tooltip.getToolTip()));
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RenderLevelStageEvent.class, render -> {
            if (render.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                BeamRenderer.renderAfterTranslucent(render.getPoseStack(), render.getCamera(), render.getPartialTick());
            }
        });
        event.enqueueWork(() -> {
            // Forge 47 has no RegisterMenuScreensEvent; screens are registered in client setup.
            HandledScreens.register(SpellBindingScreenHandler.HANDLER_TYPE, SpellBindingScreen::new);
            HandledScreens.register(SpellChoiceScreenHandler.HANDLER_TYPE, SpellChoiceScreen::new);
            SpellEngineClient.onClientStarted();
        });

        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((client, parent) -> new ConfigMenuScreen(parent)));
    }

    public static final Identifier SPELL_HUD_LAYER_ID = new Identifier(SpellEngineMod.ID, "spell_hud");
    private static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), SPELL_HUD_LAYER_ID.getPath(), (gui, graphics, partialTick, screenWidth, screenHeight) -> {
            if (MinecraftClient.getInstance().options.hudHidden) { return; }
            HudRenderHelper.render(graphics, partialTick);
        });
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        for (var keybinding: Keybindings.all()) {
            if (keybinding instanceof GuiKeyBinding) {
                // Forge natively understands GUI scoped bindings, and its key lookup
                // only activates them while a screen is open. (The controls screen still
                // marks the key red, vanilla bindings conflict with every context.)
                keybinding.setKeyConflictContext(KeyConflictContext.GUI);
            }
            event.register(keybinding);
        }
    }

    private static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        SpellEngineClient.registerParticleAppearances(new SpellEngineClient.ParticleAppearanceRegistrar() {
            @Override
            public <T extends ParticleEffect> void register(ParticleType<T> type, SpellEngineClient.SpriteFactory<T> factory) {
                event.registerSpriteSet(type, factory::create);
            }
        });
    }

    private static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SpellProjectile.ENTITY_TYPE, SpellProjectileRenderer::new);
        event.registerEntityRenderer(SpellCloud.ENTITY_TYPE, SpellCloudRenderer::new);
        event.registerEntityRenderer(SpellModelEffect.ENTITY_TYPE, SpellModelEffectRenderer::new);
    }

    private static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        // 1.20.1: additional models are registered — and later looked up — by their plain Identifier
        // (Forge 47 `ModelEvent.RegisterAdditional.register(Identifier)`, stored in `BakedModelManager.models`,
        // read back through `BakedModelManagerAccessor`), exactly like Fabric's `ModelLoadingPlugin.addModels`.

        // Register custom models from registry
        for (var id: CustomModelRegistry.getModelIds()) {
            event.register(id);
        }

        // Register dynamically discovered spell models (scrolls, books, projectiles, effects)
        ForgeModelDiscovery.registerCustomModels(event);
    }
}
