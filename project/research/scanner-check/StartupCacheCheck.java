package com.david.gocoach;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Each mode runs in a fresh JVM: the production cache deliberately has no reset/reload API. */
public final class StartupCacheCheck {
  static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  public static void main(String[] args) throws Exception {
    String mode = args[0];
    try {
      WildMapStartupCache.maxResults();
      throw new AssertionError("A getter must never lazily initialize configuration");
    } catch (IllegalStateException expected) {
    }
    AtomicInteger opens = new AtomicInteger(), closes = new AtomicInteger();
    WildMapStartupCache.Source source =
        () -> {
          opens.incrementAndGet();
          if (mode.equals("missing")) throw new IOException("Missing test asset");
          String data =
              mode.equals("invalid")
                  ? "not json"
                  : "{\"max_results\":99,\"version\":4,\"scanner\":\"test\"}";
          return new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public void close() throws IOException {
              closes.incrementAndGet();
              super.close();
            }
          };
        };
    CountDownLatch start = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    List<Thread> workers = new ArrayList<>();
    int expected = mode.equals("valid") ? 24 : 16;
    for (int i = 0; i < 12; i++) {
      Thread worker =
          new Thread(
              () -> {
                try {
                  start.await();
                  WildMapStartupCache.initialize(source);
                  for (int j = 0; j < 10000; j++)
                    require(
                        WildMapStartupCache.maxResults() == expected,
                        "Unpublished or changed data");
                } catch (Throwable e) {
                  failure.compareAndSet(null, e);
                }
              });
      workers.add(worker);
      worker.start();
    }
    start.countDown();
    for (Thread worker : workers) worker.join();
    if (failure.get() != null)
      throw new AssertionError("Concurrent startup/read failed", failure.get());
    require(opens.get() == 1, "Startup must open the asset exactly once, including failure");
    require(closes.get() == (mode.equals("missing") ? 0 : 1), "Startup must close its stream");
    WildMapStartupCache.initialize(
        () -> {
          throw new AssertionError("Scanner restart must not reopen assets");
        });
    require(WildMapStartupCache.integer("absent", 7) == 7, "Missing key fallback");
    if (mode.equals("valid")) {
      require(WildMapStartupCache.text("scanner", "").equals("test"), "Direct string lookup");
      require(WildMapStartupCache.integer("version", 0) == 4, "Direct numeric lookup");
      require(WildMapStartupCache.problem().isEmpty(), "Valid configuration should load");
    } else
      require(!WildMapStartupCache.problem().isEmpty(), "Failure should be visible at startup");
    System.out.println(
        "PASS: startup cache "
            + mode
            + ", exactly one open, 120000 concurrent memory lookups, no reload");
  }
}
