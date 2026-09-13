package com.david.gocoach;

/** Advice uses confirmed context and explicit goals, never assumed inventory or catches. */
public final class CoachAdvice {
  private CoachAdvice() {}

  public static String encounter(String name, String goal, LearningBank.Entry memory) {
    String history =
        memory == null
            ? "Confirming the name for memory."
            : "Remembered "
                + name
                + " · "
                + memory.visits
                + " confirmed encounter visit"
                + (memory.visits == 1 ? "" : "s")
                + " · "
                + memory.mapViews
                + " map view"
                + (memory.mapViews == 1 ? "" : "s")
                + ".\n";
    String next =
        "candy".equals(goal)
            ? "Next: If you want its Candy and have a Pinap Berry, use one before catching."
            : "pvp".equals(goal)
                ? "Next: Catch it, then open Appraise. CP alone cannot tell me whether this"
                    + " individual is a good PvP choice."
                : "Next: Catch it to build your collection. Afterward, open Appraise so I can"
                    + " remember its individual stats.";
    return history + "\n" + next;
  }

  public static String map(String goal, int unknown, String names, int stops) {
    if (!names.isEmpty())
      return "Looks like " + names + " from your saved map examples. Tap to confirm the name.";
    if (unknown > 0)
      return "Tap an Unknown to read its name. Then Teach map links its map picture to that name.";
    if (stops > 0)
      return "If you need supplies, check a nearby PokéStop. I cannot tell from this view whether"
          + " you can spin it.";
    return "Hold the map still briefly so I can confirm candidates.";
  }
}
