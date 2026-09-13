package com.david.gocoach;

import android.app.*;
import android.content.*;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.*;

public class MainActivity extends Activity {
  LinearLayout body;
  JSONObject pendingScan;
  Runnable collectionBack;

  @Override
  public void onBackPressed() {
    if (collectionBack != null) collectionBack.run();
    else super.onBackPressed();
  }

  protected void onCreate(Bundle b) {
    super.onCreate(b);
    try {
      PvpGrade.load(getAssets().open("pvp.json"));
    } catch (Exception ignored) {
    }
    home();
  }

  int dp(int n) {
    return CoachUi.dp(this, n);
  }

  TextView text(String value, int size) {
    TextView t =
        CoachUi.label(this, value, size, size >= 22 ? CoachUi.TEXT : CoachUi.MUTED, size >= 22);
    t.setPadding(0, dp(8), 0, dp(8));
    t.setLineSpacing(dp(3), 1);
    body.addView(t);
    return t;
  }

  void button(String label, Runnable action) {
    Button b = CoachUi.button(this, label, false, action);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(0, dp(5), 0, dp(5));
    body.addView(b, p);
  }

  void page(String title) {
    collectionBack = null;
    ScrollView scroll = new ScrollView(this);
    body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(22), dp(18), dp(22), dp(28));
    scroll.setFillViewport(true);
    scroll.setBackgroundColor(CoachUi.BACKGROUND);
    scroll.addView(body);
    setContentView(scroll);
    scroll.setOnApplyWindowInsetsListener(
        (v, i) -> {
          android.graphics.Insets x = i.getInsets(WindowInsets.Type.systemBars());
          v.setPadding(x.left, x.top, x.right, x.bottom);
          return i;
        });
    text(title, 30);
  }

  void home() {
    HomeScreen.show(this);
  }

  void startReader() {
    if (!Settings.canDrawOverlays(this)) {
      new AlertDialog.Builder(this)
          .setMessage("Allow the floating coach first, then return here.")
          .setPositiveButton(
              "Open settings",
              (d, w) ->
                  startActivity(
                      new Intent(
                          Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                          Uri.parse("package:" + getPackageName()))))
          .setNegativeButton("Cancel", null)
          .show();
      return;
    }
    stopService(new Intent(this, CoachService.class));
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
            != android.content.pm.PackageManager.PERMISSION_GRANTED)
      requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"}, 20);
    MediaProjectionManager capture =
        (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
    startActivityForResult(capture.createScreenCaptureIntent(), 10);
  }

  void chooseCoachGoal() {
    String[]
        labels = {"Build my collection", "Collect Candy for evolutions", "Compare Pokémon for PvP"},
        keys = {"collection", "candy", "pvp"};
    String current = getSharedPreferences("coach", 0).getString("goal", "collection");
    int checked = java.util.Arrays.asList(keys).indexOf(current);
    new AlertDialog.Builder(this)
        .setTitle("What should GO Coach help with?")
        .setSingleChoiceItems(
            labels,
            Math.max(0, checked),
            (dialog, which) -> {
              getSharedPreferences("coach", 0).edit().putString("goal", keys[which]).apply();
              dialog.dismiss();
              home();
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  protected void onActivityResult(int req, int result, Intent data) {
    super.onActivityResult(req, result, data);
    if (req == 10 && result == RESULT_OK && data != null) {
      Intent i = new Intent(this, CoachService.class);
      i.putExtra("consent", data);
      startForegroundService(i);
    }
  }

  JSONArray read(String key) {
    try {
      return new JSONArray(getSharedPreferences("data", 0).getString(key, "[]"));
    } catch (Exception e) {
      return new JSONArray();
    }
  }

  void save(String key, JSONArray a) {
    getSharedPreferences("data", 0).edit().putString(key, a.toString()).apply();
  }

  void scans() {
    page("Saved scans");
    button("Back", this::home);
    JSONArray a = read("scans");
    text("These are unverified text readings. Tap to review. Long-press to delete old scans.", 15);
    for (int n = a.length() - 1; n >= 0; n--) {
      JSONObject o = a.optJSONObject(n);
      if (o == null) continue;
      String raw = o.optString("text");
      Button b = new Button(this);
      b.setAllCaps(false);
      b.setText(
          new java.util.Date(o.optLong("time"))
              + "\n"
              + raw.substring(0, Math.min(75, raw.length())));
      body.addView(b);
      final int index = n;
      b.setOnClickListener(
          v ->
              new AlertDialog.Builder(this)
                  .setTitle("Unverified scan")
                  .setMessage(raw)
                  .setPositiveButton(
                      "Add Pokémon",
                      (d, w) -> {
                        pendingScan = o;
                        editRecord(raw);
                      })
                  .setNegativeButton("Delete", (d, w) -> deleteScan(index))
                  .setNeutralButton("Close", null)
                  .show());
      b.setOnLongClickListener(
          v -> {
            confirmDeleteScan(index);
            return true;
          });
    }
    if (a.length() == 0) text("No scans saved.", 17);
  }

  void confirmDeleteScan(int index) {
    new AlertDialog.Builder(this)
        .setTitle("Delete saved scan?")
        .setMessage(
            "This removes only the old unverified scan text. It does not delete Pokémon collection"
                + " records or learned image memory.")
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Delete", (d, w) -> deleteScan(index))
        .show();
  }

  void deleteScan(int index) {
    JSONArray a = read("scans");
    if (index >= 0 && index < a.length()) {
      a.remove(index);
      save("scans", a);
    }
    scans();
  }

  void editRecord(String raw) {
    page("Confirm Pokémon");
    text(
        "Give each duplicate its own record. Review the extracted appraisal values. Collectible"
            + " traits remain unknown until you confirm them.",
        15);
    EditText name = new EditText(this);
    name.setHint("Pokémon / nickname (required)");
    body.addView(name);
    EditText stats = new EditText(this);
    int cut = raw.indexOf("Raw OCR (unverified):");
    if (cut > 0) stats.setText(raw.substring(0, cut).trim());
    stats.setHint("CP · Attack/Defense/HP · moves (optional)");
    body.addView(stats);
    CheckBox protect = new CheckBox(this);
    protect.setText("Protect from transfer suggestions");
    protect.setChecked(true);
    body.addView(protect);
    EditText traits = new EditText(this);
    traits.setHint("Shiny / background / form — leave blank if unknown");
    body.addView(traits);
    button(
        "Save new Pokémon",
        () -> {
          if (name.getText().toString().trim().isEmpty()) {
            name.setError("Enter a name");
            return;
          }
          try {
            JSONArray a = read("collection");
            JSONObject o = new JSONObject();
            o.put("id", java.util.UUID.randomUUID().toString());
            o.put("name", name.getText().toString());
            o.put("stats", stats.getText().toString());
            o.put("traits", traits.getText().toString());
            o.put("protected", protect.isChecked());
            o.put("scan", raw);
            if (pendingScan != null
                && pendingScan.optJSONObject("details") != null
                && cut > 0
                && stats.getText().toString().equals(raw.substring(0, cut).trim())) {
              o.put("details", pendingScan.optJSONObject("details"));
              o.put("fingerprint", pendingScan.optString("fingerprint"));
            }
            o.put("time", System.currentTimeMillis());
            a.put(o);
            save("collection", a);
            collection();
          } catch (Exception e) {
            text("Could not save record.", 16);
          }
        });
    button("Cancel", this::scans);
  }

  void collection() {
    new CollectionScreen(this).show();
  }

  void savedGrade(JSONObject record) {
    JSONObject details = record.optJSONObject("details");
    if (details == null) {
      new AlertDialog.Builder(this)
          .setMessage("This older record needs one linked details reading before it can be graded.")
          .setPositiveButton("Close", null)
          .show();
      return;
    }
    Reading r = new Reading();
    r.species = details.optString("species", "Unknown");
    r.cp = details.optString("cp", "Unknown");
    RecordStore.recall(r, record);
    new AlertDialog.Builder(this)
        .setTitle("Coach advice")
        .setMessage(PvpGrade.brief(r))
        .setPositiveButton("Close", null)
        .setNeutralButton(
            "Show details",
            (d, w) ->
                new AlertDialog.Builder(this)
                    .setTitle("Optional PvP details")
                    .setMessage(PvpGrade.report(r))
                    .setPositiveButton("Close", null)
                    .show())
        .show();
  }
}
