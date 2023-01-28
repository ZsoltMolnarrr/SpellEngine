package net.spell_engine.client.gui;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class HudMessages {
    public static final HudMessages INSTANCE = new HudMessages();
    public static final int DEFAULT_ERROR_MESSAGE_DURATION = 20;
    public static final int DEFAULT_ERROR_MESSAGE_FADEOUT = 10;

    private ErrorMessageState currentError;

    public void error(String message) {
        error(message, DEFAULT_ERROR_MESSAGE_DURATION, DEFAULT_ERROR_MESSAGE_FADEOUT);
    }

    public void error(String message, int duration, int fadeOut) {
        currentError = new ErrorMessageState(message, duration, fadeOut);
    }

    public void tick() {
        if (currentError != null) {
            if (currentError.durationLeft <= 0) {
                currentError = null;
            } else {
                currentError.durationLeft -= 1;
            }
        }
    }

    public static class ErrorMessageState {
        public ErrorMessageState(String message, int durationLeft, int fadeOut) {
            this.message = Text.literal(message).formatted(Formatting.RED);
            this.durationLeft = durationLeft;
            this.fadeOut = fadeOut;
        }
        public Text message;
        public int durationLeft;
        public int fadeOut;
    }

    public ErrorMessageState currentError() {
        return currentError;
    }
}
