package com.david.gocoach;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import org.json.*;

public class CoachService extends Service {
  final java.util.concurrent.ExecutorService mapWorker =
      java.util.concurrent.Executors.newSingleThreadExecutor();
  final java.util.concurrent.ExecutorService visionWorker =
      java.util.concurrent.Executors.newSingleThreadExecutor();
  VisualMatcher visualMatcher;
  WildMapScanner wildScanner;
  boolean visualUnavailable = false, visualLoading = false;
  CoachLearning learning;
  boolean detailOverlayVisible;
  RecordStore recordStore;
  Reading currentReading;
  String linkedId = "", linkedKey = "";
  final java.util.Map<String, String> moveCatalog = new java.util.HashMap<>();
  final Handler handler = new Handler(Looper.getMainLooper());
  HandlerThread captureThread;
  Handler captureHandler;
  volatile boolean captureVisible = true;
  MediaProjection projection;
  VirtualDisplay display;
  ImageReader reader;
  WindowManager wm;
  LinearLayout panel, controls;
  TextView title;
  TextView message;
  TextRecognizer recognizer;
  WindowManager.LayoutParams params, markerParams;
  WildMapMarkerLayer markerLayer;
  volatile boolean paused = false, busy = false, dead = false;
  boolean small = false;
  long last = 0;
  String latest = "", previousSummary = "", structured = "", lastLearnedKey = "";
  int matches = 0;
  boolean stable = false;
  volatile int generation = 0;

  public IBinder onBind(Intent i) {
    return null;
  }

  public int onStartCommand(Intent intent, int flags, int id) {
    if (intent == null || "STOP".equals(intent.getAction())) {
      stopSelf();
      return START_NOT_STICKY;
    }
    if (projection != null) return START_NOT_STICKY;
    NotificationManager nm = getSystemService(NotificationManager.class);
    nm.createNotificationChannel(
        new NotificationChannel("reader", "Screen reader", NotificationManager.IMPORTANCE_LOW));
    PendingIntent stop =
        PendingIntent.getService(
            this,
            0,
            new Intent(this, CoachService.class).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE);
    Notification n =
        new Notification.Builder(this, "reader")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("GO Coach is reading your shared screen")
            .setContentText("Tap Stop when finished")
            .setOngoing(true)
            .addAction(new Notification.Action.Builder(null, "Stop", stop).build())
            .build();
    startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
    try {
      Intent consent = intent.getParcelableExtra("consent");
      if (consent == null) {
        stopSelf();
        return START_NOT_STICKY;
      }
      projection =
          ((MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE))
              .getMediaProjection(Activity.RESULT_OK, consent);
      captureThread =
          new HandlerThread("GO-Coach-capture", android.os.Process.THREAD_PRIORITY_BACKGROUND);
      captureThread.start();
      captureHandler = new Handler(captureThread.getLooper());
      projection.registerCallback(
          new MediaProjection.Callback() {
            public void onStop() {
              stopSelf();
            }

            public void onCapturedContentResize(int width, int height) {
              generation++;
              captureHandler.post(() -> resize(width, height));
            }

            public void onCapturedContentVisibilityChanged(boolean visible) {
              captureVisible = visible;
              generation++;
              if (!visible && learning != null) learning.reset();
              if (wildScanner != null) wildScanner.reset();
              updateMarkers(java.util.Collections.emptyList());
              if (!visible && message != null)
                message.setText("Shared app is hidden. Return to Pokémon GO to resume scanning.");
            }
          },
          handler);
      recordStore = new RecordStore(this);
      try {
        PvpGrade.load(getAssets().open("pvp.json"));
      } catch (Exception ignored) {
      }
      try {
        PvpGrade.loadNames(getAssets().open("pokemon-names.json"));
      } catch (Exception ignored) {
      }
      try (java.io.BufferedReader lines =
          new java.io.BufferedReader(
              new java.io.InputStreamReader(
                  getAssets().open("moves.tsv"), java.nio.charset.StandardCharsets.UTF_8))) {
        String line;
        while ((line = lines.readLine()) != null) {
          String[] parts = line.split("\t", 2);
          if (parts.length == 2)
            moveCatalog.put(
                parts[1].replaceAll("[^A-Za-z]", "").toLowerCase(java.util.Locale.ROOT), line);
        }
      }
      recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
      wildScanner = new WildMapScanner();
      learning = new CoachLearning(this);
      wm = getSystemService(WindowManager.class);
      Rect bounds = wm.getMaximumWindowMetrics().getBounds();
      createReader(bounds.width(), bounds.height());
      display =
          projection.createVirtualDisplay(
              "GO Coach",
              reader.getWidth(),
              reader.getHeight(),
              getResources().getDisplayMetrics().densityDpi,
              DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
              reader.getSurface(),
              null,
              handler);
      overlay();
    } catch (Exception e) {
      Toast.makeText(
              this,
              "Reader could not start. Reopen GO Coach and approve sharing again.",
              Toast.LENGTH_LONG)
          .show();
      stopSelf();
    }
    return START_NOT_STICKY;
  }

  void warmVisualMatcher() {
    if (visualMatcher != null || visualUnavailable || visualLoading) return;
    visualLoading = true;
    handler.post(
        () -> {
          if (!dead && !paused && message != null)
            message.setText("Loading Pokémon recognition in the background…");
        });
    visionWorker.execute(
        () -> {
          boolean ok = false;
          try {
            visualMatcher = new VisualMatcher(this);
            ok = true;
          } catch (Exception | LinkageError e) {
            visualUnavailable = true;
          }
          final boolean ready = ok;
          handler.post(
              () -> {
                visualLoading = false;
                if (!dead && message != null) {
                  String now = String.valueOf(message.getText());
                  if (now.contains("Loading Pokémon recognition"))
                    message.setText(
                        ready
                            ? "Recognition ready. Open a Pokémon detail screen."
                            : "Image recognition unavailable. Text reading still works.");
                }
              });
        });
  }

  void createReader(int w, int h) {
    reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
    reader.setOnImageAvailableListener(r -> frame(r), captureHandler);
  }

  void resize(int w, int h) {
    if (dead || display == null || w <= 0 || h <= 0 || reader == null) return;
    if (reader.getWidth() == w && reader.getHeight() == h) return;
    display.setSurface(null);
    reader.close();
    createReader(w, h);
    display.resize(w, h, getResources().getDisplayMetrics().densityDpi);
    display.setSurface(reader.getSurface());
  }

  void frame(ImageReader r) {
    Image im = null;
    Bitmap bitmap = null;
    boolean claimed = false;
    try {
      im = r.acquireLatestImage();
      if (im == null) return;
      long now = SystemClock.elapsedRealtime();
      if (dead || paused || !captureVisible || busy || now - last < 1000) return;
      busy = true;
      claimed = true;
      last = now;
      final int epoch = generation;
      Image.Plane p = im.getPlanes()[0];
      int w = im.getWidth(), h = im.getHeight();
      int padded = p.getRowStride() / p.getPixelStride();
      Bitmap full = Bitmap.createBitmap(padded, h, Bitmap.Config.ARGB_8888);
      full.copyPixelsFromBuffer(p.getBuffer());
      bitmap = Bitmap.createBitmap(full, 0, 0, w, h);
      if (bitmap != full) full.recycle();
      if (w > 1080) {
        Bitmap reduced = Bitmap.createScaledBitmap(bitmap, 1080, Math.max(1, h * 1080 / w), true);
        bitmap.recycle();
        bitmap = reduced;
      }
      if (!bitmap.isMutable()) {
        Bitmap mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        bitmap.recycle();
        bitmap = mutable;
      }
      final Bitmap work = bitmap;
      if (handler.post(() -> analyzeFrame(work, epoch))) {
        bitmap = null;
        claimed = false;
      }
    } catch (Exception e) {
      handler.post(
          () -> {
            if (!dead && message != null)
              message.setText("Unable to read this frame. Try restarting sharing.");
          });
    } finally {
      if (im != null) im.close();
      if (bitmap != null) bitmap.recycle();
      if (claimed) busy = false;
    }
  }

  void analyzeFrame(Bitmap work, int epoch) {
    if (dead || paused || !captureVisible || epoch != generation) {
      finishFrame(work);
      return;
    }
    try {
      final Rect covered = panelCaptureBounds(work);
      mapWorker.execute(
          () -> {
            java.util.List<WildMapMatch> detected = null;
            try {
              detected = wildScanner.wild(work, covered);
            } catch (Exception ignored) {
            }
            final java.util.List<WildMapMatch> found = detected;
            handler.post(
                () -> {
                  if (dead || paused || !captureVisible || epoch != generation) {
                    finishFrame(work);
                    return;
                  }
                  if (found != null) {
                    try {
                      showMap(found, work);
                    } finally {
                      finishFrame(work);
                    }
                  } else {
                    try {
                      readDetails(work, epoch);
                    } catch (Exception e) {
                      message.setText(
                          "Unable to read this frame. Restart sharing if it continues.");
                      finishFrame(work);
                    }
                  }
                });
          });
    } catch (Exception e) {
      finishFrame(work);
      if (!dead && message != null)
        message.setText("Unable to analyze this frame. Restart sharing if it continues.");
    }
  }

  Rect panelCaptureBounds(Bitmap bitmap) {
    if (panel == null || !panel.isShown()) return null;
    Rect screen = wm.getMaximumWindowMetrics().getBounds();
    int[] location = new int[2];
    panel.getLocationOnScreen(location);
    float sx = bitmap.getWidth() / (float) screen.width(),
        sy = bitmap.getHeight() / (float) screen.height();
    return new Rect(
        (int) (location[0] * sx),
        (int) (location[1] * sy),
        (int) ((location[0] + panel.getWidth()) * sx),
        (int) ((location[1] + panel.getHeight()) * sy));
  }

  void showMap(java.util.List<WildMapMatch> found, Bitmap bitmap) {
    learning.noteMap(bitmap, found);
    updateMarkers(found);
    currentReading = null;
    stable = false;
    matches = 0;
    previousSummary = "";
    linkedId = "";
    linkedKey = "";
    latest = "";
    int stops = 0;
    for (WildMapMatch m : found) if (m.stop) stops++;
    String stamp =
        java.text.DateFormat.getTimeInstance(java.text.DateFormat.MEDIUM)
            .format(new java.util.Date());
    structured =
        "Map scanned · "
            + stamp
            + "\n"
            + (found.size() - stops)
            + " possible Pokémon · "
            + stops
            + " PokéStops";
    message.setText(structured + "\n" + learning.mapAdvice(found));
  }

  void readDetails(Bitmap work, int epoch) {
    Rect covered = panelCaptureBounds(work);
    Rect detailPicture =
        new Rect(
            (int) (work.getWidth() * .08),
            (int) (work.getHeight() * .10),
            (int) (work.getWidth() * .88),
            (int) (work.getHeight() * .345));
    detailOverlayVisible = covered != null && Rect.intersects(covered, detailPicture);
    Bitmap clean = work.copy(Bitmap.Config.ARGB_8888, true);
    if (covered != null) {
      Paint paint = new Paint();
      paint.setColor(Color.BLACK);
      new Canvas(clean).drawRect(covered, paint);
    }
    recognizer
        .process(InputImage.fromBitmap(clean, 0))
        .addOnCompleteListener(
            task -> {
              clean.recycle();
              if (dead
                  || paused
                  || !captureVisible
                  || epoch != generation
                  || !task.isSuccessful()) {
                if (!dead && !task.isSuccessful())
                  message.setText("Text reading failed. Hold the screen still.");
                finishFrame(work);
                return;
              }
              Text result = task.getResult();
              Reading prelim = Reading.parse(result.getText());
              if (prelim.maximum <= 0 && prelim.current < 0) {
                learning.readEncounter(result, work, epoch);
                return;
              }
              learning.current = null;
              analyzeDetails(result, work, epoch);
            });
  }

  void analyzeDetails(Text result, Bitmap work, int epoch) {
    Reading preliminary = Reading.parse(result.getText());
    if (!ScreenKind.hasPokemonDetails(preliminary)) {
      latest = "";
      structured = "";
      currentReading = null;
      stable = false;
      matches = 0;
      previousSummary = "";
      linkedId = "";
      linkedKey = "";
      updateMarkers(java.util.Collections.emptyList());
      message.setText(
          "Waiting for the map or a Pokémon CP / HP reading.\nChecked · "
              + java.text.DateFormat.getTimeInstance(java.text.DateFormat.MEDIUM)
                  .format(new java.util.Date()));
      finishFrame(work);
      return;
    }
    visionWorker.execute(
        () -> {
          String match = "";
          java.util.List<WildMapMatch> wild = null;
          try {
            Reading prelim = Reading.parse(result.getText());
            // OCR selects the kind of screen only. Species matching sees only the upper image crop.
            boolean detailLike =
                !prelim.cp.equals("Unknown")
                    || prelim.current >= 0
                    || prelim.maximum > 0
                    || !prelim.species.equals("Unknown");
            if (detailLike) {
              if (visualMatcher == null && !visualUnavailable) {
                handler.post(
                    () -> {
                      if (!dead && !paused && epoch == generation)
                        message.setText("Loading Pokémon recognition…");
                    });
                warmVisualMatcher();
              }
              if (visualMatcher != null && prelim.species.equals("Unknown"))
                match = visualMatcher.identify(work, prelim.current < 0);
            }

          } catch (Exception e) {
            match = "";
          }
          final String found = match;
          final java.util.List<WildMapMatch> wildFound = wild;
          handler.post(
              () -> {
                try {
                  if (!dead && !paused && epoch == generation)
                    accept(result, work, found, wildFound);
                } finally {
                  finishFrame(work);
                }
              });
        });
  }

  void finishFrame(Bitmap work) {
    work.recycle();
    busy = false;
    if (dead && recognizer != null) recognizer.close();
  }

  void accept(Text t, Bitmap bitmap, String visual, java.util.List<WildMapMatch> wildFound) {
    latest = t.getText();
    Reading r = Reading.parse(latest);
    boolean fromName = false;
    if (r.species.equals("Unknown") && r.maximum > 0) {
      int hpTop = -1;
      for (Text.TextBlock block : t.getTextBlocks())
        for (Text.Line line : block.getLines()) {
          Rect b = line.getBoundingBox();
          if (b != null
              && b.top > bitmap.getHeight() * .25
              && b.top < bitmap.getHeight() * .65
              && line.getText().matches(".*[0-9]+\\s*/\\s*[0-9]+\\s*HP.*")) hpTop = b.top;
        }
      if (hpTop > 0)
        for (Text.TextBlock block : t.getTextBlocks())
          for (Text.Line line : block.getLines()) {
            Rect b = line.getBoundingBox();
            if (b == null || b.bottom > hpTop || b.top < hpTop - bitmap.getHeight() * .10) continue;
            String name = PvpGrade.visibleSpeciesName(line.getText());
            if (!name.isEmpty()) {
              r.species = name;
              fromName = true;
              break;
            }
          }
    }
    if (r.species.equals("Unknown")) {
      for (Text.TextBlock block : t.getTextBlocks())
        for (Text.Line line : block.getLines()) {
          Rect b = line.getBoundingBox();
          if (b == null || b.top > bitmap.getHeight() * .72) continue;
          String cleaned =
              line.getText()
                  .replaceAll("(?i)\\bC\\s*P\\s*[0-9][0-9\\s,.]{0,7}\\b", "")
                  .replace('/', ' ')
                  .trim();
          String name = PvpGrade.visibleSpeciesInLine(line.getText());
          if (name.isEmpty()) name = PvpGrade.visibleSpeciesInLine(cleaned);
          if (name.isEmpty())
            for (Text.Element element : line.getElements()) {
              name = PvpGrade.visibleSpeciesInLine(element.getText());
              if (!name.isEmpty()) break;
            }
          if (!name.isEmpty()) {
            r.species = name;
            fromName = true;
            if (r.cp.equals("Unknown")) {
              java.util.regex.Matcher cp =
                  java.util.regex.Pattern.compile("(?i)\\bC\\s*P\\s*([0-9][0-9\\s,.]{0,7})")
                      .matcher(line.getText());
              if (cp.find()) {
                String digits = cp.group(1).replaceAll("[^0-9]", "");
                if (digits.length() >= 1 && digits.length() <= 5) r.cp = digits;
              }
            }
            break;
          }
        }
    }
    boolean fromImage = r.species.equals("Unknown") && !visual.isEmpty();
    if (fromImage) r.species = visual;

    Reading.Anchor[] anchors = new Reading.Anchor[3];
    for (Text.TextBlock block : t.getTextBlocks())
      for (Text.Line line : block.getLines()) {
        String label = line.getText().trim().toLowerCase(java.util.Locale.ROOT);
        Rect b = line.getBoundingBox();
        if (b == null) continue;
        int i =
            label.equals("attack") ? 0 : label.equals("defense") ? 1 : label.equals("hp") ? 2 : -1;
        if (i >= 0 && b.left < bitmap.getWidth() * .50 && b.top > bitmap.getHeight() * .45)
          anchors[i] = new Reading.Anchor(b.left, b.bottom);
      }
    r.iv =
        Reading.appraisal(
            new Reading.Pixels() {
              public int width() {
                return bitmap.getWidth();
              }

              public int height() {
                return bitmap.getHeight();
              }

              public int get(int x, int y) {
                return bitmap.getPixel(x, y);
              }
            },
            anchors);
    java.util.List<Text.Line> lines = new java.util.ArrayList<>();
    int movesTop = -1, movesEnd = bitmap.getHeight();
    for (Text.TextBlock block : t.getTextBlocks())
      for (Text.Line line : block.getLines()) {
        Rect b = line.getBoundingBox();
        if (b == null) continue;
        lines.add(line);
        String key = line.getText().replaceAll("[^A-Za-z]", "").toLowerCase(java.util.Locale.ROOT);
        if (key.contains("gymsraids") || key.contains("trainerbattles"))
          movesTop = Math.max(movesTop, b.bottom);
        if (key.equals("newattack")) movesEnd = Math.min(movesEnd, b.top);
      }
    lines.sort(java.util.Comparator.comparingInt(line -> line.getBoundingBox().top));
    // A missed heading must not suppress every move. Restrict fallback to the
    // lower half of a recognized CP/HP details screen, above NEW ATTACK.
    if (movesTop < 0 && !r.cp.equals("Unknown") && r.maximum > 0)
      movesTop = (int) (bitmap.getHeight() * .50);
    if (movesTop >= 0)
      for (Text.Line line : lines) {
        Rect b = line.getBoundingBox();
        if (b.top > movesTop && b.bottom <= movesEnd) {
          r.seeMove(line.getText(), moveCatalog);
          // ML Kit sometimes merges name, icon and damage into a single line.
          StringBuilder words = new StringBuilder();
          for (Text.Element element : line.getElements()) {
            String word = element.getText();
            if (word.matches("[0-9]+")) continue;
            if (words.length() > 0) words.append(" ");
            words.append(word);
          }
          r.seeMove(words.toString(), moveCatalog);
        }
      }
    String key = r.fingerprint();
    String observed = r.summary() + "|" + key;
    boolean relevant =
        !r.species.equals("Unknown") || !r.cp.equals("Unknown") || r.current >= 0 || r.iv != null;
    if (relevant && observed.equals(previousSummary)) matches++;
    else matches = 1;
    previousSummary = observed;
    stable = relevant && matches >= 2;
    if (key.isEmpty() || !key.equals(linkedKey)) {
      linkedId = "";
      linkedKey = "";
    }
    String memory = "";
    if (stable && !key.isEmpty()) {
      JSONObject record = recordStore.autoSave(r, fromImage || fromName);
      if (record != null) {
        linkedId = record.optString("id");
        linkedKey = key;
        memory = "Saved to your collection";
        if (record.optBoolean("needsReview")) memory += " · identity needs review";
      } else {
        linkedId = "";
        linkedKey = "";
        memory = "Could not safely match/save this reading. Use Review match.";
      }
    } else if (stable) memory = "Show CP, HP, weight and height so I can save this Pokémon.";
    boolean learnedNow = false;
    String learnKey =
        r.species
            + "|"
            + r.maximum
            + "|"
            + r.weight
            + "|"
            + r.height
            + "|"
            + (r.iv == null ? "" : r.iv[0] + "-" + r.iv[1] + "-" + r.iv[2]);
    boolean canLearn =
        stable
            && !detailOverlayVisible
            && visualMatcher != null
            && !fromImage
            && !r.species.equals("Unknown")
            && r.maximum > 0
            && (!r.weight.isEmpty() || !r.height.isEmpty() || r.iv != null);
    if (canLearn && !learnKey.equals(lastLearnedKey)) {
      lastLearnedKey = learnKey;
      learnedNow = true;
      final Bitmap learnedFrame = bitmap.copy(Bitmap.Config.ARGB_8888, false);
      final String learnedSpecies = r.species;
      final boolean learnedEncounter = r.current < 0;
      visionWorker.execute(
          () -> {
            try {
              visualMatcher.learn(learnedSpecies, learnedFrame, learnedEncounter);
            } finally {
              learnedFrame.recycle();
            }
          });
    }
    currentReading = r;
    structured = r.summary() + "\n" + PvpGrade.brief(r);
    if (fromImage) structured += "\nSpecies: tentative image match";
    else if (fromName) structured += "\nSpecies: visible name (form unverified)";
    if (!memory.isEmpty()) structured += "\n" + memory;
    if (learnedNow) structured += "\nLearning detail image…";
    if (!relevant && wildFound != null && !wildFound.isEmpty()) {
      updateMarkers(wildFound);
      java.util.ArrayList<String> labels = new java.util.ArrayList<>();
      for (WildMapMatch m : wildFound) labels.add(m.name);
      String names = String.join(", ", labels);
      structured = "Wild nearby: " + names;
      matches = 0;
      currentReading = null;
      message.setText(
          "Wild nearby:\n"
              + names
              + "\n"
              + "Unknown markers mean tap that Pokémon, then let the encounter screen teach me its"
              + " name.");
      return;
    }
    if (!relevant) {
      structured =
          "No Pokémon details identified. Open details or appraise a Pokémon. Wild recognition is"
              + " checking the map.";
      matches = 0;
      currentReading = null;
      updateMarkers(java.util.Collections.emptyList());
    } else updateMarkers(java.util.Collections.emptyList());

    String advice =
        !relevant
            ? "No map or Pokémon details detected. Share Pokémon GO with its map visible, then move"
                + " the map slightly."
            : !stable
                ? "Checking this Pokémon…"
                : r.species
                    + " · CP "
                    + r.cp
                    + "\n"
                    + (r.iv == null ? "Appraisal: open Appraise to add it" : "Appraisal remembered")
                    + "\n"
                    + (r.fast.isEmpty()
                        ? "Moves: show its moves to add them"
                        : r.fast
                            + (r.charged.isEmpty() ? "" : " + " + String.join(" + ", r.charged)));
    if (relevant && stable && r.current < 0)
      advice =
          r.species
              + " · CP "
              + r.cp
              + "\n"
              + "Wild encounter found. Catch it, then open details/appraise to save the full"
              + " record.";
    if (relevant && stable && fromImage) advice += "\nIdentification is tentative.";
    if (relevant && stable && learnedNow) advice += "\nLearning this detail picture…";
    if (relevant && stable && r.current >= 0 && !memory.isEmpty()) advice += "\n\n" + memory;
    message.setText(
        advice
            + "\nChecked · "
            + java.text.DateFormat.getTimeInstance(java.text.DateFormat.MEDIUM)
                .format(new java.util.Date()));
  }

  int dp(int x) {
    return (int) (x * getResources().getDisplayMetrics().density);
  }

  void overlay() {
    panel = new LinearLayout(this);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(dp(10), dp(6), dp(10), dp(6));
    panel.setBackground(background());
    panel.setElevation(0);
    title = new TextView(this);
    title.setText("GO Coach · collapse");
    title.setTextColor(Color.rgb(100, 235, 190));
    title.setTextSize(16);
    title.setShadowLayer(dp(2), 0, dp(1), Color.BLACK);
    title.setPadding(0, dp(10), 0, dp(10));
    panel.addView(title);
    message = new TextView(this);
    message.setText("Open Pokémon GO. Approve screen sharing, then switch to the game.");
    message.setTextColor(Color.WHITE);
    message.setTextSize(13);
    message.setShadowLayer(dp(2), 0, dp(1), Color.BLACK);
    ScrollView readingScroll = new ScrollView(this);
    readingScroll.addView(message);
    panel.addView(readingScroll, new LinearLayout.LayoutParams(-1, dp(100)));
    controls = new LinearLayout(this);
    controls.setOrientation(LinearLayout.VERTICAL);
    panel.addView(controls);
    LinearLayout row = new LinearLayout(this);
    controls.addView(row);
    Button pause = new Button(this);
    pause.setText("Pause");
    row.addView(pause, new LinearLayout.LayoutParams(0, dp(48), 1));
    pause.setOnClickListener(
        v -> {
          paused = !paused;
          generation++;
          if (wildScanner != null) wildScanner.reset();
          if (learning != null) learning.reset();
          matches = 0;
          stable = false;
          previousSummary = "";
          structured = "";
          linkedId = "";
          linkedKey = "";
          currentReading = null;
          updateMarkers(java.util.Collections.emptyList());
          pause.setText(paused ? "Resume" : "Pause");
          latest = "";
          message.setText(
              paused ? "Paused • no frames analyzed" : "Resuming • waiting for a fresh reading");
        });
    Button stop = new Button(this);
    stop.setText("Stop");
    row.addView(stop, new LinearLayout.LayoutParams(0, dp(48), 1));
    stop.setOnClickListener(v -> stopSelf());
    Button grade = new Button(this);
    grade.setText("Show details");
    controls.addView(grade);
    grade.setOnClickListener(
        v -> {
          if (currentReading == null || !stable) {
            Toast.makeText(this, "Hold the Pokémon details still for a reading", Toast.LENGTH_SHORT)
                .show();
            return;
          }
          final boolean wasPaused = paused;
          paused = true;
          generation++;
          AlertDialog dialog =
              new AlertDialog.Builder(this)
                  .setTitle("Optional PvP details")
                  .setMessage(PvpGrade.report(currentReading))
                  .setPositiveButton("Close", null)
                  .create();
          dialog.setOnDismissListener(
              d -> {
                paused = wasPaused;
              });
          showOverlayDialog(dialog);
        });
    Button teach = new Button(this);
    teach.setText("Correct map (optional)");
    controls.addView(teach);
    teach.setOnClickListener(v -> learning.teachMap());
    Button link = new Button(this);
    link.setText("Review match");
    controls.addView(link);
    link.setOnClickListener(v -> linkRecord());

    Button save = new Button(this);
    save.setText("Save reading");
    controls.addView(save);
    save.setOnClickListener(v -> saveScan());
    for (Button button : new Button[] {pause, stop, grade, teach, link, save}) {
      android.graphics.drawable.GradientDrawable glass =
          new android.graphics.drawable.GradientDrawable();
      glass.setColor(Color.argb(70, 18, 30, 44));
      glass.setCornerRadius(dp(18));
      glass.setStroke(dp(1), Color.argb(120, 100, 235, 190));
      button.setBackgroundTintList(null);
      button.setBackground(glass);
      button.setTextColor(Color.WHITE);
      button.setShadowLayer(dp(2), 0, dp(1), Color.BLACK);
    }
    markerLayer = new WildMapMarkerLayer(this);
    markerParams =
        new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
    markerParams.alpha =
        0.6f; // Keep the non-touchable marker window below Android's touch-obscuring limit.
    markerParams.gravity = Gravity.TOP | Gravity.LEFT;

    params =
        new WindowManager.LayoutParams(
            dp(248),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);
    params.gravity = Gravity.TOP | Gravity.LEFT;
    params.x = dp(12);
    params.y = dp(36);
    wm.addView(panel, params);
    wm.addView(markerLayer, markerParams);
    title.setContentDescription("GO Coach. Tap to expand or collapse; drag to move.");
    title.setOnTouchListener(
        new View.OnTouchListener() {
          float x, y;
          int px, py;
          boolean moved;

          public boolean onTouch(View v, MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
              x = e.getRawX();
              y = e.getRawY();
              px = params.x;
              py = params.y;
              moved = false;
              return true;
            }
            if (e.getAction() == MotionEvent.ACTION_MOVE) {
              if (Math.abs(e.getRawX() - x) + Math.abs(e.getRawY() - y) > dp(8)) moved = true;
              if (moved) {
                params.x = px + (int) (e.getRawX() - x);
                params.y = py + (int) (e.getRawY() - y);
                clamp();
                wm.updateViewLayout(panel, params);
              }
              return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP) {
              if (!moved) setSmall(!small);
              return true;
            }
            return true;
          }
        });
    setSmall(true);
  }

  final Runnable expireMarkers =
      () -> {
        if (markerLayer != null) markerLayer.setMarkers(java.util.Collections.emptyList());
      };

  void updateMarkers(java.util.List<WildMapMatch> markers) {
    handler.removeCallbacks(expireMarkers);
    if (markerLayer != null) markerLayer.setMarkers(markers);
    if (markers != null && !markers.isEmpty()) handler.postDelayed(expireMarkers, 2200);
  }

  android.graphics.drawable.GradientDrawable background() {
    android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
    d.setColor(Color.argb(235, 11, 20, 32));
    d.setCornerRadius(dp(24));
    d.setStroke(dp(1), Color.argb(70, 100, 235, 190));
    return d;
  }

  void clamp() {
    Rect bounds = wm.getCurrentWindowMetrics().getBounds();
    params.x = Math.max(0, Math.min(Math.max(0, bounds.width() - params.width), params.x));
    params.y =
        Math.max(
            dp(36),
            Math.min(Math.max(dp(36), bounds.height() - panel.getHeight() - dp(52)), params.y));
  }

  void setSmall(boolean value) {
    small = value;
    controls.setVisibility(small ? View.GONE : View.VISIBLE);
    title.setText(small ? "GO Coach  ·  expand" : "GO Coach  ·  collapse");
    title.setGravity(Gravity.LEFT);
    title.setPadding(0, 0, 0, 0);
    title.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(36)));
    message.setMaxLines(small ? 2 : 8);
    message.setEllipsize(android.text.TextUtils.TruncateAt.END);
    ((View) message.getParent())
        .setLayoutParams(new LinearLayout.LayoutParams(-1, dp(small ? 40 : 110)));
    panel.setPadding(dp(12), dp(6), dp(12), dp(10));
    params.width = dp(small ? 220 : 260);
    params.height = WindowManager.LayoutParams.WRAP_CONTENT;
    clamp();
    wm.updateViewLayout(panel, params);
    panel.post(
        () -> {
          if (!dead) {
            clamp();
            wm.updateViewLayout(panel, params);
          }
        });
  }

  void showOverlayDialog(AlertDialog dialog) {
    dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
    dialog.show();
  }

  void linkRecord() {
    if (paused || !stable || currentReading == null || currentReading.fingerprint().isEmpty()) {
      Toast.makeText(
              this, "Hold details still with CP, HP, weight and height visible", Toast.LENGTH_LONG)
          .show();
      return;
    }
    final Reading selected = currentReading;
    final JSONArray records = recordStore.all();
    if (records.length() == 0) {
      Toast.makeText(this, "Save a reading and add a collection record first", Toast.LENGTH_LONG)
          .show();
      return;
    }
    String[] names = new String[records.length()];
    for (int i = 0; i < names.length; i++) {
      JSONObject o = records.optJSONObject(i);
      names[i] = o == null ? "Unavailable" : o.optString("name") + " · record " + (i + 1);
    }
    showOverlayDialog(
        new AlertDialog.Builder(this)
            .setTitle("Which saved Pokémon is this?")
            .setItems(
                names,
                (d, which) -> {
                  JSONObject chosen = records.optJSONObject(which);
                  if (chosen == null) return;
                  showOverlayDialog(
                      new AlertDialog.Builder(this)
                          .setTitle("Update " + chosen.optString("name") + "?")
                          .setMessage(
                              selected.summary()
                                  + "\n\n"
                                  + "Confirm this is the same individual. Visible values will"
                                  + " update this record; missing values will be kept.")
                          .setPositiveButton(
                              "Link and update",
                              (dd, w) -> {
                                if (recordStore.update(chosen.optString("id"), selected, true)) {
                                  linkedId = chosen.optString("id");
                                  linkedKey = selected.fingerprint();
                                  Toast.makeText(
                                          this,
                                          "Linked. Saved appraisal and moves will appear on the"
                                              + " next reading.",
                                          Toast.LENGTH_LONG)
                                      .show();
                                } else
                                  Toast.makeText(this, "Could not link record", Toast.LENGTH_LONG)
                                      .show();
                              })
                          .setNegativeButton("Cancel", null)
                          .create());
                })
            .setNegativeButton("Cancel", null)
            .create());
  }

  void saveScan() {
    if (paused || !stable || latest.trim().isEmpty()) {
      Toast.makeText(this, "Hold a Pokémon screen still for two readings first", Toast.LENGTH_SHORT)
          .show();
      return;
    }
    try {
      android.content.SharedPreferences prefs = getSharedPreferences("data", 0);
      JSONArray a = new JSONArray(prefs.getString("scans", "[]"));
      JSONObject o = new JSONObject();
      o.put("text", structured + "\n\nRaw OCR (unverified):\n" + latest);
      if (currentReading != null) {
        o.put("details", RecordStore.encode(currentReading));
        o.put("fingerprint", currentReading.fingerprint());
      }
      o.put("time", System.currentTimeMillis());
      a.put(o);
      while (a.length() > 200) a.remove(0);
      prefs.edit().putString("scans", a.toString()).apply();
      Toast.makeText(this, "Saved for review in GO Coach", Toast.LENGTH_SHORT).show();
    } catch (Exception e) {
      Toast.makeText(this, "Could not save reading", Toast.LENGTH_SHORT).show();
    }
  }

  public void onDestroy() {
    dead = true;
    if (learning != null) learning.close();
    handler.removeCallbacks(expireMarkers);
    mapWorker.shutdown();
    visionWorker.execute(
        () -> {
          if (visualMatcher != null) visualMatcher.close();
        });
    visionWorker.shutdown();
    if (captureHandler != null) {
      captureHandler.post(
          () -> {
            try {
              if (display != null) display.release();
              if (reader != null) reader.close();
              if (projection != null) projection.stop();
            } finally {
              captureThread.quitSafely();
            }
          });
    } else if (projection != null) projection.stop();
    if (wm != null && markerLayer != null) {
      try {
        wm.removeView(markerLayer);
      } catch (Exception ignored) {
      }
    }
    if (wm != null && panel != null) {
      try {
        wm.removeView(panel);
      } catch (Exception ignored) {
      }
    }
    if (recognizer != null && !busy) recognizer.close();
    stopForeground(STOP_FOREGROUND_REMOVE);
    super.onDestroy();
  }
}
