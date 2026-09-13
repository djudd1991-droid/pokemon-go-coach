package com.david.gocoach;

import android.content.*;
import java.io.*;
import org.json.*;

public class RecognitionCheck {
  static void check(boolean b, String why) {
    if (!b) throw new AssertionError(why);
  }

  public static void main(String[] args) throws Exception {
    PvpGrade.load(new FileInputStream(args[0]));
    check(PvpGrade.visibleSpeciesName(" Glimmet ").equals("Glimmet"), "broad exact name fallback");
    check(PvpGrade.visibleSpeciesName("Cinder131511").isEmpty(), "nickname not guessed");
    check(PvpGrade.visibleSpeciesName("Tackle").isEmpty(), "move not species");
    MemoryCheck.Prefs prefs = new MemoryCheck.Prefs();
    Context c =
        new Context() {
          public SharedPreferences getSharedPreferences(String n, int m) {
            return prefs;
          }
        };
    RecordStore s = new RecordStore(c);
    Reading r = MemoryCheck.detail();
    r.iv = new int[] {13, 15, 11};
    String id = s.autoSave(r, true).getString("id");
    Reading powered = MemoryCheck.detail();
    powered.cp = "3000";
    powered.maximum = 160;
    powered.iv = new int[] {13, 15, 11};
    JSONObject updated = s.autoSave(powered, true);
    check(updated.getString("id").equals(id) && s.all().length() == 1, "power-up reconnect");
    Reading different = MemoryCheck.detail();
    different.weight = "99.99";
    different.iv = new int[] {13, 15, 11};
    check(
        !s.autoSave(different, true).getString("id").equals(id), "different weight stays separate");
    System.out.println("Species fallback and power-up matching checks passed");
  }
}
