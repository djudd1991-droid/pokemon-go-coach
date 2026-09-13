package com.david.gocoach;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class LearningCheck {
  static void require(boolean b, String text) {
    if (!b) throw new AssertionError(text);
  }

  static int[] picture(int color) {
    int[] p = new int[48 * 48];
    Arrays.fill(p, 0xff65bd91);
    for (int y = 12; y < 38; y++)
      for (int x = 13; x < 35; x++) p[y * 48 + x] = ((x + y) % 5 == 0) ? 0xff483b3d : color;
    return p;
  }

  public static void main(String[] args) throws Exception {
    require(
        Reading.parse("Pidgey / cp137\n363").cp.equals("137"),
        "CP must not absorb the next line's ball count");
    require(Reading.parse("CP 1 3 7\n363").cp.equals("137"), "Spaced CP must remain supported");
    List<String> names = Arrays.asList("Pidgey", "Noibat", "Mew", "Mewtwo");
    EncounterEvidence e = EncounterEvidence.parse("o>\n& Pj\n} S Pidgey / cp137", names);
    require(
        e.valid() && e.name.equals("Pidgey") && e.cp.equals("137"), "Pidgey screenshot OCR header");
    require(
        !EncounterEvidence.parse("Pidgey Noibat CP137", names).valid(),
        "Ambiguous names must not train");
    require(
        EncounterEvidence.parse("Pidgey", names).valid(),
        "A clear name can confirm encounter learning without CP");
    require(
        EncounterEvidence.parse("Mewtwo CP302", names).name.equals("Mewtwo"),
        "No substring-name confusion");
    EncounterEvidence.Dictionary dictionary = new EncounterEvidence.Dictionary(names);
    require(
        dictionary.parse("Mewtwo").name.equals("Mewtwo") && !dictionary.parse("Mewtwo Mew").valid(),
        "Cached dictionary must retain exact, unambiguous matching");
    int[] nightPicture = picture(0xffc8a250);
    for (int i = 0; i < nightPicture.length; i++)
      if (nightPicture[i] == 0xff65bd91) nightPicture[i] = 0xff385b92;
    require(
        WildMapAppearance.describe(nightPicture) != null,
        "Night background must not prevent learning a clear foreground");
    EncounterEvidence.Gate nameOnly = new EncounterEvidence.Gate();
    require(
        !nameOnly.confirm(dictionary.parse("Mewtwo CP302"), 1000)
            && nameOnly.confirm(dictionary.parse("Mewtwo"), 2000),
        "Missing CP must not restart name confirmation");
    EncounterEvidence.Gate gate = new EncounterEvidence.Gate();
    require(!gate.confirm(e, 1000), "First read is not evidence enough");
    require(gate.confirm(e, 2000), "Second read confirms");
    require(!gate.confirm(e, 3000), "Same screen must not repeatedly count");
    gate.reset();
    require(!gate.confirm(e, 4000) && gate.confirm(e, 5000), "A new encounter session can confirm");
    File dir = Files.createTempDirectory("go-learning-test").toFile(),
        file = new File(dir, "memory.bin");
    LearningBank bank = new LearningBank(file);
    int[] pidgey = picture(0xffc8a250), noibat = picture(0xff854baa);
    require(bank.matchMap(pidgey).isEmpty(), "Empty memory must not invent a label");
    require(
        !bank.teach("Pidgey", LearningBank.MAP, pidgey),
        "Unknown/unconfirmed species must not train");
    require(bank.observe("Pidgey", "137", "session-1", 1000), "Save a confirmed encounter");
    require(
        bank.observe("Noibat", "Unknown", "name-only", 1050),
        "Save a confirmed name when CP is offscreen");
    require(
        bank.observe("Pidgey", "137", "session-1", 1100) && bank.entry("Pidgey").visits == 1,
        "No repeated-frame count inflation");
    require(bank.teach("Pidgey", LearningBank.ENCOUNTER, pidgey), "Save encounter picture");
    require(
        bank.matchMap(pidgey).isEmpty(),
        "Encounter pictures must not silently become map templates");
    require(bank.matchEncounter(pidgey).equals("Pidgey"), "Encounter picture memory is usable");
    require(bank.teach("Pidgey", LearningBank.MAP, pidgey), "Explicit map teaching");
    require(bank.matchMap(pidgey).equals("Pidgey"), "Use saved map example");
    require(bank.observe("Noibat", "302", "session-2", 2000), "Second species");
    require(
        !bank.teach("Noibat", LearningBank.MAP, pidgey),
        "Conflicting identical map labels must be rejected");
    require(bank.teach("Noibat", LearningBank.MAP, noibat), "Different confirmed example");
    bank = new LearningBank(file);
    require(
        bank.entry("Pidgey").visits == 1
            && bank.entry("Pidgey").mapViews == 1
            && bank.entry("Pidgey").portraits == 1,
        "Counts and both image domains survive reopening");
    require(
        bank.matchMap(pidgey).equals("Pidgey") && bank.matchMap(noibat).equals("Noibat"),
        "Predictions survive reopening");
    require(bank.forgetMap("Pidgey"), "Correct wrong map memory");
    bank = new LearningBank(file);
    require(
        bank.entry("Pidgey").mapViews == 0
            && bank.entry("Pidgey").portraits == 1
            && bank.entry("Pidgey").visits == 1,
        "Correction preserves encounter history and portraits");
    require(
        bank.teach("Noibat", LearningBank.MAP, noibat),
        "Restore map sample for Wild Map delete-all test");
    require(bank.forgetAllMap(), "Delete all Wild Map learning");
    bank = new LearningBank(file);
    require(
        bank.entry("Noibat").mapViews == 0
            && bank.entry("Pidgey").portraits == 1
            && bank.entry("Pidgey").visits == 1,
        "Delete all Wild Map learning preserves encounters and portraits");
    byte[] corrupt = {1, 2, 3, 4, 5};
    Files.write(file.toPath(), corrupt);
    bank = new LearningBank(file);
    require(
        !bank.problem().isEmpty() && !bank.observe("Pidgey", "137", "x", 3000),
        "Corrupt memory must be read-only");
    require(
        Arrays.equals(Files.readAllBytes(file.toPath()), corrupt),
        "Preserve unreadable memory instead of overwriting it");
    System.out.println(
        "PASS: Pidgey OCR parsing, confirmation, duplicate suppression, map/encounter separation,"
            + " persistence, matching, conflicting-label rejection, correction and corrupt-file"
            + " preservation");
  }
}
