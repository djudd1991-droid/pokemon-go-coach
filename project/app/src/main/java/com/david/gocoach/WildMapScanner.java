package com.david.gocoach;

import android.graphics.Bitmap;
import android.graphics.Rect;

public final class WildMapScanner {
  volatile LearningBank memory;
  final WildMapTracker tracker = new WildMapTracker();

  public void reset() {
    tracker.clear();
  }

  /**
   * Null means this is not confidently a map; an empty list is a scanned map with no candidates.
   */
  public java.util.List<WildMapMatch> wild(Bitmap bitmap, Rect overlay) {
    long started = android.os.SystemClock.elapsedRealtime();
    int w = Math.min(230, bitmap.getWidth()),
        h = Math.max(1, bitmap.getHeight() * w / bitmap.getWidth());
    Bitmap small = Bitmap.createScaledBitmap(bitmap, w, h, false);
    int[] pixels = new int[w * h];
    small.getPixels(pixels, 0, w, 0, 0, w, h);
    if (small != bitmap) small.recycle();
    long scaled = android.os.SystemClock.elapsedRealtime();
    int[] zone =
        overlay == null
            ? null
            : new int[] {
              overlay.left * w / bitmap.getWidth(),
              overlay.top * h / bitmap.getHeight(),
              overlay.right * w / bitmap.getWidth(),
              overlay.bottom * h / bitmap.getHeight()
            };
    if (!WildMapDetector.isMap(pixels, w, h, zone)) {
      tracker.clear();
      WildMapTiming.scan(
          started,
          scaled,
          android.os.SystemClock.elapsedRealtime(),
          android.os.SystemClock.elapsedRealtime(),
          0);
      return null;
    }
    long mapped = android.os.SystemClock.elapsedRealtime();
    java.util.List<WildMapMatch> found = new java.util.ArrayList<>();
    java.util.List<WildMapDetector.Spot> spots =
        WildMapDetector.detect(pixels, w, h, zone, WildMapStartupCache.maxResults());
    long detected = android.os.SystemClock.elapsedRealtime();
    for (WildMapDetector.Spot s :
        tracker.update(spots, w, h, android.os.SystemClock.elapsedRealtime())) {
      WildMapMatch m =
          new WildMapMatch(
              s.stop ? "PokéStop" : "Unknown",
              s.x * 100f / w,
              s.y * 100f / h,
              s.stop,
              s.stop,
              (s.right - s.left + 1) * 100f / w,
              (s.bottom - s.top + 1) * 100f / h);
      if (!s.stop && memory != null) {
        String name =
            memory.matchMap(LearningImages.pixels(bitmap, LearningImages.mapRect(bitmap, m)));
        if (!name.isEmpty())
          m =
              new WildMapMatch(
                  name + "?", m.xPercent, m.yPercent, true, false, m.widthPercent, m.heightPercent);
      }
      found.add(m);
    }
    WildMapTiming.scan(started, scaled, mapped, detected, found.size());
    return found;
  }
}
