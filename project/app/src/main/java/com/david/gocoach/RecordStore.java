package com.david.gocoach;

import android.content.Context;
import java.util.*;
import org.json.*;

final class RecordStore {
  final android.content.SharedPreferences prefs;

  RecordStore(Context c) {
    prefs = c.getSharedPreferences("data", 0);
  }

  JSONArray all() {
    try {
      return new JSONArray(prefs.getString("collection", "[]"));
    } catch (Exception e) {
      return new JSONArray();
    }
  }

  JSONObject get(String id) {
    JSONArray a = all();
    for (int i = 0; i < a.length(); i++) {
      JSONObject o = a.optJSONObject(i);
      if (o != null && o.optString("id").equals(id)) return o;
    }
    return null;
  }

  List<JSONObject> matches(String fp) {
    List<JSONObject> out = new ArrayList<>();
    if (fp.isEmpty()) return out;
    JSONArray a = all();
    for (int i = 0; i < a.length(); i++) {
      JSONObject o = a.optJSONObject(i);
      if (o != null && o.optString("fingerprint").equals(fp)) out.add(o);
    }
    return out;
  }

  static JSONObject encode(Reading r) {
    JSONObject o = new JSONObject();
    try {
      o.put("species", r.species);
      o.put("cp", r.cp);
      o.put("maximum", r.maximum);
      o.put("weight", r.weight);
      o.put("height", r.height);
      o.put("fast", r.fast);
      o.put("charged", new JSONArray(r.charged));
      if (r.iv != null) o.put("iv", new JSONArray(Arrays.asList(r.iv[0], r.iv[1], r.iv[2])));
    } catch (Exception ignored) {
    }
    return o;
  }

  static boolean sameIdentity(Reading a, Reading b) {
    if (a.species.equals("Unknown")
        || b.species.equals("Unknown")
        || !a.species.equalsIgnoreCase(b.species)) return false;
    if (!a.cp.equals("Unknown") && !b.cp.equals("Unknown") && a.maximum > 0 && b.maximum > 0)
      return a.cp.equals(b.cp) && a.maximum == b.maximum;
    if (a.maximum > 0
        && b.maximum > 0
        && !a.weight.isEmpty()
        && !b.weight.isEmpty()
        && !a.height.isEmpty()
        && !b.height.isEmpty())
      return a.maximum == b.maximum && a.weight.equals(b.weight) && a.height.equals(b.height);
    return false;
  }

  static int quality(Reading r, JSONObject o) {
    int q = 0;
    if (!r.species.equals("Unknown")) q += 4;
    if (!r.cp.equals("Unknown")) q += 4;
    if (r.maximum > 0) q += 4;
    if (!r.weight.isEmpty()) q += 2;
    if (!r.height.isEmpty()) q += 2;
    if (r.iv != null) q += 8;
    if (!r.fast.isEmpty()) q += 3;
    q += Math.min(6, r.charged.size() * 3);
    if (!o.optBoolean("needsReview")) q += 2;
    return q;
  }

  static void mergeInto(JSONObject target, JSONObject source) {
    Reading base = Reading.parse(target.optString("stats")),
        extra = Reading.parse(source.optString("stats"));
    JSONObject d = target.optJSONObject("details");
    if (d != null) {
      base.species = d.optString("species", base.species);
      base.cp = d.optString("cp", base.cp);
      base.maximum = d.optInt("maximum", base.maximum);
      base.weight = d.optString("weight", base.weight);
      base.height = d.optString("height", base.height);
    }
    recall(base, target);
    recall(extra, source);
    if (base.species.equals("Unknown")) base.species = extra.species;
    if (base.cp.equals("Unknown")) base.cp = extra.cp;
    if (base.maximum < 1) base.maximum = extra.maximum;
    if (base.weight.isEmpty()) base.weight = extra.weight;
    if (base.height.isEmpty()) base.height = extra.height;
    if (base.iv == null) base.iv = extra.iv;
    if (base.fast.isEmpty()) base.fast = extra.fast;
    for (String move : extra.charged)
      if (!base.charged.contains(move) && base.charged.size() < 2) base.charged.add(move);
    try {
      target.put("details", encode(base));
      target.put("fingerprint", base.fingerprint());
      target.put("stats", base.summary());
      target.put("time", Math.max(target.optLong("time"), source.optLong("time")));
      target.put(
          "protected",
          target.optBoolean("protected", true) || source.optBoolean("protected", true));
      target.put(
          "needsReview", target.optBoolean("needsReview") && !source.optBoolean("needsReview"));
    } catch (Exception ignored) {
    }
  }

  static boolean conflict(Reading r, JSONObject record) {
    JSONObject d = record.optJSONObject("details");
    JSONArray iv = d == null ? null : d.optJSONArray("iv");
    return r.iv != null
        && iv != null
        && iv.length() == 3
        && (r.iv[0] != iv.optInt(0) || r.iv[1] != iv.optInt(1) || r.iv[2] != iv.optInt(2));
  }

  static void recall(Reading r, JSONObject record) {
    JSONObject d = record.optJSONObject("details");
    if (d == null) {
      d = new JSONObject();
      java.util.regex.Matcher m =
          java.util.regex.Pattern.compile(
                  "Appraisal: ([0-9]{1,2})\\s*/\\s*([0-9]{1,2})\\s*/\\s*([0-9]{1,2})")
              .matcher(record.optString("stats"));
      if (m.find())
        try {
          JSONArray a = new JSONArray();
          for (int i = 1; i <= 3; i++) {
            int v = Integer.parseInt(m.group(i));
            if (v > 15) return;
            a.put(v);
          }
          d.put("iv", a);
        } catch (Exception ignored) {
        }
    }
    JSONArray iv = d.optJSONArray("iv");
    if (r.iv == null && iv != null && iv.length() == 3) {
      r.iv = new int[] {iv.optInt(0), iv.optInt(1), iv.optInt(2)};
      r.savedIv = true;
    }
    String fast = d.optString("fast");
    if (r.fast.isEmpty() && !fast.isEmpty()) {
      r.fast = fast;
      r.savedMoves = true;
    }
    JSONArray charged = d.optJSONArray("charged");
    if (charged != null) {
      if (r.charged.isEmpty()) {
        for (int i = 0; i < charged.length(); i++) r.charged.add(charged.optString(i));
        r.savedMoves |= charged.length() > 0;
      } else if (r.charged.size() == 1
          && charged.length() == 2
          && !r.charged.get(0).equals(charged.optString(1))) {
        r.charged.add(charged.optString(1));
        r.savedMoves = true;
      }
    }
  }

  /**
   * Creates only with a full stable fingerprint, or updates one exact match. Returns null for
   * ambiguous/conflicting readings; never overwrites those.
   */
  JSONObject autoSave(Reading r, boolean tentative) {
    String fp = r.fingerprint();
    if (fp.isEmpty()) return null;
    List<JSONObject> found = matches(fp);
    if (found.isEmpty() && r.iv != null) {
      // Power-ups change CP/HP. Reconnect only with matching live appraisal,
      // species, weight and height, and exactly one saved candidate.
      JSONArray saved = all();
      for (int i = 0; i < saved.length(); i++) {
        JSONObject o = saved.optJSONObject(i);
        JSONObject d = o == null ? null : o.optJSONObject("details");
        if (d == null) continue;
        JSONArray iv = d.optJSONArray("iv");
        if (iv == null || iv.length() != 3) continue;
        if (r.species.equals(d.optString("species"))
            && r.weight.equals(d.optString("weight"))
            && r.height.equals(d.optString("height"))
            && r.iv[0] == iv.optInt(0)
            && r.iv[1] == iv.optInt(1)
            && r.iv[2] == iv.optInt(2)) found.add(o);
      }
    }
    if (found.size() > 1) return null;
    if (found.size() == 1) {
      JSONObject o = found.get(0);
      if (conflict(r, o)) return null;
      return update(o.optString("id"), r, false) ? get(o.optString("id")) : null;
    }
    JSONArray saved = all();
    JSONObject close = null;
    for (int i = 0; i < saved.length(); i++) {
      JSONObject o = saved.optJSONObject(i);
      if (o == null) continue;
      Reading old = Reading.parse(o.optString("stats"));
      JSONObject d = o.optJSONObject("details");
      if (d != null) {
        old.species = d.optString("species", old.species);
        old.cp = d.optString("cp", old.cp);
        old.maximum = d.optInt("maximum", old.maximum);
        old.weight = d.optString("weight", old.weight);
        old.height = d.optString("height", old.height);
      }
      recall(old, o);
      if (sameIdentity(r, old)) {
        if (close != null) return null;
        close = o;
      }
    }
    if (close != null) {
      if (conflict(r, close)) return null;
      return update(close.optString("id"), r, false) ? get(close.optString("id")) : null;
    }
    try {
      JSONObject o = new JSONObject();
      o.put("id", UUID.randomUUID().toString());
      o.put("name", r.species);
      o.put("details", encode(r));
      o.put("fingerprint", fp);
      o.put("stats", r.summary());
      o.put("traits", "");
      o.put("protected", true);
      o.put("autoAdded", true);
      o.put("needsReview", tentative);
      o.put("time", System.currentTimeMillis());
      JSONArray all = all();
      all.put(o);
      return prefs.edit().putString("collection", all.toString()).commit() ? o : null;
    } catch (Exception e) {
      return null;
    }
  }

  boolean update(String id, Reading r, boolean explicit) {
    JSONArray a = all();
    for (int i = 0; i < a.length(); i++) {
      JSONObject o = a.optJSONObject(i);
      if (o == null || !o.optString("id").equals(id)) continue;
      if (!explicit && conflict(r, o)) return false;
      try {
        recall(r, o);
        JSONObject old = o.optJSONObject("details");
        if (old != null
            && old.toString().equals(encode(r).toString())
            && o.optString("fingerprint").equals(r.fingerprint())) return true;
        if (old != null && !old.toString().equals(encode(r).toString()))
          o.put("previousDetails", old);
        o.put("details", encode(r));
        o.put("fingerprint", r.fingerprint());
        o.put("stats", r.summary());
        o.put("time", System.currentTimeMillis());
        return prefs.edit().putString("collection", a.toString()).commit();
      } catch (Exception e) {
        return false;
      }
    }
    return false;
  }

  int cleanDuplicates() {
    JSONArray a = all();
    int removed = 0;
    for (int i = 0; i < a.length(); i++) {
      JSONObject one = a.optJSONObject(i);
      if (one == null) continue;
      Reading r1 = Reading.parse(one.optString("stats"));
      JSONObject d1 = one.optJSONObject("details");
      if (d1 != null) {
        r1.species = d1.optString("species", r1.species);
        r1.cp = d1.optString("cp", r1.cp);
        r1.maximum = d1.optInt("maximum", r1.maximum);
        r1.weight = d1.optString("weight", r1.weight);
        r1.height = d1.optString("height", r1.height);
      }
      recall(r1, one);
      for (int j = a.length() - 1; j > i; j--) {
        JSONObject two = a.optJSONObject(j);
        if (two == null) continue;
        Reading r2 = Reading.parse(two.optString("stats"));
        JSONObject d2 = two.optJSONObject("details");
        if (d2 != null) {
          r2.species = d2.optString("species", r2.species);
          r2.cp = d2.optString("cp", r2.cp);
          r2.maximum = d2.optInt("maximum", r2.maximum);
          r2.weight = d2.optString("weight", r2.weight);
          r2.height = d2.optString("height", r2.height);
        }
        recall(r2, two);
        if (!sameIdentity(r1, r2) || conflict(r1, two) || conflict(r2, one)) continue;
        JSONObject keep = quality(r2, two) > quality(r1, one) ? two : one,
            drop = keep == one ? two : one;
        mergeInto(keep, drop);
        try {
          if (keep == two) {
            a.put(i, two);
            one = two;
            r1 = r2;
          }
          a.remove(j);
          removed++;
        } catch (Exception ignored) {
        }
      }
    }
    if (removed > 0) prefs.edit().putString("collection", a.toString()).apply();
    return removed;
  }
}
