package com.david.gocoach;

import java.io.*;
import java.util.*;
import org.json.*;

/** Offline snapshot and stat-product ranks. Fixed L50, IV floor 0; no buddy boost. */
public final class PvpGrade {
  private static JSONObject data;
  private static double[] cpms;
  private static final Map<String, JSONObject> species = new HashMap<>();
  private static final Map<String, String> cache = new HashMap<>();
  private static final Map<String, String> displayNames = new HashMap<>();
  private static final Map<String, String> normalizedNames = new HashMap<>();

  public static synchronized void load(InputStream stream) throws Exception {
    try (InputStream in = stream) {
      if (data != null) return;
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      byte[] b = new byte[8192];
      int n;
      while ((n = in.read(b)) != -1) bytes.write(b, 0, n);
      JSONObject root = new JSONObject(bytes.toString("UTF-8"));
      JSONArray a = root.getJSONArray("cpms");
      double[] c = new double[a.length()];
      for (int i = 0; i < c.length; i++) c[i] = a.getDouble(i);
      JSONArray p = root.getJSONArray("pokemon");
      for (int i = 0; i < p.length(); i++) {
        JSONObject s = p.getJSONObject(i);
        displayNames.put(s.getString("name").toLowerCase(Locale.ROOT), s.getString("name"));
        species.put(key(s.getString("name")), s);
        if (s.has("alias")) species.put(key(s.getString("alias")), s);
      }
      cpms = c;
      data = root;
      indexNames();
    }
  }

  public static synchronized void loadNames(InputStream stream) throws Exception {
    try (InputStream in = stream) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      byte[] b = new byte[8192];
      int n;
      while ((n = in.read(b)) != -1) bytes.write(b, 0, n);
      JSONArray names = new JSONArray(bytes.toString("UTF-8"));
      for (int i = 0; i < names.length(); i++) {
        String name = names.optString(i, "").trim();
        if (!name.isEmpty()) displayNames.put(name.toLowerCase(Locale.ROOT), name);
      }
      indexNames();
    }
  }

  private static void indexNames() {
    for (String name : displayNames.values()) normalizedNames.put(key(name), name);
  }

  public static String visibleSpeciesName(String text) {
    // Exact display names only; no nickname guessing and no CP-only identity.
    String clean = text.trim().toLowerCase(Locale.ROOT);
    return displayNames.getOrDefault(clean, "");
  }

  public static String visibleSpeciesInLine(String text) {
    if (text == null) return "";
    String exact = visibleSpeciesName(text);
    if (!exact.isEmpty()) return exact;
    String clean =
        text.replaceAll("(?i)\\bC\\s*P\\s*[0-9][0-9\\s,.]{0,7}\\b.*", " ")
            .replaceAll("[•·/|]+", " ")
            .trim();
    exact = visibleSpeciesName(clean);
    if (!exact.isEmpty()) return exact;
    String lineKey = key(text);
    String best = "";
    int bestLength = 0;
    for (Map.Entry<String, String> e : normalizedNames.entrySet()) {
      String k = e.getKey();
      if (k.length() >= 4 && lineKey.contains(k) && k.length() > bestLength) {
        best = e.getValue();
        bestLength = k.length();
      }
    }
    return best;
  }

  private static String key(String s) {
    return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
  }

  static int cp(int[] base, int a, int d, int h, int level) {
    double c = cpms[level];
    return Math.max(
        10,
        (int)
            Math.floor(
                (base[0] + a) * Math.sqrt(base[1] + d) * Math.sqrt(base[2] + h) * c * c / 10));
  }

  static double[] optimum(int[] base, int a, int d, int h, int cap) {
    int lo = 0, hi = cpms.length - 1;
    if (cp(base, a, d, h, 0) > cap) return new double[] {0, 0, 0};
    while (lo < hi) {
      int mid = (lo + hi + 1) / 2;
      if (cp(base, a, d, h, mid) <= cap) lo = mid;
      else hi = mid - 1;
    }
    double c = cpms[lo];
    double product =
        (base[0] + a) * (base[1] + d) * c * c * Math.max(10, Math.floor((base[2] + h) * c));
    return new double[] {product, 1 + lo * .5, cp(base, a, d, h, lo)};
  }

  static String ivRank(JSONObject p, int[] iv, int cap) throws Exception {
    String k = p.getString("id") + Arrays.toString(iv) + cap;
    if (cache.containsKey(k)) return cache.get(k);
    JSONArray b = p.getJSONArray("stats");
    int[] base = {b.getInt(0), b.getInt(1), b.getInt(2)};
    double[] mine = optimum(base, iv[0], iv[1], iv[2], cap);
    int rank = 1;
    double best = 0;
    for (int a = 0; a < 16; a++)
      for (int d = 0; d < 16; d++)
        for (int h = 0; h < 16; h++) {
          double v = optimum(base, a, d, h, cap)[0];
          best = Math.max(best, v);
          if (v > mine[0] + 1e-8) rank++;
        }
    String result =
        String.format(
            Locale.US,
            "IV rank #%d / 4096 · %.1f%% of best stat product\nTarget: CP %.0f at level %.1f",
            rank,
            100 * mine[0] / best,
            mine[2],
            mine[1]);
    if (cache.size() > 200) cache.clear();
    cache.put(k, result);
    return result;
  }

  private static boolean valid(int[] iv) {
    if (iv == null || iv.length != 3) return false;
    for (int v : iv) if (v < 0 || v > 15) return false;
    return true;
  }

  public static String brief(Reading r) {
    if (data == null) return "PvP advice unavailable. Don't spend resources based on this reading.";
    JSONObject p = species.get(key(r.species));
    int current;
    try {
      current = Integer.parseInt(r.cp);
    } catch (Exception e) {
      return "I need a clear Pokémon details screen before judging this one.";
    }
    if (p == null) return "I haven't identified this Pokémon well enough to judge it.";
    String[] keys = {"great", "ultra", "master"},
        names = {"Great League", "Ultra League", "Master League"};
    int[] caps = {1500, 2500, 10000};
    double best = -1;
    String league = "";
    boolean missing = false;
    JSONObject leagues = p.optJSONObject("leagues");
    for (int i = 0; i < 3; i++) {
      if (i < 2 && current > caps[i]) continue;
      JSONObject meta = leagues == null ? null : leagues.optJSONObject(keys[i]);
      if (meta == null) {
        missing = true;
        continue;
      }
      double score = meta.optDouble("score", -1);
      if (score < 0) {
        missing = true;
        continue;
      }
      if (score > best) {
        best = score;
        league = names[i];
      }
    }
    if (best < 0)
      return "I don't have enough PvP information to judge this one. Hold off on powering it up.";
    if (best < 70 && missing)
      return "No strong PvP use found yet. Some league information is missing, so hold off on"
          + " powering it up.";
    String advice;
    if (best < 70) advice = "Not a good PvP investment.\nSave your Stardust.";
    else if (best < 85)
      advice =
          "Could work in "
              + league
              + ", but isn't a priority.\nHold off on powering it up until we compare your team.";
    else
      advice = "Promising for " + league + ".\nKeep it for a closer look; don't power it up yet.";
    if (best < 70 && current > 2500)
      advice += "\nToo much CP for Great and Ultra; a weak Master League choice.";
    else if (best >= 70 && !valid(r.iv))
      advice += "\nOpen Appraise once so I can judge your individual Pokémon.";
    else if (best >= 70 && (r.fast.isEmpty() || r.charged.isEmpty()))
      advice += "\nShow its moves before we choose an upgrade.";
    return advice + "\nBased on PvP rankings saved " + data.optString("date") + ".";
  }

  public static String report(Reading r) {
    if (data == null) return "PvP data could not load.";
    JSONObject p = species.get(key(r.species));
    if (p == null) return "Species/form not identified in the PvP data. No grade calculated.";
    StringBuilder s =
        new StringBuilder(
            r.species
                + " · CP "
                + r.cp
                + "\nVerify species/form and appraisal before investing.\n");
    if (valid(r.iv))
      s.append(
          String.format(
              Locale.US,
              "\nOverall IV: %.1f%% (not a PvP grade)\n",
              100.0 * (r.iv[0] + r.iv[1] + r.iv[2]) / 45));
    String[] leagues = {"great", "ultra", "master"},
        names = {"Great League", "Ultra League", "Master League"};
    int[] caps = {1500, 2500, 10000};
    int current = -1;
    try {
      current = Integer.parseInt(r.cp);
    } catch (Exception ignored) {
    }
    try {
      for (int i = 0; i < 3; i++) {
        s.append("\n").append(names[i]).append("\n");
        if (i < 2 && current > caps[i]) {
          s.append("Not eligible: already above ")
              .append(caps[i])
              .append(" CP. CP cannot be lowered.\n");
          continue;
        }
        s.append(
            current < 0
                ? "Current CP unknown.\n"
                : i == 2
                    ? "No CP limit.\n"
                    : "Within CP limit; cup/form restrictions may apply.\n");
        if (valid(r.iv)) s.append(ivRank(p, r.iv, caps[i])).append("\n");
        else s.append("IV grade: open Appraise once to read the bars.\n");
        JSONObject meta = p.getJSONObject("leagues").optJSONObject(leagues[i]);
        if (meta == null) {
          s.append("Species not ranked in this snapshot.\n");
          continue;
        }
        int rank = meta.getInt("rank"), total = meta.getInt("total");
        s.append("Species rank #")
            .append(rank)
            .append(" / ")
            .append(total)
            .append(" · score ")
            .append(meta.getDouble("score"))
            .append("/100\n");
        if (rank > total / 2)
          s.append("Lower half of this ranking; not a priority investment from this snapshot.\n");
        s.append("Suggested moves: ");
        JSONArray moves = meta.getJSONArray("moves");
        for (int m = 0; m < moves.length(); m++) {
          if (m > 0) s.append(" + ");
          s.append(moves.getString(m));
        }
        s.append("\n");
      }
    } catch (Exception e) {
      s.append("Grade unavailable for this entry.\n");
    }
    s.append("\nYour fast move: ")
        .append(r.fast.isEmpty() ? "Not read" : r.fast)
        .append("\nYour charged moves: ")
        .append(r.charged.isEmpty() ? "Not read" : String.join(" + ", r.charged));
    s.append(
            "\n\n"
                + "IV ranks compare this species/form's 4096 IV spreads by attack × defense ×"
                + " floored HP at the highest level under the cap. Assumptions: level 50 maximum,"
                + " IV floor 0, no Best Buddy boost; ties share rank. No evolution grades yet.\n\n"
                + "Species ranks and moves: PvPoke open-league overall snapshot downloaded ")
        .append(data.optString("date"))
        .append(
            ". Offline snapshot, not a live ranking or a simulation of your moves/team. Elite/event"
                + " moves may require special availability. Item ownership is not checked.\n"
                + "https://pvpoke.com/rankings/");
    return s.toString();
  }
}
