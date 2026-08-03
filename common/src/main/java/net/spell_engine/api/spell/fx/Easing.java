package net.spell_engine.api.spell.fx;

/// Conventional easing functions from [easings.net](https://easings.net)
public enum Easing {
    LINEAR,
    EASE_IN_SINE, EASE_OUT_SINE, EASE_IN_OUT_SINE,
    EASE_IN_QUAD, EASE_OUT_QUAD, EASE_IN_OUT_QUAD,
    EASE_IN_CUBIC, EASE_OUT_CUBIC, EASE_IN_OUT_CUBIC,
    EASE_IN_QUART, EASE_OUT_QUART, EASE_IN_OUT_QUART,
    EASE_IN_QUINT, EASE_OUT_QUINT, EASE_IN_OUT_QUINT,
    EASE_IN_EXPO, EASE_OUT_EXPO, EASE_IN_OUT_EXPO,
    EASE_IN_CIRC, EASE_OUT_CIRC, EASE_IN_OUT_CIRC,
    EASE_IN_BACK, EASE_OUT_BACK, EASE_IN_OUT_BACK,
    EASE_IN_ELASTIC, EASE_OUT_ELASTIC, EASE_IN_OUT_ELASTIC,
    EASE_IN_BOUNCE, EASE_OUT_BOUNCE, EASE_IN_OUT_BOUNCE;

    private static final float PI = (float) Math.PI;
    private static final float C1 = 1.70158F;
    private static final float C2 = C1 * 1.525F;
    private static final float C3 = C1 + 1F;
    private static final float C4 = (2F * PI) / 3F;
    private static final float C5 = (2F * PI) / 4.5F;

    public static float apply(Easing easing, float x) {
        return switch (easing) {
            case LINEAR -> x;

            // Sine
            case EASE_IN_SINE -> 1F - (float) Math.cos((x * PI) / 2F);
            case EASE_OUT_SINE -> (float) Math.sin((x * PI) / 2F);
            case EASE_IN_OUT_SINE -> -((float) Math.cos(PI * x) - 1F) / 2F;

            // Quad
            case EASE_IN_QUAD -> x * x;
            case EASE_OUT_QUAD -> 1F - (1F - x) * (1F - x);
            case EASE_IN_OUT_QUAD -> x < 0.5F ? 2F * x * x : 1F - pow(-2F * x + 2F, 2) / 2F;

            // Cubic
            case EASE_IN_CUBIC -> x * x * x;
            case EASE_OUT_CUBIC -> 1F - pow(1F - x, 3);
            case EASE_IN_OUT_CUBIC -> x < 0.5F ? 4F * x * x * x : 1F - pow(-2F * x + 2F, 3) / 2F;

            // Quart
            case EASE_IN_QUART -> x * x * x * x;
            case EASE_OUT_QUART -> 1F - pow(1F - x, 4);
            case EASE_IN_OUT_QUART -> x < 0.5F ? 8F * x * x * x * x : 1F - pow(-2F * x + 2F, 4) / 2F;

            // Quint
            case EASE_IN_QUINT -> x * x * x * x * x;
            case EASE_OUT_QUINT -> 1F - pow(1F - x, 5);
            case EASE_IN_OUT_QUINT -> x < 0.5F ? 16F * x * x * x * x * x : 1F - pow(-2F * x + 2F, 5) / 2F;

            // Expo
            case EASE_IN_EXPO -> x == 0F ? 0F : pow(2F, 10F * x - 10F);
            case EASE_OUT_EXPO -> x == 1F ? 1F : 1F - pow(2F, -10F * x);
            case EASE_IN_OUT_EXPO -> x == 0F ? 0F : x == 1F ? 1F
                    : x < 0.5F ? pow(2F, 20F * x - 10F) / 2F
                    : (2F - pow(2F, -20F * x + 10F)) / 2F;

            // Circ
            case EASE_IN_CIRC -> 1F - (float) Math.sqrt(1F - x * x);
            case EASE_OUT_CIRC -> (float) Math.sqrt(1F - (x - 1F) * (x - 1F));
            case EASE_IN_OUT_CIRC -> x < 0.5F
                    ? (1F - (float) Math.sqrt(1F - 4F * x * x)) / 2F
                    : ((float) Math.sqrt(1F - (-2F * x + 2F) * (-2F * x + 2F)) + 1F) / 2F;

            // Back
            case EASE_IN_BACK -> C3 * x * x * x - C1 * x * x;
            case EASE_OUT_BACK -> 1F + C3 * pow(x - 1F, 3) + C1 * pow(x - 1F, 2);
            case EASE_IN_OUT_BACK -> x < 0.5F
                    ? (pow(2F * x, 2) * ((C2 + 1F) * 2F * x - C2)) / 2F
                    : (pow(2F * x - 2F, 2) * ((C2 + 1F) * (x * 2F - 2F) + C2) + 2F) / 2F;

            // Elastic
            case EASE_IN_ELASTIC -> x == 0F ? 0F : x == 1F ? 1F
                    : -pow(2F, 10F * x - 10F) * (float) Math.sin((x * 10F - 10.75F) * C4);
            case EASE_OUT_ELASTIC -> x == 0F ? 0F : x == 1F ? 1F
                    : pow(2F, -10F * x) * (float) Math.sin((x * 10F - 0.75F) * C4) + 1F;
            case EASE_IN_OUT_ELASTIC -> x == 0F ? 0F : x == 1F ? 1F
                    : x < 0.5F
                    ? -(pow(2F, 20F * x - 10F) * (float) Math.sin((20F * x - 11.125F) * C5)) / 2F
                    : (pow(2F, -20F * x + 10F) * (float) Math.sin((20F * x - 11.125F) * C5)) / 2F + 1F;

            // Bounce
            case EASE_IN_BOUNCE -> 1F - bounceOut(1F - x);
            case EASE_OUT_BOUNCE -> bounceOut(x);
            case EASE_IN_OUT_BOUNCE -> x < 0.5F
                    ? (1F - bounceOut(1F - 2F * x)) / 2F
                    : (1F + bounceOut(2F * x - 1F)) / 2F;
        };
    }

    private static float pow(float base, float exponent) {
        return (float) Math.pow(base, exponent);
    }

    private static float bounceOut(float x) {
        final float n1 = 7.5625F;
        final float d1 = 2.75F;
        if (x < 1F / d1) {
            return n1 * x * x;
        } else if (x < 2F / d1) {
            x -= 1.5F / d1;
            return n1 * x * x + 0.75F;
        } else if (x < 2.5F / d1) {
            x -= 2.25F / d1;
            return n1 * x * x + 0.9375F;
        } else {
            x -= 2.625F / d1;
            return n1 * x * x + 0.984375F;
        }
    }
}
