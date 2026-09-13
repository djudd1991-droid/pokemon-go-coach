package com.david.gocoach;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Runs the production detector on phone captures. Coordinates are detector pixels. */
public class ScreenshotCheck {
  public static void main(String[] args) throws Exception {
    String[] expected = System.getProperty("expected", "").split(",");
    int index = 0;
    for (String path : args) {
      BufferedImage source = ImageIO.read(new File(path));
      int w = 230, h = source.getHeight() * w / source.getWidth();
      int[] pixels = new int[w * h];
      for (int y = 0; y < h; y++)
        for (int x = 0; x < w; x++)
          pixels[y * w + x] = source.getRGB(x * source.getWidth() / w, y * source.getHeight() / h);
      int[] overlay = {3, 40, 151, 246};
      long start = System.nanoTime();
      boolean map = WildMapDetector.isMap(pixels, w, h, overlay);
      java.util.List<WildMapDetector.Spot> spots =
          map
              ? WildMapDetector.detect(pixels, w, h, overlay, 16)
              : java.util.Collections.emptyList();
      if (index < expected.length && !expected[index].isEmpty()) {
        int count = Integer.parseInt(expected[index]);
        if (map != (count >= 0) || (map && spots.size() != count))
          throw new AssertionError(path + ": unexpected map classification/candidate count");
        if (spots.stream().anyMatch(s -> s.stop))
          throw new AssertionError(path + ": a spawn was labeled PokéStop");
      }
      index++;
      System.out.println(new File(path).getName() + " map=" + map);
      if (map && Boolean.getBoolean("boxes"))
        for (WildMapDetector.Box b : WildMapForeground.find(pixels, w, h, overlay))
          System.out.printf("  RAW %d,%d,%d,%d n=%d%n", b.l, b.t, b.r, b.b, b.n);
      if (map)
        for (WildMapDetector.Spot s : WildMapDetector.detect(pixels, w, h, overlay, 16))
          System.out.printf(
              "  %s (%d,%d) box=%d,%d,%d,%d%n",
              s.stop ? "STOP" : "UNKNOWN", s.x, s.y, s.left, s.top, s.right, s.bottom);
      System.out.printf("  elapsed=%.1fms%n", (System.nanoTime() - start) / 1e6);
      if (map) {
        for (int i = 0; i < 10; i++) WildMapDetector.detect(pixels, w, h, overlay, 16);
        long[] times = new long[30];
        for (int i = 0; i < times.length; i++) {
          long t = System.nanoTime();
          WildMapDetector.detect(pixels, w, h, overlay, 16);
          times[i] = System.nanoTime() - t;
        }
        java.util.Arrays.sort(times);
        System.out.printf(
            "  warm detector median=%.2fms p95=%.2fms (desktop, excludes OCR/capture)%n",
            times[15] / 1e6, times[28] / 1e6);
      }
    }
  }
}
