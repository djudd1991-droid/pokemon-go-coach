package com.david.gocoach;

import android.content.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;

public class MemoryCheck {
  static class Prefs implements SharedPreferences, SharedPreferences.Editor {
    Map<String, String> map = new HashMap<>();

    public String getString(String k, String d) {
      return map.getOrDefault(k, d);
    }

    public Editor edit() {
      return this;
    }

    public Editor putString(String k, String v) {
      map.put(k, v);
      return this;
    }

    public boolean commit() {
      return true;
    }
  }

  static void check(boolean c, String s) {
    if (!c) throw new AssertionError(s);
  }

  static Reading detail() {
    Reading r = Reading.parse("CP2795\n154/154 HP\n21.35kg\n1.08m");
    r.species = "Cinderace";
    return r;
  }

  public static void main(String[] args) throws Exception {
    Prefs prefs = new Prefs();
    Context ctx =
        new Context() {
          public SharedPreferences getSharedPreferences(String n, int m) {
            return prefs;
          }
        };
    RecordStore store = new RecordStore(ctx);
    Reading live = detail();
    check(!live.fingerprint().isEmpty(), "identity units parse");
    Reading other = detail();
    other.weight = "22.35";
    check(!live.fingerprint().equals(other.fingerprint()), "same CP distinct weight");
    Reading incomplete = detail();
    incomplete.height = "";
    check(incomplete.fingerprint().isEmpty(), "no partial identity matching");
    JSONObject legacy =
        new JSONObject()
            .put("id", "one")
            .put("name", "Cinderace")
            .put("protected", true)
            .put("stats", "Appraisal: 13 / 15 / 11 (Atk / Def / HP)");
    prefs.putString("collection", new JSONArray().put(legacy).toString());
    check(store.update("one", live, true), "legacy link");
    check(Arrays.equals(live.iv, new int[] {13, 15, 11}) && live.savedIv, "legacy recall");
    check(store.matches(live.fingerprint()).size() == 1, "persistent match");
    Map<String, String> moves = new HashMap<>();
    for (String line : Files.readAllLines(Path.of(args[0]))) {
      String[] p = line.split("\t", 2);
      moves.put(p[1].replaceAll("[^A-Za-z]", "").toLowerCase(Locale.ROOT), line);
    }
    Reading withMoves = detail();
    withMoves.seeMove("Tackle 5", moves);
    withMoves.seeMove("Focus Blast 140", moves);
    withMoves.seeMove("NEW ATTACK 10000", moves);
    check(
        withMoves.fast.equals("Tackle") && withMoves.charged.equals(List.of("Focus Blast")),
        "moves recognized and noise rejected");
    check(store.update("one", withMoves, false), "move update");
    Reading reopened = detail();
    RecordStore.recall(reopened, new RecordStore(ctx).get("one"));
    check(
        reopened.fast.equals("Tackle")
            && reopened.charged.equals(List.of("Focus Blast"))
            && reopened.iv[1] == 15,
        "reopen recalls IV and moves");
    Reading conflict = detail();
    conflict.iv = new int[] {15, 15, 15};
    check(!store.update("one", conflict, false), "conflicting IVs blocked");
    check(
        store.get("one").getJSONObject("details").getJSONArray("iv").getInt(0) == 13,
        "conflict preserved original");
    JSONObject duplicate = new JSONObject(store.get("one").toString()).put("id", "two");
    prefs.putString("collection", new JSONArray().put(store.get("one")).put(duplicate).toString());
    check(store.matches(live.fingerprint()).size() == 2, "ambiguous records exposed");
    check(store.get("one").getBoolean("protected"), "protection preserved");
    Reading twoMoves = detail();
    twoMoves.fast = "Tackle";
    twoMoves.charged.addAll(List.of("Focus Blast", "Flamethrower"));
    store.update("one", twoMoves, true);
    Reading partial = detail();
    partial.fast = "Tackle";
    partial.charged.add("Focus Blast");
    store.update("one", partial, false);
    check(partial.charged.size() == 2 && partial.savedMoves, "offscreen second move retained");
    System.out.println(
        "Memory, legacy migration, move parsing, conflict, and duplicate checks passed");
  }
}
