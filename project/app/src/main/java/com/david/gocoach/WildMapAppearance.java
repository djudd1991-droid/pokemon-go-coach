package com.david.gocoach;

/** Night map ground must not occupy almost every cell of a learned spawn crop. */
public final class WildMapAppearance {
  private WildMapAppearance() {}

  public static float[] describe(int[] pixels) {
    if (pixels == null || pixels.length != Appearance.SIZE * Appearance.SIZE) return null;
    int[] foreground = pixels.clone();
    for (int i = 0; i < foreground.length; i++) {
      int c = foreground[i], r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
      // Muted blue ground, distinct from the strongly cyan stop/creature colors.
      if (r > 30 && r > g * .45 && b > g * 1.15 && b > r * 1.45) foreground[i] = 0;
    }
    return Appearance.describe(foreground);
  }
}
