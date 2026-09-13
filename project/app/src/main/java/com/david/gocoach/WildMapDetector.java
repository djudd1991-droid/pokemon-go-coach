package com.david.gocoach;

import java.util.*;

/** Compact map foreground candidates, with separate HUD and PokéStop shape checks. */
public final class WildMapDetector {
  public static final class Spot {
    public final int x, y;
    public final boolean stop;
    final int left, top, right, bottom, area;
    final double anchorX, anchorY, radius;
    final boolean rocket;

    Spot(Box b, boolean stop, double ax, double ay, double radius, boolean rocket) {
      left = b.l;
      top = b.t;
      right = b.r;
      bottom = b.b;
      area = b.n;
      x = (b.l + b.r) / 2;
      y = (b.t + b.b) / 2;
      this.stop = stop;
      anchorX = ax;
      anchorY = ay;
      this.radius = radius;
      this.rocket = rocket;
    }
  }

  static final class Box {
    int l, t, r, b, n, parts = 1;

    Box(int l, int t, int r, int b, int n) {
      this.l = l;
      this.t = t;
      this.r = r;
      this.b = b;
      this.n = n;
    }

    int width() {
      return r - l + 1;
    }

    int height() {
      return b - t + 1;
    }
  }

  static int red(int c) {
    return (c >> 16) & 255;
  }

  static int green(int c) {
    return (c >> 8) & 255;
  }

  static int blue(int c) {
    return c & 255;
  }

  static boolean excluded(int x, int y, int w, int h, int[] overlay) {
    if (y < h * .40 || y >= h * .88 || x < w * .015 || x > w * .985) return true;
    if (y > h * .79 && x > w * .85 || isBuddyOrProfilePortrait(x, y, w, h)) return true;
    return overlay != null
        && x >= overlay[0]
        && x <= overlay[2]
        && y >= overlay[1]
        && y <= overlay[3];
  }

  /**
   * The round trainer/buddy portrait occupies this lower-left map HUD box. Keep this exact
   * exclusion separate from general map UI.
   */
  static boolean isBuddyOrProfilePortrait(int x, int y, int w, int h) {
    return x >= w * .02 && x <= w * .245 && y >= h * .805 && y <= h * .985;
  }

  public static boolean isMap(int[] pixels, int w, int h, int[] overlay) {
    if (w < 8 || h < 32 || pixels.length != w * h) return false;
    int total = 0, green = 0, dayRoad = 0, nightBlue = 0, nightRoad = 0;
    for (int y = h / 4; y < h * 85 / 100; y += 2)
      for (int x = 0; x < w; x += 2) {
        if (overlay != null
            && x >= overlay[0]
            && x <= overlay[2]
            && y >= overlay[1]
            && y <= overlay[3]) continue;
        int c = pixels[y * w + x], r = red(c), g = green(c), b = blue(c);
        total++;
        if (g > r + 12 && g > b + 4 && g > 90) green++;
        if (r > 205 && g > 150 && g > b + 35 && r > g + 35) dayRoad++;
        // Pokémon GO's night map is predominantly muted blue, with red/pink route
        // lines.  Keep this signature paired so ordinary blue app screens do not
        // enter Wild Map mode.
        if (b > r + 14 && b > g + 8 && b > 75) nightBlue++;
        // Map roads are muted rose, unlike the vivid red Poké Ball and catch UI.
        if (r > 105 && r < 210 && g > 45 && b > 60 && r > g + 15 && r > b + 8) nightRoad++;
      }
    boolean daylight = green > total * .18 && dayRoad > total * .002;
    boolean night = nightBlue > total * .45 && nightRoad > total * .001;
    return total > 100 && (daylight || night) && hasMapMenu(pixels, w, h);
  }

  /** A compact red/white menu at the bottom distinguishes the map from a catch ball. */
  static boolean hasMapMenu(int[] pixels, int w, int h) {
    int red = 0, white = 0, total = 0;
    for (int y = (int) (h * .905); y < (int) (h * .965); y++)
      for (int x = (int) (w * .43); x < (int) (w * .57); x++) {
        int c = pixels[y * w + x], r = red(c), g = green(c), b = blue(c);
        total++;
        if (r > 170 && r > g + 65 && r > b + 40) red++;
        if (r > 185 && g > 185 && b > 185) white++;
      }
    return total > 0 && red > total * .045 && white > total * .12;
  }

  static boolean cyan(int c) {
    int r = red(c), g = green(c), b = blue(c);
    return r < 105 && g > 135 && b > 175 && g > b * .72 && b > g - 42;
  }

  static List<Box> components(boolean[] mask, int w, int h, boolean dilate) {
    boolean[] expanded = mask.clone(), seen = new boolean[w * h];
    if (dilate)
      for (int y = 0; y < h; y++)
        for (int x = 0; x < w; x++) {
          int i = y * w + x;
          if (!mask[i]) continue;
          if (x > 0) expanded[i - 1] = true;
          if (x + 1 < w) expanded[i + 1] = true;
          if (y > 0) expanded[i - w] = true;
          if (y + 1 < h) expanded[i + w] = true;
        }
    List<Box> boxes = new ArrayList<>();
    int[] queue = new int[w * h];
    for (int seed = 0; seed < mask.length; seed++) {
      if (!expanded[seed] || seen[seed]) continue;
      int head = 0, tail = 0, n = 0, l = w, t = h, r = 0, b = 0;
      queue[tail++] = seed;
      seen[seed] = true;
      while (head < tail) {
        int i = queue[head++], x = i % w, y = i / w;
        if (mask[i]) {
          n++;
          l = Math.min(l, x);
          r = Math.max(r, x);
          t = Math.min(t, y);
          b = Math.max(b, y);
        }
        if (x > 0 && !seen[i - 1] && expanded[i - 1]) {
          seen[i - 1] = true;
          queue[tail++] = i - 1;
        }
        if (x + 1 < w && !seen[i + 1] && expanded[i + 1]) {
          seen[i + 1] = true;
          queue[tail++] = i + 1;
        }
        if (y > 0 && !seen[i - w] && expanded[i - w]) {
          seen[i - w] = true;
          queue[tail++] = i - w;
        }
        if (y + 1 < h && !seen[i + w] && expanded[i + w]) {
          seen[i + w] = true;
          queue[tail++] = i + w;
        }
      }
      if (n >= 8) boxes.add(new Box(l, t, r, b, n));
    }
    return boxes;
  }

  public static List<Spot> detect(int[] p, int w, int h, int[] overlay, int limit) {
    if (w < 8 || h < 32 || p.length != w * h || limit < 1) return new ArrayList<>();
    List<Box> boxes = WildMapForeground.find(p, w, h, overlay);
    boxes.removeIf(
        b -> {
          double x = (b.l + b.r) / 2.0, y = (b.t + b.b) / 2.0;
          return x > w * .455 && x < w * .545 && y > h * .615 && y < h * .725;
        });
    // Join small color fragments belonging to a single dark or multi-colored body.
    for (int i = 0; i < boxes.size(); i++) {
      Box a = boxes.get(i);
      if (a.n > 80) continue;
      for (int j = i + 1; j < boxes.size(); j++) {
        Box b = boxes.get(j);
        if (b.n > 80) continue;
        int dx = Math.max(0, Math.max(a.l, b.l) - Math.min(a.r, b.r));
        int dy = Math.max(0, Math.max(a.t, b.t) - Math.min(a.b, b.b));
        if (dx <= w * .05
            && dy <= w * .035
            && Math.max(a.r, b.r) - Math.min(a.l, b.l) < w * .16
            && Math.max(a.b, b.b) - Math.min(a.t, b.t) < w * .16) {
          int parts = a.parts + b.parts;
          a =
              new Box(
                  Math.min(a.l, b.l),
                  Math.min(a.t, b.t),
                  Math.max(a.r, b.r),
                  Math.max(a.b, b.b),
                  a.n + b.n);
          a.parts = parts;
          boxes.set(i, a);
          boxes.remove(j--);
        }
      }
    }
    List<Spot> candidates = new ArrayList<>();
    for (Box b : boxes) {
      double cx = (b.l + b.r) / 2.0, cy = (b.t + b.b) / 2.0;
      double density = b.n / (double) (b.width() * b.height());
      if (b.n < 18
          || b.width() < 4
          || b.height() < 5
          || b.width() > w * .20
          || b.height() > w * .23
          || b.width() > b.height() * 2.2
          || density < (b.parts >= 2 ? .08 : .20)
          || cy > h * .80) continue;
      if (cx > w * .455 && cx < w * .545 && cy > h * .615 && cy < h * .725) continue;
      boolean stop = isStop(p, w, b);
      candidates.add(new Spot(b, stop, cx, cy, Math.max(b.width(), b.height()) * .7, false));
    }
    candidates.sort((a, b) -> Integer.compare(b.area, a.area));
    return new ArrayList<>(candidates.subList(0, Math.min(limit, candidates.size())));
  }

  /** A cyan head and narrow stem are required; a blue body alone is not a stop. */
  private static boolean isStop(int[] pixels, int w, Box b) {
    if (b.height() < b.width() * 1.3 || b.height() < 12) return false;
    int cyan = 0, head = 0, stem = 0, outsideStem = 0;
    int mid = (b.l + b.r) / 2;
    for (int y = b.t; y <= b.b; y++)
      for (int x = b.l; x <= b.r; x++)
        if (cyan(pixels[y * w + x])) {
          cyan++;
          if (y < b.t + b.height() * .45) head++;
          else if (Math.abs(x - mid) <= Math.max(1, b.width() * .20)) stem++;
          else outsideStem++;
        }
    return cyan > b.n * .65 && head >= 8 && stem >= 5 && outsideStem <= stem * .4;
  }
}
