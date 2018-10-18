package io.grpc.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

public abstract class AbstractManagedControlPlaneScheduler extends ManagedControlPlaneScheduler {
  private final ObjectPool<ScheduledExecutorService> timerServicePool;
  private volatile ScheduledExecutorService timerService;

  public AbstractManagedControlPlaneScheduler(
      ObjectPool<ScheduledExecutorService> timerServicePool) {
    this.timerServicePool = checkNotNull(timerServicePool, "timerServicePool");
    this.timerService = checkNotNull(timerServicePool.getObject(), "timerService");
  }

  @Override
  public final ScheduledContext schedule(final Runnable task, long delay, TimeUnit unit) {
    final ManagedRunnable runnable = new ManagedRunnable(task);
    ScheduledFuture<?> future = null;
    if (delay <= 0) {
      runInSynchronizationContext(runnable);
    } else {
      future = timerService.schedule(new Runnable() {
        @Override
        public void run() {
          runInSynchronizationContext(runnable);
        }
      }, delay, unit);
    }
    return new ScheduledContextImpl(runnable, future);
  }

  @Override
  public long currentTimeNanos() {
    return TimeProvider.SYSTEM_TIME_PROVIDER.currentTimeNanos();
  }

  @Override
  public void shutdown() {
    timerService = timerServicePool.returnObject(timerService);
  }

  protected abstract void runInSynchronizationContext(Runnable task);

  private static class ManagedRunnable implements Runnable {
    final Runnable task;
    boolean isCancelled;
    boolean hasStarted;

    ManagedRunnable(Runnable task) {
      this.task = checkNotNull(task, "task");
    }

    @Override
    public void run() {
      // The task may have been cancelled after timerService calls runInSynchronizationContext()
      // but before the runnable is actually run.  We must guarantee that the task will not be run
      // in this case.
      if (!isCancelled) {
        hasStarted = true;
        task.run();
      }
    }
  }

  private static class ScheduledContextImpl extends ScheduledContext {
    final ManagedRunnable runnable;
    @Nullable
    final ScheduledFuture<?> future;

    ScheduledContextImpl(ManagedRunnable runnable, @Nullable ScheduledFuture<?> future) {
      this.runnable = checkNotNull(runnable, "runnable");
      this.future = future;
    }

    @Override
    public void cancel() {
      runnable.isCancelled = true;
      if (future != null) {
        future.cancel(false);
      }
    }

    @Override
    public boolean isPending() {
      return !(runnable.hasStarted || runnable.isCancelled);
    }
  }
}
