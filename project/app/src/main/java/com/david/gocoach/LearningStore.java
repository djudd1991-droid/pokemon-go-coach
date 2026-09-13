package com.david.gocoach;

import android.content.Context;

public final class LearningStore {
  private static LearningBank bank;

  private LearningStore() {}

  /** Call on a worker; one process-wide instance prevents competing writes. */
  public static synchronized LearningBank get(Context context) {
    if (bank == null)
      bank = new LearningBank(new java.io.File(context.getFilesDir(), "coach-learning-v1.bin"));
    return bank;
  }
}
