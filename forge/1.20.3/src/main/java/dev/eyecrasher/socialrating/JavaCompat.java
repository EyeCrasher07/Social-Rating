package dev.eyecrasher.socialrating;
public final class JavaCompat {
    private JavaCompat() {}
    public static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    public static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
