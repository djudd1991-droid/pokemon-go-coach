package com.david.gocoach;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.util.*;
import org.json.*;
import org.opencv.android.*;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.*;
import org.opencv.features2d.*;
import org.opencv.imgproc.Imgproc;

/**
 * Experimental reference matching for a single details-screen Pokemon; no text is used as identity
 * evidence.
 */
public final class VisualMatcher implements AutoCloseable {
  static final class Reference {
    String species;
    Point[] points;
    Mat descriptors;
    boolean priority;
    long learnedAt;
  }

  final List<Reference> references = new ArrayList<>();
  SIFT sift;
  BFMatcher matcher;
  DescriptorMatcher index;
  final List<Mat> indexSamples = new ArrayList<>();
  final Context context;

  public VisualMatcher(Context context) throws Exception {
    this.context = context.getApplicationContext();
    if (!OpenCVLoader.initLocal()) throw new IllegalStateException("OpenCV could not load");
    Core.setNumThreads(1);
    sift = SIFT.create(2000, 3, 0.015, 10, 1.6);
    matcher = BFMatcher.create(Core.NORM_L2, false);
    index = DescriptorMatcher.create(DescriptorMatcher.FLANNBASED);
    try (java.io.DataInputStream in =
        new java.io.DataInputStream(
            new java.io.BufferedInputStream(AssetStreams.atlas(context.getAssets())))) {
      if (in.readInt() != 1) throw new java.io.IOException("Unsupported atlas");
      int count = in.readInt();
      if (count < 1 || count > 4000) throw new java.io.IOException("Invalid atlas count");
      for (int i = 0; i < count; i++) {
        int length = in.readInt();
        if (length < 1 || length > 200) throw new java.io.IOException("Invalid name");
        byte[] name = new byte[length];
        in.readFully(name);
        Reference ref = new Reference();
        ref.species = new String(name, java.nio.charset.StandardCharsets.UTF_8);
        ref.priority = in.readInt() == 1;
        int rows = in.readInt();
        if (rows < 4 || rows > 4000) throw new java.io.IOException("Invalid features");
        ref.points = new Point[rows];
        for (int j = 0; j < rows; j++) ref.points[j] = new Point(in.readFloat(), in.readFloat());
        byte[] bytes = new byte[rows * 128];
        in.readFully(bytes);
        ref.descriptors = new Mat(rows, 128, CvType.CV_8U);
        ref.descriptors.put(0, 0, bytes);
        references.add(ref);
        float[] sampled = new float[((rows + 3) / 4) * 128];
        for (int j = 0; j < rows; j += 4)
          for (int k = 0; k < 128; k++) sampled[(j / 4) * 128 + k] = bytes[j * 128 + k] & 255;
        Mat sample = new Mat((rows + 3) / 4, 128, CvType.CV_32F);
        sample.put(0, 0, sampled);
        indexSamples.add(sample);
      }
    }
    loadLearned();
    rebuildIndex();
  }

  public String identify(Bitmap bitmap) {
    return identify(bitmap, false);
  }

  public String identify(Bitmap bitmap, boolean encounter) {
    Rect region;
    if (encounter)
      region =
          new Rect(
              (int) (bitmap.getWidth() * .08),
              (int) (bitmap.getHeight() * .22),
              (int) (bitmap.getWidth() * .80),
              (int) (bitmap.getHeight() * .43));
    else
      region =
          new Rect(
              (int) (bitmap.getWidth() * .08),
              (int) (bitmap.getHeight() * .10),
              (int) (bitmap.getWidth() * .80),
              (int) (bitmap.getHeight() * .245));
    return identifyRegion(bitmap, region, 10, 4, .015);
  }

  String identifyRegion(Bitmap bitmap, Rect region, int minInliers, int lead, double minCoverage) {
    Mat rgba = new Mat(), gray = new Mat(), desc = new Mat(), empty = new Mat();
    MatOfKeyPoint keypoints = new MatOfKeyPoint();
    Mat roi = null;
    try {
      Utils.bitmapToMat(bitmap, rgba);
      int w = rgba.cols(), h = rgba.rows();
      int x = Math.max(0, Math.min(w - 1, region.x)),
          y = Math.max(0, Math.min(h - 1, region.y)),
          rw = Math.max(1, Math.min(w - x, region.width)),
          rh = Math.max(1, Math.min(h - y, region.height));
      roi = rgba.submat(new Rect(x, y, rw, rh));
      Imgproc.cvtColor(roi, gray, Imgproc.COLOR_RGBA2GRAY);
      sift.detectAndCompute(gray, empty, keypoints, desc);
      if (desc.rows() < 2) return "";
      KeyPoint[] target = keypoints.toArray();
      Map<String, Integer> scores = new HashMap<>();
      Map<String, Double> coverage = new HashMap<>();
      Map<Integer, Integer> votes = new HashMap<>();
      List<MatOfDMatch> nearby = new ArrayList<>();
      try {
        index.knnMatch(desc, nearby, 2);
        for (MatOfDMatch pair : nearby) {
          DMatch[] m = pair.toArray();
          if (m.length == 2 && m[0].distance < .8 * m[1].distance)
            votes.put(m[0].imgIdx, votes.getOrDefault(m[0].imgIdx, 0) + 1);
        }
      } finally {
        for (MatOfDMatch pair : nearby) pair.release();
      }
      List<Integer> shortlist = new ArrayList<>(votes.keySet());
      shortlist.sort((a, b) -> Integer.compare(votes.get(b), votes.get(a)));
      Set<Integer> candidates = new HashSet<>(shortlist.subList(0, Math.min(16, shortlist.size())));
      for (int i = 0; i < references.size(); i++) if (references.get(i).priority) candidates.add(i);
      for (int refId : candidates) {
        Reference ref = references.get(refId);
        List<MatOfDMatch> pairs = new ArrayList<>();
        Map<Integer, DMatch> unique = new HashMap<>();
        Mat expanded = new Mat();
        ref.descriptors.convertTo(expanded, CvType.CV_32F);
        try {
          matcher.knnMatch(expanded, desc, pairs, 2);
          for (MatOfDMatch pair : pairs) {
            DMatch[] a = pair.toArray();
            if (a.length == 2 && a[0].distance < .70 * a[1].distance) {
              DMatch old = unique.get(a[0].trainIdx);
              if (old == null || old.distance > a[0].distance) unique.put(a[0].trainIdx, a[0]);
            }
          }
        } finally {
          expanded.release();
          for (MatOfDMatch pair : pairs) pair.release();
        }
        if (unique.size() < 4) continue;
        List<DMatch> matches = new ArrayList<>(unique.values());
        matches.sort(Comparator.comparingDouble(m -> m.distance));
        Point[] from = new Point[matches.size()], to = new Point[matches.size()];
        for (int j = 0; j < matches.size(); j++) {
          DMatch m = matches.get(j);
          from[j] = ref.points[m.queryIdx];
          to[j] = target[m.trainIdx].pt;
        }
        MatOfPoint2f a = new MatOfPoint2f(from), b = new MatOfPoint2f(to);
        Mat mask = new Mat(), H = null;
        MatOfPoint poly = null;
        MatOfInt hull = null;
        MatOfPoint outline = null;
        try {
          H = Calib3d.findHomography(a, b, Calib3d.RANSAC, 4.0, mask);
          if (H.empty() || mask.empty()) continue;
          List<Point> points = new ArrayList<>();
          for (int j = 0; j < to.length; j++) if (mask.get(j, 0)[0] != 0) points.add(to[j]);
          int count = points.size();
          double area = 0;
          if (count >= 3) {
            poly = new MatOfPoint();
            poly.fromList(points);
            hull = new MatOfInt();
            Imgproc.convexHull(poly, hull);
            int[] indices = hull.toArray();
            Point[] hp = new Point[indices.length];
            for (int j = 0; j < indices.length; j++) hp[j] = points.get(indices[j]);
            outline = new MatOfPoint(hp);
            area = Imgproc.contourArea(outline) / (gray.rows() * gray.cols());
          }
          if (count > scores.getOrDefault(ref.species, 0)) {
            scores.put(ref.species, count);
            coverage.put(ref.species, area);
          }
        } finally {
          a.release();
          b.release();
          mask.release();
          if (H != null) H.release();
          if (poly != null) poly.release();
          if (hull != null) hull.release();
          if (outline != null) outline.release();
        }
      }
      String best = "";
      int high = 0, second = 0;
      for (Map.Entry<String, Integer> e : scores.entrySet()) {
        if (e.getValue() > high) {
          second = high;
          high = e.getValue();
          best = e.getKey();
        } else second = Math.max(second, e.getValue());
      }
      return high >= minInliers
              && high >= second + lead
              && coverage.getOrDefault(best, 0.0) >= minCoverage
          ? best
          : "";
    } finally {
      if (roi != null) roi.release();
      rgba.release();
      gray.release();
      desc.release();
      empty.release();
      keypoints.release();
    }
  }

  public synchronized boolean learn(String species, Bitmap bitmap, boolean encounter) {
    if (species == null || species.equals("Unknown") || bitmap == null) return false;
    Bitmap crop = null;
    try {
      crop = crop(bitmap, encounter);
      if (!addBitmap(species, crop, true)) return false;
      saveLearned(species, crop);
      trimLearned(species);
      trimLearnedReferences();
      rebuildIndex();
      return true;
    } catch (Exception e) {
      return false;
    } finally {
      if (crop != null) crop.recycle();
    }
  }

  void loadLearned() {
    java.io.File dir = new java.io.File(context.getFilesDir(), "learned-pokemon");
    java.io.File[] files = dir.listFiles();
    if (files == null) return;
    java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
    int loaded = 0;
    for (java.io.File file : files) {
      if (loaded >= 250) break;
      String name = file.getName();
      if (!name.endsWith(".png")) continue;
      int cut = name.indexOf("__");
      if (cut <= 0) continue;
      String species = name.substring(0, cut).replace('_', ' ');
      Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
      if (bitmap == null) continue;
      try {
        if (addBitmap(species, bitmap, true)) {
          references.get(references.size() - 1).learnedAt = file.lastModified();
          loaded++;
        }
      } finally {
        bitmap.recycle();
      }
    }
  }

  Bitmap crop(Bitmap bitmap, boolean encounter) {
    int w = bitmap.getWidth(), h = bitmap.getHeight();
    double top = encounter ? .22 : .10, bottom = encounter ? .65 : .345;
    int left = (int) (w * .08),
        right = (int) (w * .88),
        y = (int) (h * top),
        height = (int) (h * bottom) - y;
    left = Math.max(0, Math.min(w - 1, left));
    right = Math.max(left + 1, Math.min(w, right));
    y = Math.max(0, Math.min(h - 1, y));
    height = Math.max(1, Math.min(h - y, height));
    return Bitmap.createBitmap(bitmap, left, y, right - left, height);
  }

  boolean addBitmap(String species, Bitmap bitmap, boolean priority) {
    Mat rgba = new Mat(), gray = new Mat(), desc = new Mat(), empty = new Mat();
    MatOfKeyPoint keypoints = new MatOfKeyPoint();
    try {
      Utils.bitmapToMat(bitmap, rgba);
      Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY);
      sift.detectAndCompute(gray, empty, keypoints, desc);
      if (desc.rows() < 8) return false;
      Reference ref = new Reference();
      ref.species = species;
      ref.priority = priority;
      ref.learnedAt = System.currentTimeMillis();
      KeyPoint[] kp = keypoints.toArray();
      ref.points = new Point[kp.length];
      for (int i = 0; i < kp.length; i++) ref.points[i] = kp[i].pt;
      ref.descriptors = new Mat();
      desc.convertTo(ref.descriptors, CvType.CV_8U);
      references.add(ref);
      indexSamples.add(sample(ref.descriptors));
      return true;
    } finally {
      rgba.release();
      gray.release();
      desc.release();
      empty.release();
      keypoints.release();
    }
  }

  Mat sample(Mat descriptors) {
    byte[] bytes = new byte[(int) (descriptors.total() * descriptors.channels())];
    descriptors.get(0, 0, bytes);
    int rows = descriptors.rows();
    float[] sampled = new float[((rows + 3) / 4) * 128];
    for (int j = 0; j < rows; j += 4)
      for (int k = 0; k < 128; k++) sampled[(j / 4) * 128 + k] = bytes[j * 128 + k] & 255;
    Mat sample = new Mat((rows + 3) / 4, 128, CvType.CV_32F);
    sample.put(0, 0, sampled);
    return sample;
  }

  /** Match the existing disk limits in RAM during long coaching sessions. */
  void trimLearnedReferences() {
    List<Reference> learned = new ArrayList<>();
    for (Reference ref : references) if (ref.learnedAt > 0) learned.add(ref);
    learned.sort((a, b) -> Long.compare(b.learnedAt, a.learnedAt));
    Map<String, Integer> perSpecies = new HashMap<>();
    Set<Reference> keep = new HashSet<>();
    for (Reference ref : learned) {
      int count = perSpecies.getOrDefault(ref.species, 0);
      if (keep.size() < 250 && count < 8) {
        keep.add(ref);
        perSpecies.put(ref.species, count + 1);
      }
    }
    for (int i = references.size() - 1; i >= 0; i--) {
      Reference ref = references.get(i);
      if (ref.learnedAt > 0 && !keep.contains(ref)) {
        references.remove(i).descriptors.release();
        indexSamples.remove(i).release();
      }
    }
  }

  void rebuildIndex() {
    if (index != null) index.clear();
    index = DescriptorMatcher.create(DescriptorMatcher.FLANNBASED);
    if (!indexSamples.isEmpty()) {
      index.add(indexSamples);
      index.train();
    }
  }

  void saveLearned(String species, Bitmap crop) throws java.io.IOException {
    java.io.File dir = new java.io.File(context.getFilesDir(), "learned-pokemon");
    if (!dir.exists()) dir.mkdirs();
    String safe = species.replaceAll("[^A-Za-z0-9]+", "_");
    java.io.File file = new java.io.File(dir, safe + "__" + System.currentTimeMillis() + ".png");
    try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
      crop.compress(Bitmap.CompressFormat.PNG, 90, out);
    }
  }

  void trimLearned(String species) {
    java.io.File dir = new java.io.File(context.getFilesDir(), "learned-pokemon");
    java.io.File[] files = dir.listFiles();
    if (files == null) return;
    String prefix = species.replaceAll("[^A-Za-z0-9]+", "_") + "__";
    java.util.List<java.io.File> same = new java.util.ArrayList<>(),
        all = new java.util.ArrayList<>();
    for (java.io.File f : files)
      if (f.getName().endsWith(".png")) {
        all.add(f);
        if (f.getName().startsWith(prefix)) same.add(f);
      }
    java.util.Comparator<java.io.File> newest =
        (a, b) -> Long.compare(b.lastModified(), a.lastModified());
    same.sort(newest);
    all.sort(newest);
    for (int i = 8; i < same.size(); i++) same.get(i).delete();
    for (int i = 250; i < all.size(); i++) all.get(i).delete();
  }

  public void close() {
    for (Reference ref : references) ref.descriptors.release();
    references.clear();
    if (index != null) index.clear();
    for (Mat m : indexSamples) m.release();
    indexSamples.clear();
    if (sift != null) sift.clear();
    if (matcher != null) matcher.clear();
  }
}
