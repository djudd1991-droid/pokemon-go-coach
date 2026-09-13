package com.david.gocoach;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Process-wide configuration. Startup publishes a private HashMap that is never mutated afterward.
 */
public final class WildMapStartupCache {
  public static final String MAX_RESULTS = "max_results";
  private static volatile HashMap<String, Object> cache;
  private static String loadProblem = "";

  private WildMapStartupCache() {}

  @FunctionalInterface
  interface Source {
    InputStream open() throws IOException;
  }

  /** Only WildMapInitialization calls this in production, before activities or services start. */
  static synchronized void initialize(Source source) {
    if (cache != null) return;
    HashMap<String, Object> loaded = new HashMap<>();
    loaded.put(MAX_RESULTS, 16);
    try (InputStream in = source.open();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[4096];
      int count;
      while ((count = in.read(buffer)) != -1) bytes.write(buffer, 0, count);
      JSONObject json = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
      Iterator<String> keys = json.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        Object value = json.get(key);
        // The tuning file is flat. Store only immutable scalar values, never live JSON objects.
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
          loaded.put(key, value);
        }
      }
      loaded.put(MAX_RESULTS, Math.max(1, Math.min(24, json.optInt(MAX_RESULTS, 16))));
    } catch (IOException | JSONException e) {
      loaded.clear();
      loaded.put(MAX_RESULTS, 16);
      loadProblem = "Wild Map configuration unavailable; using startup defaults.";
    }
    // Volatile publication makes the fully populated HashMap visible to every scanning worker.
    // Failure also publishes defaults once; gameplay never retries asset access.
    cache = loaded;
  }

  private static HashMap<String, Object> ready() {
    HashMap<String, Object> snapshot = cache;
    if (snapshot == null)
      throw new IllegalStateException("Wild Map startup initialization was skipped");
    return snapshot;
  }

  public static int maxResults() {
    return (Integer) ready().get(MAX_RESULTS);
  }

  public static String text(String key, String fallback) {
    Object value = ready().get(key);
    return value instanceof String ? (String) value : fallback;
  }

  public static int integer(String key, int fallback) {
    Object value = ready().get(key);
    return value instanceof Number ? ((Number) value).intValue() : fallback;
  }

  static String problem() {
    ready();
    return loadProblem;
  }
}
