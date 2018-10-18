package io.grpc.internal;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import io.grpc.internal.ControlPlaneScheduler.ScheduledContext;
import java.util.concurrent.TimeUnit;

/**
 * Schedules tasks according to back-off policy.  Can have at most one pending task at at time.
 * Not thread-safe.  All methods must be synchronized externally in the same context as the given
 * Executor.
 */
public final class BackedOffExecutor {
  private final ControlPlaneScheduler scheduler;
  private final BackoffPolicy.Provider backoffPolicyProvider;

  private boolean initialRunScheduled;
  private BackoffPolicy backoffPolicy;
  private ScheduledContext scheduledContext;
  private Long prevStartNanos;

  public BackedOffExecutor(
      ControlPlaneScheduler scheduler, BackoffPolicy.Provider backoffPolicyProvider) {
    this.scheduler = checkNotNull(scheduler, "scheduler");
    this.backoffPolicyProvider = checkNotNull(backoffPolicyProvider, "backoffPolicyProvider");
    reset();
  }

  public boolean isInitialRunScheduled() {
    return initialRunScheduled;
  }

  /**
   * Schedule a task for the initial run, which is not subject to the backoff.  Can only be called
   * one time initially or after {@link #reset} is called.
   */
  public void initialRun(Runnable task) {
    checkState(!initialRunScheduled, "initial run already scheduled");
    initialRunScheduled = true;
    scheduleAfterNanos(task, 0);
  }

  /**
   * Schedule a subsequent run according to the backoff policy.  Will throw if a task has already
   * been scheduled, or the initial run (via {@link #scheduleInitialRun}) has not happened.
   */
  public void subsequentRun(Runnable task) {
    checkState(initialRunScheduled, "the initial run was not scheduled");
    checkState(scheduledContext == null, "previously scheduled task was not run");
    checkState(prevStartNanos != null, "the initial run did not run");
    if (backoffPolicy == null) {
      backoffPolicy = backoffPolicyProvider.get();
    }
    long delayNanos =
        prevStartNanos + backoffPolicy.nextBackoffNanos() - scheduler.currentTimeNanos();
    scheduleAfterNanos(task, delayNanos);
  }

  private void scheduleAfterNanos(final Runnable task, long delayNanos) {
    final Runnable scheduledTask = new Runnable() {
        @Override
        public void run() {
          prevStartNanos = scheduler.currentTimeNanos();
          scheduledContext = null;
          task.run();
        }

        @Override
        public String toString() {
          return BackedOffExecutor.class.getSimpleName() + ".scheduledTask(" + task + ")";
        }
      };
    if (delayNanos <= 0) {
      scheduledTask.run();
    } else {
      scheduledContext = scheduler.schedule(scheduledTask, delayNanos, TimeUnit.NANOSECONDS);
    }
  }

  /**
   * Bring the executor to the initial state, and cancel the scheduled task if there is any.
   */
  public void reset() {
    if (scheduledContext != null) {
      scheduledContext.cancel();
      scheduledContext = null;
    }
    backoffPolicy = null;
    prevStartNanos = null;
    initialRunScheduled = false;
  }
}
