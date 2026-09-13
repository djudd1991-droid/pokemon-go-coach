package com.david.gocoach;

import android.content.res.AssetManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Reads numbered asset pieces as one stream without allocating the complete atlas. */
final class AssetStreams {
  static InputStream atlas(AssetManager assets) throws IOException {
    String[] parts = assets.list("visual-atlas");
    if (parts == null || parts.length == 0) return assets.open("visual-atlas.bin");
    Arrays.sort(parts);
    List<InputStream> streams = new ArrayList<>();
    try {
      for (String part : parts) {
        if (part.matches("part-[0-9]{3}\\.bin")) streams.add(assets.open("visual-atlas/" + part));
      }
      if (streams.isEmpty()) throw new IOException("Missing atlas pieces");
      return new SequenceInputStream(Collections.enumeration(streams));
    } catch (IOException e) {
      for (InputStream stream : streams)
        try {
          stream.close();
        } catch (IOException ignored) {
        }
      throw e;
    }
  }
}
