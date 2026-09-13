package com.david.gocoach;

import android.app.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Native, searchable collection with saved values only. */
final class CollectionScreen {
  final MainActivity a;
  final RecordStore store;
  LinearLayout root, bar;
  ListView list;
  final ArrayList<JSONObject> shown = new ArrayList<>();
  String query = "";
  int sort = 0;
  boolean protectedOnly = false;
  String detailId = "";
  final int blue = CoachUi.SURFACE,
      ink = CoachUi.TEXT,
      muted = CoachUi.MUTED,
      green = CoachUi.ACCENT;
  final Map<String, Bitmap> pictures = new HashMap<>();
  final Map<String, String> types = new HashMap<>();

  CollectionScreen(MainActivity a) {
    this.a = a;
    store = new RecordStore(a);
    try (java.io.InputStream in = a.getAssets().open("move-types.json")) {
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      byte[] b = new byte[4096];
      int n;
      while ((n = in.read(b)) != -1) out.write(b, 0, n);
      JSONObject j = new JSONObject(out.toString("UTF-8"));
      Iterator<String> keys = j.keys();
      while (keys.hasNext()) {
        String k = keys.next();
        types.put(k, j.getString(k));
      }
    } catch (Exception ignored) {
    }
  }

  int dp(int n) {
    return (int) (n * a.getResources().getDisplayMetrics().density + .5f);
  }

  TextView label(String s, int size, int color, boolean bold) {
    TextView t = new TextView(a);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    if (bold) t.setTypeface(null, Typeface.BOLD);
    return t;
  }

  LinearLayout column() {
    LinearLayout l = new LinearLayout(a);
    l.setOrientation(1);
    return l;
  }

  LinearLayout row() {
    LinearLayout l = new LinearLayout(a);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  void base(String title, Runnable back) {
    a.collectionBack = back;
    root = column();
    root.setBackgroundColor(CoachUi.BACKGROUND);
    a.setContentView(root);
    root.setOnApplyWindowInsetsListener(
        (v, i) -> {
          Insets x = i.getInsets(WindowInsets.Type.systemBars());
          v.setPadding(x.left, x.top, x.right, x.bottom);
          return i;
        });
    bar = row();
    bar.setBackgroundColor(blue);
    bar.setMinimumHeight(dp(56));
    root.addView(bar);
    action(bar, "‹", "Back", back);
    TextView heading = label(title, 20, Color.WHITE, false);
    heading.setSingleLine();
    heading.setEllipsize(TextUtils.TruncateAt.END);
    bar.addView(heading, new LinearLayout.LayoutParams(0, dp(54), 1));
    heading.setGravity(Gravity.CENTER_VERTICAL);
  }

  void action(LinearLayout parent, String text, String description, Runnable run) {
    TextView b = label(text, 24, Color.WHITE, false);
    b.setGravity(Gravity.CENTER);
    b.setContentDescription(description);
    parent.addView(b, new LinearLayout.LayoutParams(dp(48), dp(54)));
    b.setOnClickListener(v -> run.run());
  }

  void line(LinearLayout parent) {
    View v = new View(a);
    v.setBackgroundColor(CoachUi.BORDER);
    parent.addView(v, new LinearLayout.LayoutParams(-1, dp(1)));
  }

  Reading reading(JSONObject o) {
    JSONObject d = o.optJSONObject("details");
    Reading r = Reading.parse(o.optString("stats"));
    if (d != null) {
      r.species = d.optString("species", "Unknown");
      r.cp = d.optString("cp", "Unknown");
      r.maximum = d.optInt("maximum", -1);
      r.weight = d.optString("weight");
      r.height = d.optString("height");
    }
    RecordStore.recall(r, o);
    return r;
  }

  double percent(Reading r) {
    if (r.iv == null || r.iv.length != 3) return -1;
    int sum = 0;
    for (int x : r.iv) {
      if (x < 0 || x > 15) return -1;
      sum += x;
    }
    return sum * 100.0 / 45;
  }

  String ivText(Reading r) {
    return percent(r) < 0 ? "IV not read" : "IV " + r.iv[0] + "–" + r.iv[1] + "–" + r.iv[2];
  }

  String date(JSONObject o) {
    return o.optLong("time") > 0
        ? java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
            .format(new Date(o.optLong("time")))
        : "Date unknown";
  }

  void show() {
    detailId = "";
    base("My Pokémon (" + store.all().length() + ")", a::home);
    action(
        bar,
        "⋮",
        "Sort and filter",
        () ->
            new AlertDialog.Builder(a)
                .setTitle("Collection options")
                .setItems(
                    new String[] {
                      "Sort: newest first",
                      "Sort: highest IV",
                      "Sort: highest CP",
                      "Sort: name",
                      protectedOnly ? "Show all Pokémon" : "Show protected only",
                      "Clean duplicate records",
                      "Add Pokémon manually"
                    },
                    (d, n) -> {
                      if (n < 4) sort = n;
                      else if (n == 4) protectedOnly = !protectedOnly;
                      else if (n == 5) {
                        int count = store.cleanDuplicates();
                        Toast.makeText(
                                a,
                                count == 0
                                    ? "No duplicates found"
                                    : "Cleaned " + count + " duplicate records",
                                Toast.LENGTH_SHORT)
                            .show();
                      } else {
                        a.pendingScan = null;
                        a.editRecord("");
                        return;
                      }
                      refresh();
                    })
                .show());
    EditText search = new EditText(a);
    search.setSingleLine();
    search.setTextColor(ink);
    search.setHintTextColor(muted);
    search.setTextSize(16);
    search.setHint("Search Pokémon or moves");
    search.setPadding(dp(16), dp(8), dp(16), dp(8));
    search.setText(query);
    root.addView(search, new LinearLayout.LayoutParams(-1, dp(50)));
    list = new ListView(a);
    list.setDividerHeight(dp(1));
    root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    TextView empty =
        label(
            "No matching Pokémon.\nOpen Pokémon details in the game to start your collection.",
            17,
            muted,
            false);
    empty.setGravity(Gravity.CENTER);
    root.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1));
    list.setEmptyView(empty);
    list.setOnItemClickListener((p, v, n, id) -> detail(shown.get(n).optString("id")));
    list.setOnItemLongClickListener(
        (p, v, n, id) -> {
          confirmRemove(shown.get(n).optString("id"), true);
          return true;
        });
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int c, int af) {}

          public void onTextChanged(CharSequence s, int st, int b, int c) {
            query = s.toString();
            refresh();
          }

          public void afterTextChanged(Editable e) {}
        });
    refresh();
  }

  int cp(Reading r) {
    try {
      return Integer.parseInt(r.cp);
    } catch (Exception e) {
      return -1;
    }
  }

  void refresh() {
    shown.clear();
    JSONArray all = store.all();
    for (int i = 0; i < all.length(); i++) {
      JSONObject o = all.optJSONObject(i);
      if (o == null) continue;
      Reading r = reading(o);
      String hay =
          (o.optString("name") + " " + r.species + " " + r.fast + " " + String.join(" ", r.charged))
              .toLowerCase(Locale.ROOT);
      if (hay.contains(query.toLowerCase(Locale.ROOT))
          && (!protectedOnly || o.optBoolean("protected"))) shown.add(o);
    }
    shown.sort(
        (x, y) ->
            sort == 1
                ? Double.compare(percent(reading(y)), percent(reading(x)))
                : sort == 2
                    ? Integer.compare(cp(reading(y)), cp(reading(x)))
                    : sort == 3
                        ? x.optString("name").compareToIgnoreCase(y.optString("name"))
                        : Long.compare(y.optLong("time"), x.optLong("time")));
    list.setAdapter(
        new BaseAdapter() {
          public int getCount() {
            return shown.size();
          }

          public Object getItem(int n) {
            return shown.get(n);
          }

          public long getItemId(int n) {
            return n;
          }

          public View getView(int n, View reuse, ViewGroup parent) {
            return entry(shown.get(n));
          }
        });
  }

  View portrait(Reading r, int size) {
    String name = r.species.toLowerCase(Locale.ROOT).replace(' ', '-');
    Bitmap b = pictures.get(name);
    if (b == null)
      try (java.io.InputStream in = a.getAssets().open("portraits/" + name + ".png")) {
        b = BitmapFactory.decodeStream(in);
        pictures.put(name, b);
      } catch (Exception ignored) {
      }
    if (b == null) {
      TextView t = label("?", 28, muted, true);
      t.setGravity(Gravity.CENTER);
      t.setBackgroundColor(CoachUi.SURFACE);
      t.setContentDescription("Picture unavailable");
      t.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
      return t;
    }
    ImageView image = new ImageView(a);
    image.setImageBitmap(b);
    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
    image.setBackgroundColor(CoachUi.SURFACE);
    image.setContentDescription(r.species + " reference picture; appearance not verified");
    image.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
    return image;
  }

  View entry(JSONObject o) {
    Reading r = reading(o);
    LinearLayout l = row();
    l.setPadding(dp(10), dp(10), dp(10), dp(10));
    l.setMinimumHeight(dp(105));
    l.addView(portrait(r, 54));
    LinearLayout info = column();
    info.setPadding(dp(10), 0, dp(6), 0);
    l.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
    TextView name = label(o.optString("name", r.species), 16, ink, true);
    name.setMaxLines(1);
    name.setEllipsize(TextUtils.TruncateAt.END);
    info.addView(name);
    info.addView(
        label("CP " + r.cp + " / HP " + (r.maximum < 0 ? "—" : r.maximum), 13, ink, false));
    info.addView(
        label(ivText(r) + (o.optBoolean("needsReview") ? " · Review ID" : ""), 13, muted, false));
    moves(info, r, 11);
    if (r.fast.isEmpty() && r.charged.isEmpty()) info.addView(label(date(o), 11, muted, false));
    l.addView(new Ring(percent(r), 54));
    return l;
  }

  int typeColor(String move) {
    String t = types.getOrDefault(move, "");
    switch (t) {
      case "fire":
        return 0xffdf793e;
      case "water":
        return 0xff458cba;
      case "grass":
        return 0xff6b963d;
      case "electric":
        return 0xff987719;
      case "fighting":
        return 0xffc74760;
      case "psychic":
        return 0xffce5980;
      case "ghost":
      case "poison":
        return 0xff7963a9;
      case "ice":
        return 0xff398b97;
      case "bug":
        return 0xff7c902c;
      case "dark":
        return 0xff62616b;
      case "steel":
        return 0xff63858f;
      case "ground":
      case "rock":
        return 0xff9a8150;
      case "dragon":
        return 0xff626eb3;
      case "fairy":
        return 0xffb35c9a;
      default:
        return 0xff818a87;
    }
  }

  void moves(LinearLayout parent, Reading r, int size) {
    ArrayList<String> names = new ArrayList<>();
    if (!r.fast.isEmpty()) names.add(r.fast);
    names.addAll(r.charged);
    if (names.isEmpty()) return;
    LinearLayout line = row();
    parent.addView(line);
    for (String name : names) {
      TextView t = label(name, size, Color.WHITE, true);
      t.setPadding(dp(4), dp(3), dp(4), dp(3));
      GradientDrawable bg = new GradientDrawable();
      bg.setColor(typeColor(name));
      bg.setCornerRadius(dp(3));
      t.setBackground(bg);
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
      p.setMargins(0, dp(4), dp(3), 0);
      line.addView(t, p);
    }
  }

  void detail(String id) {
    JSONObject o = store.get(id);
    if (o == null) {
      show();
      return;
    }
    detailId = id;
    Reading r = reading(o);
    base(o.optString("name", r.species), this::show);
    action(
        bar,
        "⋮",
        "Record actions",
        () ->
            new AlertDialog.Builder(a)
                .setItems(
                    new String[] {"View saved scan", "Remove record"},
                    (d, n) -> {
                      if (n == 0)
                        new AlertDialog.Builder(a)
                            .setTitle("Saved reading")
                            .setMessage(o.optString("stats"))
                            .setPositiveButton("Close", null)
                            .show();
                      else remove(id);
                    })
                .show());
    ScrollView scroll = new ScrollView(a);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout content = column();
    content.setPadding(dp(16), dp(18), dp(16), dp(24));
    scroll.addView(content);
    LinearLayout hero = row();
    hero.addView(portrait(r, 100));
    LinearLayout specs = column();
    specs.setPadding(dp(14), 0, 0, 0);
    hero.addView(specs, new LinearLayout.LayoutParams(0, -2, 1));
    specs.addView(
        label("CP " + r.cp + " / HP " + (r.maximum < 0 ? "—" : r.maximum), 19, ink, true));
    specs.addView(
        label("Weight " + (r.weight.isEmpty() ? "—" : r.weight + " kg"), 14, muted, false));
    specs.addView(
        label("Height " + (r.height.isEmpty() ? "—" : r.height + " m"), 14, muted, false));
    specs.addView(label(date(o), 12, muted, false));
    content.addView(hero);
    section(content, "APPRAISAL");
    LinearLayout grade = row();
    grade.setGravity(Gravity.CENTER);
    grade.addView(new Ring(percent(r), 76));
    TextView verdict =
        label(
            percent(r) < 0
                ? "IV not read"
                : String.format(Locale.US, "%.1f%% overall IV", percent(r)),
            22,
            green,
            true);
    verdict.setPadding(dp(15), 0, 0, 0);
    grade.addView(verdict);
    content.addView(grade);
    TextView hint = label("Overall IV is separate from PvP usefulness.", 13, muted, false);
    hint.setGravity(Gravity.CENTER);
    content.addView(hint);
    LinearLayout values = row();
    String[] labels = {"Attack IV", "Defense IV", "HP IV"};
    for (int i = 0; i < 3; i++) {
      LinearLayout cell = column();
      cell.setGravity(Gravity.CENTER);
      cell.addView(label(labels[i], 15, ink, true));
      cell.addView(label(percent(r) < 0 ? "—" : r.iv[i] + " / 15", 25, ink, false));
      values.addView(cell, new LinearLayout.LayoutParams(0, dp(85), 1));
    }
    content.addView(values);
    moves(content, r, 16);
    if (r.fast.isEmpty() || r.charged.isEmpty())
      content.addView(
          label("Open the moves section in Pokémon GO to fill missing moves.", 14, muted, false));
    section(content, "PVP ASSESSMENT");
    content.addView(label(PvpGrade.brief(r), 16, ink, true));
    Button pvp = new Button(a);
    pvp.setAllCaps(false);
    pvp.setText("Coach advice");
    content.addView(pvp);
    pvp.setOnClickListener(v -> a.savedGrade(o));
    section(content, "COLLECTION SETTINGS");
    Switch protect = new Switch(a);
    protect.setText("Protect from transfer suggestions");
    protect.setTextColor(ink);
    protect.setChecked(o.optBoolean("protected", true));
    content.addView(protect);
    protect.setOnCheckedChangeListener(
        (v, checked) -> {
          JSONArray all = store.all();
          for (int n = 0; n < all.length(); n++) {
            JSONObject item = all.optJSONObject(n);
            if (item != null && id.equals(item.optString("id")))
              try {
                item.put("protected", checked);
              } catch (Exception ignored) {
              }
          }
          a.save("collection", all);
        });
    content.addView(
        label(
            "Traits: " + (o.optString("traits").isEmpty() ? "Unknown" : o.optString("traits")),
            15,
            muted,
            false));
    content.addView(
        label(
            "Reference artwork does not confirm shiny, costume or background status.",
            12,
            muted,
            false));
  }

  void section(LinearLayout parent, String title) {
    TextView t = label(title, 12, 0xff607d8b, true);
    t.setPadding(0, dp(20), 0, dp(10));
    parent.addView(t);
  }

  void remove(String id) {
    confirmRemove(id, false);
  }

  void confirmRemove(String id, boolean stayList) {
    JSONObject record = store.get(id);
    String name = record == null ? "this Pokémon" : record.optString("name", "this Pokémon");
    new AlertDialog.Builder(a)
        .setTitle("Remove " + name + "?")
        .setMessage(
            "This deletes the collection row only. Learned image memory stays so recognition can"
                + " still improve.")
        .setNegativeButton("Cancel", null)
        .setPositiveButton(
            "Remove",
            (d, w) -> {
              JSONArray all = store.all();
              for (int i = all.length() - 1; i >= 0; i--) {
                JSONObject o = all.optJSONObject(i);
                if (o != null && id.equals(o.optString("id"))) all.remove(i);
              }
              a.save("collection", all);
              if (stayList) refresh();
              else show();
            })
        .show();
  }

  final class Ring extends View {
    final double value;
    final Paint paint = new Paint(3);

    Ring(double value, int size) {
      super(a);
      this.value = value;
      setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
      setContentDescription(value < 0 ? "IV unknown" : Math.round(value) + " percent overall IV");
    }

    protected void onDraw(Canvas c) {
      super.onDraw(c);
      float size = Math.min(getWidth(), getHeight()), stroke = size * .12f;
      RectF oval = new RectF(stroke, stroke, getWidth() - stroke, getHeight() - stroke);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(stroke);
      paint.setColor(CoachUi.BORDER);
      c.drawOval(oval, paint);
      paint.setColor(green);
      if (value >= 0) c.drawArc(oval, -90, (float) (value * 3.6), false, paint);
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(ink);
      paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
      paint.setTextSize(size * .25f);
      paint.setTextAlign(Paint.Align.CENTER);
      String text = value < 0 ? "—" : Math.round(value) + "%";
      c.drawText(
          text, getWidth() / 2f, getHeight() / 2f - (paint.ascent() + paint.descent()) / 2, paint);
    }
  }
}
