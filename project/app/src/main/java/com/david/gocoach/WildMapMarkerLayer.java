package com.david.gocoach;

import android.content.Context;
import android.graphics.*;
import android.view.View;

public final class WildMapMarkerLayer extends View {
  final Paint paint = new Paint(3);
  final java.util.List<WildMapMatch> markers = new java.util.ArrayList<>();

  public WildMapMarkerLayer(Context c) {
    super(c);
    setWillNotDraw(false);
  }

  public void setMarkers(java.util.List<WildMapMatch> next) {
    markers.clear();
    if (next != null) markers.addAll(next);
    postInvalidate();
  }

  protected void onDraw(Canvas canvas) {
    int w = getWidth(), h = getHeight();
    paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    paint.setTextAlign(Paint.Align.CENTER);
    for (WildMapMatch m : markers) {
      float x = w * m.xPercent / 100f, y = h * m.yPercent / 100f;
      String label = m.known ? m.name : "Unknown";
      int color = m.known ? Color.rgb(100, 235, 190) : Color.rgb(255, 213, 79);
      float d = getResources().getDisplayMetrics().density;
      paint.setTextSize(12 * d);
      float tw = paint.measureText(label),
          lx = Math.max(tw / 2 + 5 * d, Math.min(w - tw / 2 - 5 * d, x));
      float bottom = y - 19 * d, top = bottom - 18 * d;
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(Color.argb(205, 18, 30, 44));
      canvas.drawRoundRect(
          new RectF(lx - tw / 2 - 5 * d, top, lx + tw / 2 + 5 * d, bottom), 5 * d, 5 * d, paint);
      paint.setColor(color);
      canvas.drawText(label, lx, bottom - 4 * d, paint);
      paint.setStrokeWidth(2 * d);
      canvas.drawLine(lx, bottom, x, y - 8 * d, paint);
      Path arrow = new Path();
      arrow.moveTo(x, y);
      arrow.lineTo(x - 4 * d, y - 9 * d);
      arrow.lineTo(x + 4 * d, y - 9 * d);
      arrow.close();
      canvas.drawPath(arrow, paint);
    }
  }
}
