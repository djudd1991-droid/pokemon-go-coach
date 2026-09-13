package com.david.gocoach;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.widget.*;

/** Shared spacing, colors and controls for the native app and floating coach. */
final class CoachUi {
  static final int BACKGROUND = 0xff0b1420, SURFACE = 0xff142337, BORDER = 0xff294058;
  static final int TEXT = 0xfff1f6fb, MUTED = 0xffaabbd0, ACCENT = 0xff6ce5bf;

  private CoachUi() {}

  static int dp(Context c, int value) {
    return Math.round(value * c.getResources().getDisplayMetrics().density);
  }

  static GradientDrawable shape(Context c, int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(c, radius));
    return d;
  }

  static TextView label(Context c, String text, int size, int color, boolean bold) {
    TextView v = new TextView(c);
    v.setText(text);
    v.setTextSize(size);
    v.setTextColor(color);
    if (bold) v.setTypeface(null, Typeface.BOLD);
    v.setFontFeatureSettings("kern");
    return v;
  }

  static Button button(Context c, String text, boolean primary, Runnable action) {
    Button b = new Button(c);
    b.setText(text);
    b.setAllCaps(false);
    b.setTextSize(16);
    b.setTextColor(primary ? BACKGROUND : TEXT);
    b.setTypeface(null, Typeface.BOLD);
    b.setBackgroundTintList(null);
    b.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(0x306ce5bf), shape(c, primary ? ACCENT : SURFACE, 16), null));
    b.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10));
    b.setMinHeight(dp(c, 52));
    b.setOnClickListener(v -> action.run());
    return b;
  }

  static LinearLayout card(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setOrientation(LinearLayout.VERTICAL);
    l.setPadding(dp(c, 20), dp(c, 18), dp(c, 20), dp(c, 18));
    l.setBackground(shape(c, SURFACE, 20));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(0, dp(c, 8), 0, dp(c, 8));
    l.setLayoutParams(p);
    return l;
  }
}
