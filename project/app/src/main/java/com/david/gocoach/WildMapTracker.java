package com.david.gocoach;

import java.util.*;

/** Confirm on consecutive frames, and never carry a disappeared position forward. */
public final class WildMapTracker {
  private List<WildMapDetector.Spot> previous = new ArrayList<>();
  private long time = 0;
  private int width, height;

  public synchronized void clear() {
    previous.clear();
    time = 0;
  }

  public synchronized List<WildMapDetector.Spot> update(
      List<WildMapDetector.Spot> current, int w, int h, long now) {
    if (w != width || h != height || now - time > 3000 || now < time) previous.clear();
    List<WildMapDetector.Spot> stable = new ArrayList<>();
    boolean[] used = new boolean[previous.size()];
    for (WildMapDetector.Spot c : current) {
      int nearest = -1;
      double distance = Double.MAX_VALUE;
      for (int i = 0; i < previous.size(); i++) {
        WildMapDetector.Spot p = previous.get(i);
        if (used[i] || p.stop != c.stop) continue;
        double dx = c.x - p.x, dy = c.y - p.y, d = dx * dx + dy * dy;
        double threshold =
            Math.max(w * .035, Math.min(c.right - c.left + 1, p.right - p.left + 1) * .45);
        if (d <= threshold * threshold && d < distance) {
          nearest = i;
          distance = d;
        }
      }
      if (nearest >= 0) {
        used[nearest] = true;
        stable.add(c);
      }
    }
    previous = new ArrayList<>(current);
    width = w;
    height = h;
    time = now;
    return stable;
  }
}
