package com.david.gocoach;

/** Small color/spatial descriptor. Separate map and encounter banks prevent domain confusion. */
public final class Appearance {
  public static final int SIZE = 48;

  public static float[] describe(int[] pixels) {
    if (pixels == null || pixels.length != SIZE * SIZE) return null;
    float[] v = new float[8 * 8 * 4];
    int count = 0;
    for (int y = 0; y < SIZE; y++)
      for (int x = 0; x < SIZE; x++) {
        int c = pixels[y * SIZE + x], r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        // Suppress grass, cyan spawn auras and bright road paint.
        if (g > r + 12 && g > b && b > g * .64
            || g > r + 45 && b > r + 45
            || r > 235 && g > 170 && b < 170) continue;
        if (Math.max(r, Math.max(g, b)) < 28) continue;
        int k = ((y / 6) * 8 + x / 6) * 4;
        v[k] += r / 255f;
        v[k + 1] += g / 255f;
        v[k + 2] += b / 255f;
        v[k + 3] += 1;
        count++;
      }
    if (count < 60 || count > pixels.length * .95) return null;
    double length = 0;
    for (int i = 0; i < v.length; i++) length += v[i] * v[i];
    if (length < 1e-8) return null;
    float scale = (float) (1 / Math.sqrt(length));
    for (int i = 0; i < v.length; i++) v[i] *= scale;
    return v;
  }

  public static double similarity(float[] a, float[] b) {
    if (a == null || b == null || a.length != b.length) return 0;
    double d = 0;
    for (int i = 0; i < a.length; i++) d += a[i] * b[i];
    return d;
  }
}
