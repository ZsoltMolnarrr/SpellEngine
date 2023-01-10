package net.spell_engine.client.util;

public record Color(float red, float green, float blue) {
    public static Color from(int rgb) {
        float red = ((float) ((rgb >> 16) & 0xFF)) / 255F;
        float green = ((float) ((rgb >> 8) & 0xFF)) / 255F;
        float blue = ((float) (rgb & 0xFF)) / 255F;
        return new Color(red, green, blue);
    }

    public static final Color RED = new Color(1, 0, 0);
    public static final Color GREEN = new Color(1, 0, 0);
    public static final Color BLUE = new Color(1, 0, 0);
}
