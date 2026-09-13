package com.david.gocoach;

/** A loading screen or map label must not enter the expensive detail parser. */
public final class ScreenKind {
  private ScreenKind() {}

  public static boolean hasPokemonDetails(Reading reading) {
    return !"Unknown".equals(reading.cp)
        || reading.current >= 0
        || reading.maximum > 0
        || !"Unknown".equals(reading.species);
  }
}
