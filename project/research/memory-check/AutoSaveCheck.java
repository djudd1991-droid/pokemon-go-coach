package com.david.gocoach;

import android.content.*;
import org.json.*;

public class AutoSaveCheck {
  static void check(boolean b, String why) {
    if (!b) throw new AssertionError(why);
  }

  public static void main(String[] args) throws Exception {
    MemoryCheck.Prefs prefs = new MemoryCheck.Prefs();
    Context c =
        new Context() {
          public SharedPreferences getSharedPreferences(String n, int m) {
            return prefs;
          }
        };
    RecordStore s = new RecordStore(c);
    Reading r = MemoryCheck.detail();
    JSONObject first = s.autoSave(r, true);
    check(first != null && s.all().length() == 1, "auto creates");
    check(first.getBoolean("protected") && first.getBoolean("needsReview"), "uncertain protected");
    r.iv = new int[] {13, 15, 11};
    r.fast = "Tackle";
    r.charged.add("Focus Blast");
    s.autoSave(r, false);
    check(s.all().length() == 1, "repeat updates no duplicate");
    Reading reopened = MemoryCheck.detail();
    s.autoSave(reopened, false);
    check(reopened.iv[0] == 13 && reopened.fast.equals("Tackle"), "retains hidden stats");
    Reading partial = MemoryCheck.detail();
    partial.height = "";
    check(s.autoSave(partial, false) == null, "partial not saved");
    Reading conflict = MemoryCheck.detail();
    conflict.iv = new int[] {15, 15, 15};
    check(s.autoSave(conflict, false) == null, "conflict blocked");
    JSONObject copy = new JSONObject(first.toString()).put("id", "twin");
    prefs.putString(
        "collection", new JSONArray().put(s.all().getJSONObject(0)).put(copy).toString());
    check(
        s.autoSave(MemoryCheck.detail(), false) == null && s.all().length() == 2,
        "ambiguous untouched");
    System.out.println(
        "Automatic create, deduplication, recall, incomplete readings, conflicts and ambiguity"
            + " passed");
  }
}
