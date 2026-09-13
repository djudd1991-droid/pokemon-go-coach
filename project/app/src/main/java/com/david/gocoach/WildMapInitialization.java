package com.david.gocoach;

import android.app.Application;
import android.util.Log;

/** Android invokes this once per app process, before creating an activity or capture service. */
public final class WildMapInitialization extends Application {
  @Override
  public void onCreate() {
    super.onCreate();
    WildMapStartupCache.initialize(() -> getAssets().open("wild-map/wild-map.json"));
    if (!WildMapStartupCache.problem().isEmpty()) {
      Log.w("GO-Coach-WildMap", WildMapStartupCache.problem());
    }
  }
}
