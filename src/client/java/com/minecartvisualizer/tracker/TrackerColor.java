package com.minecartvisualizer.tracker;

public enum TrackerColor {
    WHITE(0xFFFFFF, "white"),
    ORANGE(0xF9801D, "orange"),
    MAGENTA(0xC74EBD, "magenta"),
    LIGHT_BLUE(0x3AB3DA, "light_blue"),
    YELLOW(0xFED83D, "yellow"),
    LIME(0x80C71F, "lime"),
    PINK(0xF38BAA, "pink"),
    GRAY(0x474F52, "gray"),
    LIGHT_GRAY(0x9D9D97, "light_gray"),
    CYAN(0x169C9C, "cyan"),
    PURPLE(0x8932B8, "purple"),
    BLUE(0x3C44AA, "blue"),
    BROWN(0x835432, "brown"),
    GREEN(0x5E7C16, "green"),
    RED(0xB02E26, "red"),
    BLACK(0x1D1D21, "black");

    private final int hex;
    private final String label;

    TrackerColor(int hex, String label) {
        this.hex = hex;
        this.label = label;
    }

    public int getHex() { return hex; }
    public String getLabel() { return label; }
}