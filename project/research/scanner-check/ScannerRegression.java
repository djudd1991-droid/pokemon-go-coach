package com.david.gocoach;

import java.io.*;
import java.util.*;

public class ScannerRegression {
  static void require(boolean b, String message) {
    if (!b) throw new AssertionError(message);
  }

  static WildMapDetector.Spot spot(int x, int y) {
    return new WildMapDetector.Spot(
        new WildMapDetector.Box(x - 5, y - 5, x + 5, y + 5, 70), false, x, y, 10, false);
  }

  static void menu(int[] p, int w, int h) {
    for (int y = (int) (h * .91); y < (int) (h * .96); y++)
      for (int x = (int) (w * .45); x < (int) (w * .55); x++)
        p[y * w + x] = y < h * .935 ? 0xffef3030 : 0xffeeeeee;
  }

  public static void main(String[] args) throws Exception {
    int w = 230, h = 512;
    int[] p = new int[w * h];
    Arrays.fill(p, 0xff64b991);
    require(!WildMapDetector.isMap(p, w, h, null), "Blank green screen should not be a map");
    for (int y = 0; y < h; y++)
      for (int x = 0; x < w; x++) {
        int d = Math.abs(y - 2 * x - 20);
        if (d < 18) p[y * w + x] = d > 13 ? 0xfffac882 : 0xff648791;
      }
    menu(p, w, h);
    require(WildMapDetector.isMap(p, w, h, null), "Road fixture should route as map");
    require(
        WildMapDetector.detect(p, w, h, null, 16).isEmpty(), "Road-only frame produced an object");
    int[] night = new int[w * h];
    Arrays.fill(night, 0xff385b92);
    for (int y = 0; y < h; y++)
      for (int x = 0; x < w; x++) {
        int d = Math.abs(y - 2 * x - 20);
        if (d < 18) night[y * w + x] = 0xffa85c77;
      }
    menu(night, w, h);
    require(
        WildMapDetector.isMap(night, w, h, null), "Night map with pink roads should route as map");
    require(
        !WildMapDetector.isMap(new int[w * h], w, h, null),
        "Blank dark screen should not be a night map");
    int[] catchScreen = new int[w * h];
    Arrays.fill(catchScreen, 0xff385b92);
    for (int y = 300; y < 380; y++)
      for (int x = 70; x < 160; x++) catchScreen[y * w + x] = 0xffef3030;
    require(
        !WildMapDetector.isMap(catchScreen, w, h, null),
        "Blue catch screen and vivid Poké Ball must not route as map");
    int blue = 0xff2874e8;
    for (int y = 260; y < 286; y++) for (int x = 100; x < 126; x++) p[y * w + x] = blue;
    java.util.List<WildMapDetector.Spot> blueCandidates = WildMapDetector.detect(p, w, h, null, 16);
    require(
        !blueCandidates.isEmpty(), "Compact colored map spawn should remain an Unknown candidate");
    for (WildMapDetector.Spot candidate : blueCandidates)
      require(!candidate.stop, "Blue Pokémon/map art must not be marked as a PokéStop");
    int[] stops = new int[w * h];
    Arrays.fill(stops, 0xff385b92);
    for (int y = 260; y < 270; y++) for (int x = 64; x < 80; x++) stops[y * w + x] = 0xff28ccf0;
    for (int y = 270; y < 290; y++) for (int x = 70; x < 74; x++) stops[y * w + x] = 0xff28ccf0;
    require(
        WildMapDetector.detect(stops, w, h, null, 16).stream().anyMatch(s -> s.stop),
        "Cyan head and narrow stem should remain a stop");
    for (int y = 260; y < 290; y++) for (int x = 150; x < 164; x++) stops[y * w + x] = 0xff28ccf0;
    require(
        WildMapDetector.detect(stops, w, h, null, 16).stream().noneMatch(s -> s.x > 140 && s.stop),
        "Tall cyan body without a narrow stem is not a stop");
    require(
        WildMapDetector.isBuddyOrProfilePortrait((int) (w * .12), (int) (h * .90), w, h),
        "Buddy/profile portrait must be excluded");
    require(
        !WildMapDetector.isBuddyOrProfilePortrait((int) (w * .30), (int) (h * .90), w, h),
        "Buddy/profile exclusion must not hide adjacent map objects");
    WildMapTracker tracker = new WildMapTracker();
    require(
        tracker.update(Arrays.asList(spot(90, 300)), w, h, 1000).isEmpty(),
        "First frame should be unconfirmed");
    require(
        tracker.update(Arrays.asList(spot(92, 302)), w, h, 2000).size() == 1,
        "Small movement should confirm at current position");
    require(
        tracker.update(Collections.emptyList(), w, h, 3000).isEmpty(),
        "Disappeared object must clear immediately");
    require(
        tracker.update(Arrays.asList(spot(92, 302)), w, h, 4000).isEmpty(),
        "Reappearing object needs confirmation");
    require(
        tracker.update(Arrays.asList(spot(150, 350)), w, h, 5000).isEmpty(),
        "Camera jump must not retain an old marker");
    require(
        tracker.update(Arrays.asList(spot(150, 350)), w, h, 6000).size() == 1,
        "Stable position must confirm");
    require(
        tracker.update(Arrays.asList(spot(150, 350)), w, h, 10000).isEmpty(),
        "Capture gap must reset confirmation");
    tracker.clear();
    require(
        tracker.update(Arrays.asList(spot(150, 350)), w, h, 11000).isEmpty(),
        "Pause reset must clear history");
    System.out.println(
        "PASS: road rejection, two-frame confirmation, current positions, disappearance, camera"
            + " jump, capture-gap and pause resets");
  }
}
