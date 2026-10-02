package com.minecartvisualizer.config;

import java.awt.Color;


public final class Colors {

    private Colors() {
    }

    public static int rgb(Color color, int fallbackRgb) {
        return (color != null ? color.getRGB() : fallbackRgb) & 0xFFFFFF;
    }

    public static float[] rgbFloats(Color color, int fallbackRgb) {
        int rgb = rgb(color, fallbackRgb);
        return new float[]{
                ((rgb >> 16) & 0xFF) / 255.0f,
                ((rgb >> 8) & 0xFF) / 255.0f,
                (rgb & 0xFF) / 255.0f
        };
    }
}
