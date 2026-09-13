package com.david.gocoach;

import android.graphics.*;

public final class LearningImages {
  private LearningImages() {}

  public static Rect mapRect(Bitmap b, WildMapMatch m) {
    int side =
        (int)
            Math.max(
                b.getWidth() * .075,
                Math.max(
                        b.getWidth() * m.widthPercent / 100f,
                        b.getHeight() * m.heightPercent / 100f)
                    * 1.5);
    side = Math.max(16, Math.min(side, (int) (b.getWidth() * .30)));
    int x = (int) (b.getWidth() * m.xPercent / 100f), y = (int) (b.getHeight() * m.yPercent / 100f);
    return clamp(b, new Rect(x - side / 2, y - side / 2, x + side / 2, y + side / 2));
  }

  public static Rect portraitRect(Bitmap b) {
    return new Rect(
        (int) (b.getWidth() * .20),
        (int) (b.getHeight() * .40),
        (int) (b.getWidth() * .80),
        (int) (b.getHeight() * .70));
  }

  public static Rect clamp(Bitmap b, Rect r) {
    return new Rect(
        Math.max(0, r.left),
        Math.max(0, r.top),
        Math.min(b.getWidth(), r.right),
        Math.min(b.getHeight(), r.bottom));
  }

  public static Bitmap crop(Bitmap b, Rect r) {
    r = clamp(b, r);
    return Bitmap.createBitmap(b, r.left, r.top, Math.max(1, r.width()), Math.max(1, r.height()));
  }

  public static int[] pixels(Bitmap b, Rect r) {
    Bitmap cut = crop(b, r),
        small = Bitmap.createScaledBitmap(cut, Appearance.SIZE, Appearance.SIZE, true);
    int[] p = new int[Appearance.SIZE * Appearance.SIZE];
    small.getPixels(p, 0, Appearance.SIZE, 0, 0, Appearance.SIZE, Appearance.SIZE);
    if (small != cut) small.recycle();
    if (cut != b) cut.recycle();
    return p;
  }
}
