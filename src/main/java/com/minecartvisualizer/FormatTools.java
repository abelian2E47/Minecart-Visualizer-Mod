package com.minecartvisualizer;

import net.minecraft.util.math.Vec3d;

public class FormatTools {
    public static String formatVec(Vec3d vec, int accuracy, boolean useSignificant) {
        return String.format("(%s, %s, %s)",
                formatDouble(vec.x, accuracy, useSignificant),
                formatDouble(vec.y, accuracy, useSignificant),
                formatDouble(vec.z, accuracy, useSignificant));
    }

    public static String formatDouble(double value, int accuracy, boolean useSignificant) {
        if (Math.abs(value) < 1e-9) return "0.00";

        if (useSignificant) {
            return String.format("%." + accuracy + "g", value);
        } else {
            return String.format("%." + accuracy + "f", value);
        }
    }
}
