package com.david.gocoach;

import java.util.*;

/** Groups a spawn's different colors before classification, in the map play area only. */
final class WildMapForeground {
  private WildMapForeground() {}

  static List<WildMapDetector.Box> find(int[] pixels, int w, int h, int[] overlay) {
    boolean[] mask = new boolean[w * h];
    for (int y = 0; y < h; y++)
      for (int x = 0; x < w; x++) {
        if (WildMapDetector.excluded(x, y, w, h, overlay)) continue;
        int c = pixels[y * w + x], r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        boolean yellow =
            r > 100 && g > 100 && b < g * .80 && r > g * .80 && !(r > 230 && g > 150 && r > g + 35);
        boolean pink = r > g + 20 && b > g + 20 && r > 125 && b > r - 8;
        boolean vividBlue = b > 125 && g > 85 && r < g * .40;
        boolean white =
            r > 155
                && g > 155
                && b > 145
                && Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) < 45;
        if (white) {
          int neighbors = 0;
          for (int yy = Math.max(0, y - 1); yy <= Math.min(h - 1, y + 1); yy++)
            for (int xx = Math.max(0, x - 1); xx <= Math.min(w - 1, x + 1); xx++) {
              int a = pixels[yy * w + xx];
              if (((a >> 16) & 255) > 155 && ((a >> 8) & 255) > 155 && (a & 255) > 145) neighbors++;
            }
          white = neighbors >= 6; // Thin interaction rings are not foreground bodies.
        }
        // Dull blue ground, muted roads and buildings are deliberately absent.
        mask[y * w + x] = yellow || pink || vividBlue || white;
      }
    return WildMapDetector.components(mask, w, h, true);
  }
}
