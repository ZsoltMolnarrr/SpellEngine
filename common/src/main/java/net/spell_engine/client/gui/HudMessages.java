package net.spell_engine.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.spell_engine.api.effect.EntityActionsAllowed;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.internals.casting.SpellCast;

import java.util.List;
import java.util.Locale;

public class HudMessages {
    public static final HudMessages INSTANCE = new HudMessages();
    public static final int DEFAULT_ERROR_MESSAGE_DURATION = 20;
    public static final int DEFAULT_ERROR_MESSAGE_FADEOUT = 10;

    private MessageState currentMessage;

    private static final String castAttemptPrefix = "hud.cast_attempt_error.";
    private boolean attemptDisplayed = false;
    public void castAttemptError(SpellCast.Attempt attempt) {
        if (attemptDisplayed) {
            return;
        }
        if (attempt.isSuccess() || attempt.isFail()) {
            attemptDisplayed = true;
        }
        if (!attempt.isFail() || !SpellEngineClient.config.showSpellCastErrors) { return; }
        var translationKey = castAttemptPrefix + attempt.result().toString().toLowerCase(Locale.ENGLISH);
        MutableComponent message = null;
        switch (attempt.result()) {
            case MISSING_ITEM -> {
                var item = attempt.missingItem().item();
                if (item != null) {
                    var itemName = I18n.get(item.getTranslationKey());
                    message = Component.translatable(translationKey, itemName);
                }
            }
            case ON_COOLDOWN -> {
                message = Component.translatable(translationKey);
            }
        }
        if (message != null) {
            error(message.withStyle(ChatFormatting.RED));
        }
    }

    public void actionImpaired(EntityActionsAllowed.SemanticType reason) {
        error(I18n.get("hud.action_impaired." + reason.toString().toLowerCase(Locale.ENGLISH)));
    }

    public void error(String message) {
        error(message, DEFAULT_ERROR_MESSAGE_DURATION, DEFAULT_ERROR_MESSAGE_FADEOUT);
    }

    public void error(String message, int duration, int fadeOut) {
        currentMessage = MessageState.error(message, duration, fadeOut);
    }

    public void error(Component text) {
        error(text, DEFAULT_ERROR_MESSAGE_DURATION, DEFAULT_ERROR_MESSAGE_FADEOUT);
    }

    public void error(Component text, int duration, int fadeOut) {
        currentMessage = new MessageState(text, duration, fadeOut);
    }

    public void onCooldownsChanged(List<Identifier> before, List<Identifier> after) {
        var cooldownsRemoved = before.stream()
                .filter(spellId -> !after.contains(spellId))
                .toList();
        var world = Minecraft.getInstance().level;
        if (world == null) {
            return; // No world loaded, cannot display message
        }
        if (cooldownsRemoved.isEmpty()) {
            return;
        }
        if (cooldownsRemoved.size() == 1) {
            var spellId = cooldownsRemoved.getFirst();
            var spellEntry = SpellRegistry.from(world).get(spellId).orElse(null);
            if (spellEntry == null) {
                return; // Spell not found, cannot display message
            }
            var spellName = SpellTooltip.spellTranslationKey(spellId);
            info(Component.translatable("hud.cooldown_cleared.single", I18n.get(spellName) ));
        } else {
            info(Component.translatable("hud.cooldown_cleared.multiple"));
        }
    }

    public void info(Component message) {
        info(message, DEFAULT_ERROR_MESSAGE_DURATION, DEFAULT_ERROR_MESSAGE_FADEOUT);
    }

    public void infoQuick(Component message) {
        info(message, DEFAULT_ERROR_MESSAGE_DURATION / 2, DEFAULT_ERROR_MESSAGE_FADEOUT / 2);
    }

    public void info(Component message, int duration, int fadeOut) {
        currentMessage = new MessageState(message, duration, fadeOut);
    }

    public void tick() {
        if (currentMessage != null) {
            if (currentMessage.durationLeft <= 0) {
                currentMessage = null;
            } else {
                currentMessage.durationLeft -= 1;
            }
        }
        var client = Minecraft.getInstance();
        if (!client.options.keyUse.isDown()) {
            attemptDisplayed = false;
        }
    }

    public static class MessageState {
        public MessageState(Component message, int durationLeft, int fadeOut) {
            this.message = message;
            this.durationLeft = durationLeft;
            this.fadeOut = fadeOut;
        }

        public static MessageState error(String message, int durationLeft, int fadeOut) {
            return new MessageState(Component.literal(message).withStyle(ChatFormatting.RED), durationLeft, fadeOut);
        }

        public Component message;
        public int durationLeft;
        public int fadeOut;
    }

    public MessageState currentError() {
        return currentMessage;
    }
}