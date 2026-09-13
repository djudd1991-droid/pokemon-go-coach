package com.david.gocoach;

import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.*;

/** The landing page is separate from capture permissions and record editing. */
final class HomeScreen {
  static void show(MainActivity a) {
    a.page("GO Coach");
    LinearLayout brand = new LinearLayout(a);
    brand.setGravity(Gravity.CENTER_VERTICAL);
    ImageView icon = new ImageView(a);
    icon.setImageResource(R.drawable.coach_logo);
    icon.setContentDescription("GO Coach compass");
    brand.addView(icon, new LinearLayout.LayoutParams(a.dp(56), a.dp(56)));
    TextView intro = CoachUi.label(a, "Your next move, made clearer.", 19, CoachUi.TEXT, true);
    intro.setPadding(a.dp(16), 0, 0, 0);
    brand.addView(intro, new LinearLayout.LayoutParams(0, -2, 1));
    a.body.addView(brand);
    a.text("A quiet companion for your Pokémon GO adventures.", 15);
    LinearLayout card = CoachUi.card(a);
    a.body.addView(card);
    card.addView(CoachUi.label(a, "READY WHEN YOU ARE", 12, CoachUi.ACCENT, true));
    TextView help =
        CoachUi.label(
            a,
            "Start the coach, share Pokémon GO, and play. Tap the floating card for controls.",
            17,
            CoachUi.TEXT,
            false);
    help.setPadding(0, a.dp(12), 0, a.dp(16));
    card.addView(help);
    card.addView(
        CoachUi.button(a, "Start coaching", true, a::startReader),
        new LinearLayout.LayoutParams(-1, -2));
    if (!Settings.canDrawOverlays(a))
      a.button(
          "Allow floating coach",
          () ->
              a.startActivity(
                  new Intent(
                      Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                      Uri.parse("package:" + a.getPackageName()))));
    a.text("YOUR NOTEBOOK", 12).setTextColor(CoachUi.ACCENT);
    a.button("My Pokémon  ›", a::collection);
    a.button("Learning memory  ›", () -> new LearningMemoryPage(a).show());
    a.button("Choose my coaching goal  ›", a::chooseCoachGoal);
    a.button("Saved readings  ›", a::scans);
    a.text("HOW IT LEARNS", 12).setTextColor(CoachUi.ACCENT);
    a.text(
        "Open a Pokémon and keep its name visible. Two matching readings confirm the name; CP is"
            + " optional. Clear encounter pictures are saved automatically.",
        15);
    a.text(
        "Map labels stay Unknown until there is enough evidence. Crowded maps may need an optional"
            + " map correction. Collection advice uses the details you have saved.",
        15);
    a.button(
        "Stop coaching",
        () -> {
          a.stopService(new Intent(a, CoachService.class));
          Toast.makeText(a, "Coach stopped", Toast.LENGTH_SHORT).show();
        });
    a.text(
        "GO Coach · "
            + BuildConfig.VERSION_NAME
            + "\n"
            + "Map • Encounters • Collection\n"
            + "Events, bag tracking and raid coaching are still planned.",
        12);
  }
}
