package com.david.gocoach;

import java.util.*;
import java.util.regex.*;

/** Exact dictionary names in a focused encounter-header crop, never fuzzy species guesses. */
public final class EncounterEvidence {
  public final String name, cp;

  public EncounterEvidence(String name, String cp) {
    this.name = name;
    this.cp = cp;
  }

  /** A clear name is enough to learn an encounter appearance; CP is optional. */
  public boolean valid() {
    return !name.isEmpty();
  }

  public static EncounterEvidence parse(String text, Collection<String> names) {
    return new Dictionary(names).parse(text);
  }

  static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}0-9♀♂]+");

  static String normalize(String text) {
    return SEPARATORS.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
  }

  /** Normalize the species catalog once, instead of running thousands of regexes each frame. */
  public static final class Dictionary {
    final Map<String, String> normalizedNames = new LinkedHashMap<>();

    public Dictionary(Collection<String> names) {
      for (String name : names) {
        String key = normalize(name);
        if (!key.isEmpty()) normalizedNames.put(name, " " + key + " ");
      }
    }

    public EncounterEvidence parse(String text) {
      String normalized = " " + normalize(text) + " ", found = "";
      for (Map.Entry<String, String> entry : normalizedNames.entrySet()) {
        String name = entry.getKey();
        if (normalized.contains(entry.getValue())) {
          if (!found.isEmpty() && !found.equals(name)) return new EncounterEvidence("", "Unknown");
          found = name;
        }
      }
      return new EncounterEvidence(found, Reading.parse(text).cp);
    }
  }

  public static final class Gate {
    String pending = "", emitted = "";
    int count;
    long last;

    public void reset() {
      pending = "";
      emitted = "";
      count = 0;
      last = 0;
    }

    public boolean confirm(EncounterEvidence e, long now) {
      if (!e.valid()) {
        pending = "";
        count = 0;
        return false;
      }
      // OCR can miss or change CP while the name remains clear.  Do not make that
      // prevent name-only encounter learning from reaching the two-frame gate.
      String key = e.name;
      if (key.equals(pending) && now - last < 5000) count++;
      else {
        pending = key;
        count = 1;
      }
      last = now;
      if (count >= 2 && !key.equals(emitted)) {
        emitted = key;
        return true;
      }
      return false;
    }

    public boolean stable(EncounterEvidence e) {
      return e.valid() && e.name.equals(pending) && count >= 2;
    }
  }
}
