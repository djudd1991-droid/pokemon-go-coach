package com.david.gocoach;

import android.app.*;
import android.graphics.*;
import android.os.SystemClock;
import android.widget.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import java.util.*;

/** Joins exact encounter evidence, durable memory, map teaching and the coaching overlay. */
public final class CoachLearning {
  final CoachService s;
  final java.util.concurrent.ExecutorService worker =
      java.util.concurrent.Executors.newSingleThreadExecutor();
  final EncounterEvidence.Gate gate = new EncounterEvidence.Gate();
  final String run = UUID.randomUUID().toString();
  volatile LearningBank bank;
  volatile EncounterEvidence.Dictionary dictionary =
      new EncounterEvidence.Dictionary(Collections.emptyList());
  Bitmap mapFrame;
  List<WildMapMatch> mapChoices = new ArrayList<>();
  long mapAt, pictureAt;
  int session;
  boolean onMap = true;
  String automaticMapKey = "";
  int savedRecords;
  EncounterEvidence current;
  LearningBank.Entry remembered;
  String status = "";
  boolean portraitBusy;
  String portraitKey = "";

  public CoachLearning(CoachService service) {
    s = service;
    worker.execute(
        () -> {
          LearningBank loaded = LearningStore.get(s);
          List<String> catalog = new ArrayList<>();
          try (java.io.InputStream in = s.getAssets().open("pokemon-names.json")) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) != -1) out.write(b, 0, n);
            org.json.JSONArray a = new org.json.JSONArray(out.toString("UTF-8"));
            for (int i = 0; i < a.length(); i++) catalog.add(a.getString(i));
          } catch (Exception ignored) {
          }
          bank = loaded;
          dictionary = new EncounterEvidence.Dictionary(catalog);
          s.handler.post(
              () -> {
                if (!s.dead) s.wildScanner.memory = loaded;
              });
        });
  }

  String goal() {
    return s.getSharedPreferences("coach", 0).getString("goal", "collection");
  }

  public void noteMap(Bitmap bitmap, List<WildMapMatch> found) {
    gate.reset();
    current = null;
    remembered = null;
    savedRecords = 0;
    status = "";
    onMap = true;
    int width = Math.min(480, bitmap.getWidth());
    Bitmap snapshot =
        Bitmap.createScaledBitmap(
            bitmap, width, Math.max(1, bitmap.getHeight() * width / bitmap.getWidth()), true);
    if (snapshot == bitmap) snapshot = bitmap.copy(Bitmap.Config.ARGB_8888, false);
    if (mapFrame != null) mapFrame.recycle();
    mapFrame = snapshot;
    mapChoices = new ArrayList<>();
    for (WildMapMatch m : found) if (!m.stop) mapChoices.add(m);
    mapAt = SystemClock.elapsedRealtime();
  }

  public String mapAdvice(List<WildMapMatch> found) {
    int unknown = 0, stops = 0;
    Set<String> learned = new LinkedHashSet<>();
    for (WildMapMatch m : found) {
      if (m.stop) stops++;
      else if (m.known) learned.add(m.name.replace("?", ""));
      else unknown++;
    }
    return CoachAdvice.map(goal(), unknown, String.join(", ", learned), stops);
  }

  public void reset() {
    gate.reset();
    current = null;
    remembered = null;
    if (mapFrame != null) {
      mapFrame.recycle();
      mapFrame = null;
    }
    mapChoices.clear();
  }

  boolean stale(int epoch) {
    return s.dead || s.paused || !s.captureVisible || epoch != s.generation;
  }

  public void readEncounter(Text full, Bitmap work, int epoch) {
    Rect band =
        new Rect(
            (int) (work.getWidth() * .10),
            (int) (work.getHeight() * .22),
            (int) (work.getWidth() * .90),
            (int) (work.getHeight() * .41));
    Rect covered = s.panelCaptureBounds(work);
    String raw = full.getText().toLowerCase(Locale.ROOT);
    boolean overlaySeen = covered != null;
    if (overlaySeen && covered != null && Rect.intersects(covered, band)) {
      s.message.setText("Collapse or move GO Coach to uncover the Pokémon name. CP is optional.");
      s.finishFrame(work);
      return;
    }
    final boolean portraitClear =
        !overlaySeen
            || covered == null
            || !Rect.intersects(covered, LearningImages.portraitRect(work));
    StringBuilder header = new StringBuilder();
    for (Text.TextBlock block : full.getTextBlocks())
      for (Text.Line line : block.getLines()) {
        Rect bounds = line.getBoundingBox();
        if (bounds != null && band.contains(bounds.centerX(), bounds.centerY()))
          header.append(line.getText()).append('\n');
      }
    EncounterEvidence direct = dictionary.parse(header.toString());
    if (direct.valid()) {
      try {
        accept(direct, work, portraitClear);
      } finally {
        s.finishFrame(work);
      }
      return;
    }
    Bitmap cut = LearningImages.crop(work, band);
    int width = Math.min(1400, cut.getWidth() * 2);
    Bitmap enlarged =
        Bitmap.createScaledBitmap(
            cut, width, Math.max(1, cut.getHeight() * width / cut.getWidth()), true);
    if (enlarged != cut) cut.recycle();
    try {
      s.recognizer
          .process(InputImage.fromBitmap(enlarged, 0))
          .addOnCompleteListener(
              task -> {
                enlarged.recycle();
                if (stale(epoch)) {
                  s.finishFrame(work);
                  return;
                }
                if (!task.isSuccessful()) {
                  waitForName(work);
                  return;
                }
                EncounterEvidence evidence = dictionary.parse(task.getResult().getText());
                if (evidence.valid()) {
                  try {
                    accept(evidence, work, portraitClear);
                  } finally {
                    s.finishFrame(work);
                  }
                  return;
                }
                if (portraitClear && bank != null) {
                  int[] pixels = LearningImages.pixels(work, LearningImages.portraitRect(work));
                  worker.execute(
                      () -> {
                        String guess = bank.matchEncounter(pixels);
                        s.handler.post(
                            () -> {
                              try {
                                if (!stale(epoch)) {
                                  if (!guess.isEmpty()) {
                                    current = null;
                                    gate.confirm(
                                        new EncounterEvidence("", "Unknown"),
                                        SystemClock.elapsedRealtime());
                                    s.updateMarkers(Collections.emptyList());
                                    s.message.setText(
                                        "Looks like "
                                            + guess
                                            + " · CP "
                                            + evidence.cp
                                            + "\n"
                                            + "Picture-memory match; the name is not confirmed."
                                            + " Hold the name still so I can verify it.");
                                  } else
                                    s.message.setText(
                                        "Hold the Pokémon name still to learn its appearance.");
                                }
                              } finally {
                                s.finishFrame(work);
                              }
                            });
                      });
                  return;
                }
                waitForName(work);
              });
    } catch (Exception e) {
      if (!enlarged.isRecycled()) enlarged.recycle();
      waitForName(work);
    }
  }

  void waitForName(Bitmap work) {
    current = null;
    gate.confirm(new EncounterEvidence("", "Unknown"), SystemClock.elapsedRealtime());
    s.currentReading = null;
    s.stable = false;
    s.updateMarkers(Collections.emptyList());
    s.message.setText("Hold the Pokémon name still for two readings. CP is optional.");
    s.finishFrame(work);
  }

  void accept(EncounterEvidence evidence, Bitmap work, boolean portraitClear) {
    if (onMap) {
      session++;
      onMap = false;
      gate.reset();
    }
    if (current == null || !current.name.equals(evidence.name)) {
      remembered = null;
      savedRecords = 0;
      status = "";
    }
    current = evidence;
    boolean fresh = gate.confirm(evidence, SystemClock.elapsedRealtime());
    s.updateMarkers(Collections.emptyList());
    Reading reading = new Reading();
    reading.species = evidence.name;
    reading.cp = evidence.cp;
    s.currentReading = reading;
    s.stable = gate.stable(evidence);
    if (!s.stable) {
      s.message.setText(
          "Reading " + evidence.name + " · CP " + evidence.cp + " again to confirm its name…");
      return;
    }
    if (bank == null) {
      s.message.setText(evidence.name + " · CP " + evidence.cp + "\nLoading learning memory…");
      gate.reset();
      return;
    }
    long now = SystemClock.elapsedRealtime();
    String key = evidence.name;
    boolean picture =
        portraitClear && !portraitBusy && (!key.equals(portraitKey) || now - pictureAt > 12000);
    int[] pixels = picture ? LearningImages.pixels(work, LearningImages.portraitRect(work)) : null;
    if (fresh || picture || remembered == null) {
      if (picture) {
        portraitBusy = true;
        portraitAt(now, key);
      }
      final String event = run + ":" + session + ":" + key;
      status = "Saving confirmed memory…";
      worker.execute(
          () -> {
            boolean observed =
                bank.observe(evidence.name, evidence.cp, event, System.currentTimeMillis());
            boolean learned =
                observed
                    && pixels != null
                    && bank.teach(evidence.name, LearningBank.ENCOUNTER, pixels);
            LearningBank.Entry entry = bank.entry(evidence.name);
            String problem = bank.problem();
            int count = 0;
            org.json.JSONArray records = s.recordStore.all();
            for (int i = 0; i < records.length(); i++) {
              org.json.JSONObject record = records.optJSONObject(i);
              if (record == null || record.optBoolean("needsReview", false)) continue;
              org.json.JSONObject details = record.optJSONObject("details");
              if (details != null && evidence.name.equals(details.optString("species"))) count++;
            }
            final int collectionCount = count;
            s.handler.post(
                () -> {
                  portraitBusy = false;
                  if (s.dead) return;
                  if (current != null && current.name.equals(evidence.name)) {
                    if (observed) automaticallyTeachSingleMapChoice(evidence.name);
                    remembered = entry;
                    savedRecords = collectionCount;
                    status =
                        !observed
                            ? (problem.isEmpty() ? "Memory could not be saved." : problem)
                            : learned
                                ? "Encounter picture saved."
                                : portraitClear
                                    ? "Name remembered; no additional picture added."
                                    : "Name remembered. Shrink the coach to save a clear picture.";
                    render();
                  }
                });
          });
    }
    render();
  }

  /**
   * Learn only when the preceding map scan had one possible Pokemon. A crowded map remains Unknown
   * rather than guessing which marker was opened.
   */
  void automaticallyTeachSingleMapChoice(String name) {
    if (mapFrame == null || mapChoices.size() != 1 || SystemClock.elapsedRealtime() - mapAt > 90000)
      return;
    String key = name + "|" + mapAt;
    if (key.equals(automaticMapKey)) return;
    automaticMapKey = key;
    final int[] pixels =
        LearningImages.pixels(mapFrame, LearningImages.mapRect(mapFrame, mapChoices.get(0)));
    worker.execute(
        () -> {
          boolean saved = bank.teach(name, LearningBank.MAP, pixels);
          if (saved)
            s.handler.post(
                () -> {
                  if (!s.dead && current != null && current.name.equals(name)) {
                    status = "Map picture learned automatically.";
                    render();
                  }
                });
        });
  }

  void portraitAt(long time, String key) {
    pictureAt = time;
    portraitKey = key;
  }

  void render() {
    if (current == null || s.dead || s.paused) return;
    s.message.setText(
        current.name
            + " · CP "
            + current.cp
            + "\n"
            + status
            + "\n"
            + CoachAdvice.encounter(current.name, goal(), remembered)
            + (savedRecords > 0
                ? "\nYou have "
                    + savedRecords
                    + " saved "
                    + current.name
                    + " collection record(s). Compare their appraisals before spending resources."
                : ""));
  }

  public void teachMap() {
    if (current == null || !gate.stable(current)) {
      Toast.makeText(s, "Open a Pokémon and let its name be confirmed first", Toast.LENGTH_LONG)
          .show();
      return;
    }
    if (mapFrame == null || mapChoices.isEmpty() || SystemClock.elapsedRealtime() - mapAt > 90000) {
      Toast.makeText(
              s,
              "Return to the map, wait for arrows, then open that Pokémon again",
              Toast.LENGTH_LONG)
          .show();
      return;
    }
    if (bank == null) {
      Toast.makeText(s, "Learning memory is still loading", Toast.LENGTH_SHORT).show();
      return;
    }
    final String name = current.name;
    boolean wasPaused = s.paused;
    s.paused = true;
    s.generation++;
    LinearLayout rows = new LinearLayout(s);
    rows.setOrientation(LinearLayout.VERTICAL);
    rows.setPadding(s.dp(12), s.dp(8), s.dp(12), s.dp(8));
    TextView explanation = new TextView(s);
    explanation.setText(
        "Which map picture was "
            + name
            + "? Pick only the Pokémon you opened. This saves a named map example for future"
            + " scans.");
    rows.addView(explanation);
    ScrollView scroll = new ScrollView(s);
    scroll.addView(rows);
    AlertDialog dialog =
        new AlertDialog.Builder(s)
            .setTitle("Teach map: " + name)
            .setView(scroll)
            .setNegativeButton("None / cancel", null)
            .create();
    List<Bitmap> previews = new ArrayList<>();
    for (WildMapMatch candidate : mapChoices) {
      Rect rect = LearningImages.mapRect(mapFrame, candidate);
      Bitmap crop = LearningImages.crop(mapFrame, rect);
      if (crop == mapFrame) crop = mapFrame.copy(Bitmap.Config.ARGB_8888, false);
      previews.add(crop);
      ImageView image = new ImageView(s);
      image.setImageBitmap(crop);
      image.setAdjustViewBounds(true);
      rows.addView(image, new LinearLayout.LayoutParams(-1, s.dp(120)));
      final int[] pixels = LearningImages.pixels(mapFrame, rect);
      Button teach = new Button(s);
      teach.setText("Teach this as " + name);
      rows.addView(teach);
      teach.setOnClickListener(
          v -> {
            dialog.dismiss();
            worker.execute(
                () -> {
                  boolean ok = bank.teach(name, LearningBank.MAP, pixels);
                  LearningBank.Entry entry = bank.entry(name);
                  s.handler.post(
                      () -> {
                        if (!s.dead) {
                          remembered = entry;
                          status =
                              ok
                                  ? "Map picture saved. Similar future views may show "
                                      + name
                                      + "?."
                                  : "Could not add this view. Try a clearer picture, or forget a"
                                      + " wrong label in Learning memory first.";
                          Toast.makeText(s, status, Toast.LENGTH_LONG).show();
                          render();
                        }
                      });
                });
          });
    }
    dialog.setOnDismissListener(
        d -> {
          for (Bitmap preview : previews) preview.recycle();
          s.paused = wasPaused;
        });
    s.showOverlayDialog(dialog);
  }

  public void close() {
    reset();
    worker.shutdown();
  }
}
