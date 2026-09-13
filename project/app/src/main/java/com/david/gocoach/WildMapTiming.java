package com.david.gocoach;

import android.util.Log;

/** Wild Map-only timing log. Search Logcat for GO-Coach-WildMap when a scan is slow. */
final class WildMapTiming {
  private static final String TAG = "GO-Coach-WildMap";

  private WildMapTiming() {}

  static void scan(long started, long scaled, long mapped, long detected, int candidates) {
    long now = android.os.SystemClock.elapsedRealtime();
    Log.i(
        TAG,
        "scan total="
            + (now - started)
            + "ms scale="
            + (scaled - started)
            + "ms map="
            + (mapped - scaled)
            + "ms detect="
            + (detected - mapped)
            + "ms learn="
            + (now - detected)
            + "ms candidates="
            + candidates);
  }
}
