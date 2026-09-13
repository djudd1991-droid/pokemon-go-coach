package com.david.gocoach;

import java.io.*;

public class AdviceCheck {
  public static void main(String[] args) throws Exception {
    PvpGrade.load(new FileInputStream(args[0]));
    Reading r = new Reading();
    r.species = "Cinderace";
    r.cp = "2795";
    String verdict = PvpGrade.brief(r);
    if (!verdict.startsWith("Not a good PvP investment") || !verdict.contains("Save your Stardust"))
      throw new AssertionError(verdict);
    r.species = "Unknown";
    if (!PvpGrade.brief(r).contains("haven't identified"))
      throw new AssertionError("unknown species advice");
    r.species = "Cinderace";
    r.cp = "Unknown";
    if (!PvpGrade.brief(r).contains("clear Pokémon details"))
      throw new AssertionError("missing CP");
    r.species = "Lickilicky";
    r.cp = "1400";
    if (!PvpGrade.brief(r).contains("Open Appraise"))
      throw new AssertionError("promising species still needs appraisal");
    System.out.println(verdict);
    System.out.println("Advice checks passed");
  }
}
