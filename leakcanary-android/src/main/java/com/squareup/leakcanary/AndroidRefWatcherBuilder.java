package com.squareup.leakcanary;

import android.app.Application;
import android.content.Context;
import android.os.Looper;
import com.squareup.leakcanary.internal.LeakCanaryInternals;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;

/** A {@link RefWatcherBuilder} with appropriate Android defaults. */
public final class AndroidRefWatcherBuilder extends RefWatcherBuilder<AndroidRefWatcherBuilder> {

  private static final long DEFAULT_WATCH_DELAY_MILLIS = SECONDS.toMillis(5);

  private final Context context;
  private LeakDirectoryProvider leakDirectoryProvider;
  private boolean watchActivities;

  AndroidRefWatcherBuilder(Context context) {
    this.context = context.getApplicationContext();
    this.leakDirectoryProvider = new DefaultLeakDirectoryProvider(context);
    watchActivities = true;
  }

  /**
   * Sets a custom {@link LeakDirectoryProvider}. This overrides any call to {@link
   * #maxStoredHeapDumps(int)}.
   */
  public AndroidRefWatcherBuilder leakDirectoryProvider(
      LeakDirectoryProvider leakDirectoryProvider) {
    this.leakDirectoryProvider = leakDirectoryProvider;
    return this;
  }

  /**
   * Whether we should automatically watch activities (on ICS+). Default is true.
   */
  public AndroidRefWatcherBuilder watchActivities(boolean watchActivities) {
    this.watchActivities = watchActivities;
    return this;
  }

  /**
   * Sets a custom {@link AbstractAnalysisResultService} to listen to analysis results. This
   * overrides any call to {@link #heapDumpListener(HeapDump.Listener)}.
   */
  public AndroidRefWatcherBuilder listenerServiceClass(
      Class<? extends AbstractAnalysisResultService> listenerServiceClass) {
    return heapDumpListener(new ServiceHeapDumpListener(context, listenerServiceClass));
  }

  /**
   * Sets a custom delay for how long the {@link RefWatcher} should wait until it checks if a
   * tracked object has been garbage collected. This overrides any call to {@link
   * #watchExecutor(WatchExecutor)}.
   */
  public AndroidRefWatcherBuilder watchDelay(long delay, TimeUnit unit) {
    return watchExecutor(new AndroidWatchExecutor(unit.toMillis(delay)));
  }

  /**
   * Sets the maximum number of heap dumps stored. This overrides any call to {@link
   * #leakDirectoryProvider(LeakDirectoryProvider)}.
   *
   * @throws IllegalArgumentException if maxStoredHeapDumps < 1.
   */
  public AndroidRefWatcherBuilder maxStoredHeapDumps(int maxStoredHeapDumps) {
    leakDirectoryProvider = new DefaultLeakDirectoryProvider(context, maxStoredHeapDumps);
    return this;
  }

  /**
   * Creates a {@link RefWatcher} instance.
   *
   * @throws UnsupportedOperationException if not called from the main thread.
   * @throws UnsupportedOperationException if called more than once per Android process.
   */
  public RefWatcher buildAndInstall() {
    if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
      throw new UnsupportedOperationException(
          "Should be called from the main thread, not " + Thread.currentThread().getName());
    }
    if (LeakCanaryInternals.refWatcher != null) {
      throw new UnsupportedOperationException("buildAndInstall() should only be called once.");
    }
    RefWatcher refWatcher = build();
    if (watchActivities) {
      ActivityRefWatcher.install((Application) context, refWatcher);
    }
    LeakCanaryInternals.refWatcher = refWatcher;
    LeakCanaryInternals.leakDirectoryProvider = leakDirectoryProvider;
    return refWatcher;
  }

  @Override protected boolean isDisabled() {
    return LeakCanary.isInAnalyzerProcess(context);
  }

  @Override protected HeapDumper defaultHeapDumper() {
    return new AndroidHeapDumper(context, leakDirectoryProvider);
  }

  @Override protected DebuggerControl defaultDebuggerControl() {
    return new AndroidDebuggerControl();
  }

  @Override protected HeapDump.Listener defaultHeapDumpListener() {
    return new ServiceHeapDumpListener(context, DisplayLeakService.class);
  }

  @Override protected ExcludedRefs defaultExcludedRefs() {
    return AndroidExcludedRefs.createAppDefaults().build();
  }

  @Override protected WatchExecutor defaultWatchExecutor() {
    return new AndroidWatchExecutor(DEFAULT_WATCH_DELAY_MILLIS);
  }
}
