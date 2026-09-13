package com.david.gocoach;

import android.app.AlertDialog;

public final class LearningMemoryPage {
  final MainActivity activity;

  public LearningMemoryPage(MainActivity activity) {
    this.activity = activity;
  }

  public void show() {
    activity.page("Learning memory");
    activity.button("Back", activity::home);
    activity.text("Loading remembered Pokémon…", 17);
    final android.widget.LinearLayout loadingPage = activity.body;
    new Thread(
            () -> {
              LearningBank bank = LearningStore.get(activity);
              java.util.List<LearningBank.Entry> entries = bank.entries();
              String problem = bank.problem();
              activity.runOnUiThread(
                  () -> {
                    if (activity.isFinishing()
                        || activity.isDestroyed()
                        || activity.body != loadingPage) return;
                    activity.page("Learning memory");
                    activity.button("Back", activity::home);
                    activity.text(
                        "Names, encounter pictures and map examples are saved on this phone. Visits"
                            + " count confirmed encounter sessions, not catches or unique Pokémon.",
                        16);
                    if (!problem.isEmpty()) {
                      activity.text(problem, 17);
                      return;
                    }
                    activity.button(
                        "Delete all Wild Map learning",
                        () ->
                            new AlertDialog.Builder(activity)
                                .setTitle("Delete all Wild Map learning?")
                                .setMessage(
                                    "This removes every saved Wild Map example. Encounter visits,"
                                        + " encounter pictures and collection records stay saved.")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton(
                                    "Delete Wild Map learning",
                                    (d, w) ->
                                        new Thread(
                                                () -> {
                                                  boolean ok = bank.forgetAllMap();
                                                  activity.runOnUiThread(
                                                      () -> {
                                                        if (!activity.isFinishing()) {
                                                          if (ok) show();
                                                          else
                                                            activity.text(
                                                                "Could not save that change.", 17);
                                                        }
                                                      });
                                                },
                                                "GO-Coach-delete-wild-map")
                                            .start())
                                .show());
                    if (entries.isEmpty()) {
                      activity.text(
                          "Open a wild Pokémon and keep its name visible for two readings. CP is"
                              + " optional. Clear encounter pictures are saved automatically.",
                          17);
                      return;
                    }
                    for (LearningBank.Entry entry : entries) {
                      activity.text(
                          entry.name
                              + " · "
                              + entry.visits
                              + " confirmed visits\n"
                              + entry.portraits
                              + " encounter pictures · "
                              + entry.mapViews
                              + " map examples\nLast CP "
                              + entry.cp
                              + " · "
                              + new java.util.Date(entry.seen),
                          17);
                      if (entry.mapViews > 0)
                        activity.button(
                            "Delete Wild Map examples: " + entry.name,
                            () ->
                                new AlertDialog.Builder(activity)
                                    .setTitle("Delete " + entry.name + " Wild Map examples?")
                                    .setMessage(
                                        "This removes only this species’ Wild Map examples. Its"
                                            + " name, encounter pictures and collection records"
                                            + " stay saved.")
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton(
                                        "Delete examples",
                                        (d, w) ->
                                            new Thread(
                                                    () -> {
                                                      boolean ok = bank.forgetMap(entry.name);
                                                      activity.runOnUiThread(
                                                          () -> {
                                                            if (!activity.isFinishing()) {
                                                              if (ok) show();
                                                              else
                                                                activity.text(
                                                                    "Could not save that change.",
                                                                    17);
                                                            }
                                                          });
                                                    },
                                                    "GO-Coach-delete-wild-map-species")
                                                .start())
                                    .show());
                    }
                  });
            },
            "GO-Coach-memory")
        .start();
  }
}
