package com.david.gocoach;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;

public class GradeCheck {
  static void check(boolean b, String why) {
    if (!b) throw new AssertionError(why);
  }

  public static void main(String[] args) throws Exception {
    PvpGrade.load(new FileInputStream(args[0]));
    Reading r = new Reading();
    r.species = "Cinderace";
    r.cp = "2795";
    r.iv = new int[] {13, 15, 11};
    r.fast = "Tackle";
    r.charged.add("Focus Blast");
    String s = PvpGrade.report(r);
    check(
        s.contains("Not eligible: already above 1500")
            && s.contains("Not eligible: already above 2500"),
        "overcap rejected");
    check(
        s.contains("86.7%")
            && s.contains("Species rank #235")
            && s.contains("Your fast move: Tackle"),
        "screenshot report");
    r.cp = "1400";
    check(PvpGrade.report(r).contains("Great League\nWithin CP limit"), "cap eligible");
    r.iv = null;
    check(PvpGrade.report(r).contains("IV grade: open Appraise"), "missing IV no grade");
    r.species = "Not a species";
    check(PvpGrade.report(r).startsWith("Species/form not identified"), "unknown rejected");
    JSONObject p =
        new JSONObject(Files.readString(Path.of(args[0]))).getJSONArray("pokemon").getJSONObject(0);
    check(
        PvpGrade.ivRank(p, new int[] {15, 15, 15}, 10000).startsWith("IV rank #1 /"),
        "hundo uncapped optimal");
    double[] best = PvpGrade.optimum(new int[] {238, 163, 190}, 13, 15, 11, 1500);
    check(
        best[2] <= 1500
            && PvpGrade.cp(new int[] {238, 163, 190}, 13, 15, 11, (int) ((best[1] - 1) * 2) + 1)
                > 1500,
        "highest legal level");
    Map<String, String> m = new HashMap<>();
    m.put("tackle", "F\tTackle");
    m.put("focusblast", "C\tFocus Blast");
    m.put("volttackle", "C\tVolt Tackle");
    Reading x = new Reading();
    x.seeMove("O Focus Blast 140", m);
    x.seeMove("O Tackle 5", m);
    check(x.fast.equals("Tackle") && x.charged.equals(List.of("Focus Blast")), "icon OCR");
    x.seeMove("Fast: Tackle", m);
    x.seeMove("Charged: Volt Tackle", m);
    check(x.charged.size() == 1, "overlay labels rejected");
    System.out.println(s);
    System.out.println(
        "PASS: eligibility, missing data, hundo rank, legal level boundary, screenshot report, icon"
            + " noise");
  }
}
