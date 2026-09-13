package com.david.gocoach;

public final class WildMapMatch {
  public final String name;
  public final float xPercent, yPercent, widthPercent, heightPercent;
  public final boolean known, stop;

  WildMapMatch(String name, float xPercent, float yPercent, boolean known) {
    this(name, xPercent, yPercent, known, "PokéStop".equals(name), 10, 8);
  }

  WildMapMatch(
      String name,
      float xPercent,
      float yPercent,
      boolean known,
      boolean stop,
      float widthPercent,
      float heightPercent) {
    this.name = name;
    this.xPercent = xPercent;
    this.yPercent = yPercent;
    this.known = known;
    this.stop = stop;
    this.widthPercent = widthPercent;
    this.heightPercent = heightPercent;
  }
}
