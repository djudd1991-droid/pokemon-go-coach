package com.david.gocoach;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Versioned, atomic on-device learning store; independent of collection records. */
public final class LearningBank {
  public static final int MAP = 1, ENCOUNTER = 2;
  private static final int MAGIC = 0x474f4c31, MAX_SAMPLES = 512;

  public static final class Entry {
    public final String name, cp;
    public final int visits, mapViews, portraits;
    public final long seen;

    Entry(Record r) {
      name = r.name;
      cp = r.cp;
      visits = r.visits;
      seen = r.seen;
      int m = 0, e = 0;
      for (Sample s : r.samples)
        if (s.kind == MAP) m++;
        else e++;
      mapViews = m;
      portraits = e;
    }
  }

  static float[] describe(int[] pixels, int kind) {
    return kind == MAP ? WildMapAppearance.describe(pixels) : Appearance.describe(pixels);
  }

  static final class Sample {
    int kind;
    int[] pixels;
    float[] vector;

    Sample(int k, int[] p) {
      kind = k;
      pixels = p.clone();
      vector = describe(p, k);
    }
  }

  static final class Record {
    String name, cp = "Unknown", event = "";
    int visits;
    long seen;
    List<Sample> samples = new ArrayList<>();

    Record(String n) {
      name = n;
    }
  }

  private final File file;
  private final Map<String, Record> records = new TreeMap<>();
  private String problem = "";

  public LearningBank(File file) {
    this.file = file;
    load();
  }

  public synchronized String problem() {
    return problem;
  }

  public synchronized List<Entry> entries() {
    List<Entry> out = new ArrayList<>();
    for (Record r : records.values()) out.add(new Entry(r));
    return out;
  }

  public synchronized Entry entry(String name) {
    Record r = records.get(name);
    return r == null ? null : new Entry(r);
  }

  public synchronized boolean observe(String name, String cp, String event, long now) {
    if (name == null
        || name.isEmpty()
        || name.equals("Unknown")
        || cp == null
        || (!cp.equals("Unknown") && !cp.matches("[0-9]{1,5}"))
        || !problem.isEmpty()) return false;
    Record r = records.get(name);
    boolean fresh = r == null;
    if (fresh) {
      r = new Record(name);
      records.put(name, r);
    }
    if (r.event.equals(event)) return true;
    int visits = r.visits;
    String oldCp = r.cp, oldEvent = r.event;
    long oldSeen = r.seen;
    r.visits++;
    r.cp = cp;
    r.event = event;
    r.seen = now;
    if (save()) return true;
    r.visits = visits;
    r.cp = oldCp;
    r.event = oldEvent;
    r.seen = oldSeen;
    if (fresh) records.remove(name);
    return false;
  }

  public synchronized boolean teach(String name, int kind, int[] pixels) {
    Record r = records.get(name);
    float[] v = describe(pixels, kind);
    if (r == null || v == null || (kind != MAP && kind != ENCOUNTER) || !problem.isEmpty())
      return false;
    int total = 0, own = 0;
    for (Record q : records.values())
      for (Sample sample : q.samples) {
        total++;
        if (q == r && sample.kind == kind) {
          own++;
          if (Appearance.similarity(v, sample.vector) > .998) return true;
        }
        if (kind == MAP
            && sample.kind == MAP
            && q != r
            && Appearance.similarity(v, sample.vector) > .995) return false;
      }
    if (total >= MAX_SAMPLES || own >= 8) return false;
    Sample added = new Sample(kind, pixels);
    r.samples.add(added);
    if (save()) return true;
    r.samples.remove(added);
    return false;
  }

  /** Predictions are tentative and are never fed back as confirmed training data. */
  public synchronized String matchMap(int[] pixels) {
    return match(pixels, MAP);
  }

  public synchronized String matchEncounter(int[] pixels) {
    return match(pixels, ENCOUNTER);
  }

  private String match(int[] pixels, int kind) {
    float[] v = describe(pixels, kind);
    if (v == null) return "";
    String best = "";
    double score = 0, runnerUp = 0;
    for (Record r : records.values()) {
      double s = 0;
      for (Sample x : r.samples)
        if (x.kind == kind) s = Math.max(s, Appearance.similarity(v, x.vector));
      if (s > score) {
        runnerUp = score;
        score = s;
        best = r.name;
      } else runnerUp = Math.max(runnerUp, s);
    }
    return score >= .985 && score - runnerUp >= .04 ? best : "";
  }

  public synchronized boolean forgetMap(String name) {
    Record r = records.get(name);
    if (r == null || !problem.isEmpty()) return false;
    List<Sample> old = new ArrayList<>(r.samples);
    r.samples.removeIf(s -> s.kind == MAP);
    if (save()) return true;
    r.samples = old;
    return false;
  }

  /**
   * Removes only Wild Map examples. Encounter visits and encounter portraits are intentionally
   * retained.
   */
  public synchronized boolean forgetAllMap() {
    if (!problem.isEmpty()) return false;
    Map<String, List<Sample>> old = new TreeMap<>();
    boolean changed = false;
    for (Map.Entry<String, Record> entry : records.entrySet()) {
      Record r = entry.getValue();
      old.put(entry.getKey(), new ArrayList<>(r.samples));
      int before = r.samples.size();
      r.samples.removeIf(s -> s.kind == MAP);
      changed |= before != r.samples.size();
    }
    if (!changed) return true;
    if (save()) return true;
    for (Map.Entry<String, List<Sample>> entry : old.entrySet())
      records.get(entry.getKey()).samples = entry.getValue();
    return false;
  }

  private void load() {
    if (!file.exists()) return;
    try (DataInputStream in =
        new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
      if (in.readInt() != MAGIC) throw new IOException("Unsupported learning-memory version");
      int count = in.readInt();
      if (count < 0 || count > 3000) throw new IOException("Invalid memory record count");
      int total = 0;
      for (int i = 0; i < count; i++) {
        Record r = new Record(in.readUTF());
        r.cp = in.readUTF();
        r.event = in.readUTF();
        r.visits = in.readInt();
        r.seen = in.readLong();
        int n = in.readInt();
        if (n < 0 || n > 16 || total + n > MAX_SAMPLES)
          throw new IOException("Invalid sample count");
        total += n;
        for (int j = 0; j < n; j++) {
          int kind = in.readInt();
          if (kind != MAP && kind != ENCOUNTER) throw new IOException("Invalid view type");
          int[] p = new int[Appearance.SIZE * Appearance.SIZE];
          for (int k = 0; k < p.length; k++) p[k] = in.readInt();
          r.samples.add(new Sample(kind, p));
        }
        records.put(r.name, r);
      }
    } catch (IOException | RuntimeException e) {
      records.clear();
      problem = "Learning memory could not be read; the existing file was preserved.";
    }
  }

  private boolean save() {
    File parent = file.getParentFile(), temp = new File(parent, file.getName() + ".tmp");
    try {
      if (!parent.exists() && !parent.mkdirs()) return false;
      try (FileOutputStream bytes = new FileOutputStream(temp);
          DataOutputStream out = new DataOutputStream(new BufferedOutputStream(bytes))) {
        out.writeInt(MAGIC);
        out.writeInt(records.size());
        for (Record r : records.values()) {
          out.writeUTF(r.name);
          out.writeUTF(r.cp);
          out.writeUTF(r.event);
          out.writeInt(r.visits);
          out.writeLong(r.seen);
          out.writeInt(r.samples.size());
          for (Sample s : r.samples) {
            out.writeInt(s.kind);
            for (int p : s.pixels) out.writeInt(p);
          }
        }
        out.flush();
        bytes.getFD().sync();
      }
      try {
        Files.move(
            temp.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
      return true;
    } catch (IOException | RuntimeException e) {
      temp.delete();
      return false;
    }
  }
}
