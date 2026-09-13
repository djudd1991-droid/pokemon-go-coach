package com.david.gocoach;

import java.util.*;
import java.util.regex.*;

/** Conservative local extraction; no nickname-as-species fallback. */
public final class Reading {
  public String species = "Unknown", cp = "Unknown", health = "Unknown";
  public int current = -1, maximum = -1;
  public int[] iv;
  public String weight = "", height = "", fast = "";
  public java.util.List<String> charged = new java.util.ArrayList<>();
  public boolean savedIv = false, savedMoves = false;

  public static Reading parse(String raw) {
    Reading r = new Reading();
    Matcher m =
        Pattern.compile(
                "\\bThis\\s+([\\p{L}0-9 .’'♀♂:\\-]+?)\\s+was\\s+caught\\b",
                Pattern.CASE_INSENSITIVE)
            .matcher(raw.replace('\n', ' '));
    if (m.find()) r.species = m.group(1).trim();
    m =
        Pattern.compile("\\bC[ \\t]*P[ \\t]*([0-9][0-9 \\t,.]{0,7})", Pattern.CASE_INSENSITIVE)
            .matcher(raw);
    if (m.find()) {
      String digits = m.group(1).replaceAll("[^0-9]", "");
      if (digits.length() >= 1 && digits.length() <= 5) r.cp = digits;
    }
    m =
        Pattern.compile("\\b([0-9]{1,4})\\s*/\\s*([0-9]{1,4})\\s*HP\\b", Pattern.CASE_INSENSITIVE)
            .matcher(raw);
    if (m.find()) {
      int a = Integer.parseInt(m.group(1)), b = Integer.parseInt(m.group(2));
      if (b > 0 && a <= b) {
        r.current = a;
        r.maximum = b;
        r.health = a + "/" + b;
      }
    }
    m = Pattern.compile("(?i)\\b([0-9]+(?:[.,][0-9]+)?)\\s*kg\\b").matcher(raw);
    if (m.find()) r.weight = m.group(1).replace(',', '.');
    m = Pattern.compile("(?i)\\b([0-9]+(?:[.,][0-9]+)?)\\s*m\\b").matcher(raw);
    if (m.find()) r.height = m.group(1).replace(',', '.');
    return r;
  }

  public String summary() {
    String s = species + " · CP " + cp + "\nHP " + health;
    if (current >= 0)
      s += " · " + (current == 0 ? "Fainted" : current == maximum ? "Fully healed" : "Injured");
    s +=
        "\nAppraisal: "
            + (iv == null
                ? "Not read"
                : iv[0] + " / " + iv[1] + " / " + iv[2] + " (Atk / Def / HP)");
    if (savedIv && iv != null) s += " — saved";
    s += "\nFast: " + (fast.isEmpty() ? "Not read" : fast);
    s += "\nCharged: " + (charged.isEmpty() ? "Not read" : String.join(" / ", charged));
    if (savedMoves) s += " (includes saved moves)";
    s += "\nShiny / background: Unknown";
    if (current == 0) s += "\nCheck your Revives before healing.";
    else if (current >= 0 && current < maximum) s += "\nCheck your Potions before healing.";
    return s;
  }

  public String fingerprint() {
    if (species.equals("Unknown")
        || cp.equals("Unknown")
        || maximum < 1
        || weight.isEmpty()
        || height.isEmpty()) return "";
    return species.toLowerCase(java.util.Locale.ROOT)
        + "|"
        + cp
        + "|"
        + maximum
        + "|"
        + weight
        + "|"
        + height;
  }

  public void seeMove(String text, java.util.Map<String, String> catalog) {
    String key =
        text.replaceAll("[0-9]", "").replaceAll("[^A-Za-z]", "").toLowerCase(java.util.Locale.ROOT);
    String value = catalog.get(key);
    // OCR can turn a type icon into O, I, etc. Only permit up to two leading
    // characters; do not fuzzy-match arbitrary words or the coach's own labels.
    if (value == null) {
      int longest = 0;
      for (java.util.Map.Entry<String, String> e : catalog.entrySet()) {
        int prefix = key.length() - e.getKey().length();
        if (prefix > 0
            && prefix <= 2
            && key.endsWith(e.getKey())
            && e.getKey().length() > longest) {
          value = e.getValue();
          longest = e.getKey().length();
        }
      }
    }
    if (value == null) return;
    if (value.startsWith("F\t")) fast = value.substring(2);
    else if (value.startsWith("C\t") && !charged.contains(value.substring(2)) && charged.size() < 2)
      charged.add(value.substring(2));
  }

  public interface Pixels {
    int width();

    int height();

    int get(int x, int y);
  }

  public static final class Anchor {
    public int left, bottom;

    public Anchor(int x, int y) {
      left = x;
      bottom = y;
    }
  }

  private static boolean filled(int rgb) {
    int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
    return (r >= 215 && g >= 115 && g <= 205 && b >= 55 && b <= 160 && r - g >= 30)
        || (r >= 190
            && g >= 90
            && g <= 165
            && b >= 90
            && b <= 165
            && r - g >= 45
            && Math.abs(g - b) < 30);
  }

  private static boolean track(int rgb) {
    int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
    return r >= 185 && r <= 237 && Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) <= 12;
  }

  /** Uses OCR label anchors, colored fill plus gray remainder, and matching three-bar geometry. */
  public static int[] appraisal(Pixels p, Anchor[] anchors) {
    if (anchors == null || anchors.length != 3) return null;
    int[][] bars = new int[3][];
    for (int i = 0; i < 3; i++) {
      if (anchors[i] == null) return null;
      bars[i] = bar(p, anchors[i]);
      if (bars[i] == null) return null;
    }
    int width = bars[0][1] - bars[0][0];
    for (int i = 1; i < 3; i++)
      if (Math.abs((bars[i][1] - bars[i][0]) - width) > p.width() * .015
          || Math.abs(bars[i][0] - bars[0][0]) > p.width() * .015) return null;
    int dy1 = bars[1][3] - bars[0][3], dy2 = bars[2][3] - bars[1][3];
    if (dy1 < 4 || dy2 < 4 || Math.abs(dy1 - dy2) > p.height() * .015) return null;
    return new int[] {bars[0][2], bars[1][2], bars[2][2]};
  }

  private static int[] bar(Pixels p, Anchor a) {
    List<int[]> rows = new ArrayList<>();
    int gap = Math.max(2, (int) (p.width() * .007));
    int x0 = Math.max(0, a.left - (int) (p.width() * .015)),
        x1 = Math.min(p.width(), a.left + (int) (p.width() * .40));
    int y1 = Math.min(p.height(), a.bottom + (int) (p.height() * .025));
    for (int y = Math.max(0, a.bottom); y < y1; y++) {
      int start = -1, end = -1, lastFill = -1, missing = 0;
      boolean sawGray = false, invalid = false;
      for (int x = x0; x < x1; x++) {
        int c = p.get(x, y);
        boolean f = filled(c), t = track(c);
        if (f || t) {
          if (start < 0) start = x;
          end = x;
          missing = 0;
          if (f) {
            lastFill = x;
            if (sawGray) invalid = true;
          } else sawGray = true;
        } else if (start >= 0 && ++missing > gap) break;
      }
      int length = end - start + 1;
      if (invalid || length < p.width() * .20 || length > p.width() * .39 || start < 0) continue;
      double value = lastFill < 0 ? 0 : 15.0 * (lastFill - start + 1) / length;
      int rounded = (int) Math.round(value);
      if (Math.abs(value - rounded) > .25) continue;
      rows.add(new int[] {start, end, rounded, y});
    }
    if (rows.size() < 3) return null;
    int[] votes = new int[16];
    for (int[] r : rows) votes[r[2]]++;
    int winner = 0;
    for (int i = 1; i < 16; i++) if (votes[i] > votes[winner]) winner = i;
    if (votes[winner] < 3 || votes[winner] < rows.size() * .7) return null;
    final int chosen = winner;
    rows.removeIf(r -> r[2] != chosen);
    rows.sort(Comparator.comparingInt(r -> r[1] - r[0]));
    return rows.get(rows.size() / 2);
  }
}
