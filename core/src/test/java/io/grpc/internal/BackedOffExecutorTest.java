/*
 * Copyright 2016 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.grpc.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link BackedOffExecutor}.
 */
@RunWith(JUnit4.class)
public class BackedOffExecutorTest {
  private final FakeClock fakeClock = new FakeClock();
  private int taskRunTimes;
  private final Runnable task = new Runnable() {
      @Override
      public void run() {
        taskRunTimes++;
      }
    };

  @Mock
  private BackoffPolicy.Provider backoffPolicyProvider;
  @Mock
  private BackoffPolicy backoffPolicy1;
  @Mock
  private BackoffPolicy backoffPolicy2;
  private BackedOffExecutor boe;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(backoffPolicy1.nextBackoffNanos()).thenReturn(10L, 100L);
    when(backoffPolicy2.nextBackoffNanos()).thenReturn(15L, 105L);
    when(backoffPolicyProvider.get()).thenReturn(backoffPolicy1, backoffPolicy2);
    boe = new BackedOffExecutor(
        fakeClock.newControlPlaneScheduler(), backoffPolicyProvider);
  }

  @Test
  public void runAtScheduledTimes() {
    // Initial run 
    boe.initialRun(task);
    assertThat(fakeClock.runDueTasks()).isEqualTo(0);
    assertThat(taskRunTimes).isEqualTo(1);
    verifyZeroInteractions(backoffPolicyProvider);

    // First backed-off run. Will be on backoffPolicy1
    boe.subsequentRun(task);
    verify(backoffPolicyProvider).get();
    verify(backoffPolicy1).nextBackoffNanos();
    verifyTaskRunAfterNanos(10);

    // Suppose task took 13ns to run, the delay for the next try will deduct this amount
    fakeClock.forwardNanos(13);
    // Second backed-off run
    boe.subsequentRun(task);
    verifyTaskRunAfterNanos(100 - 13);

    boe.reset();
    verify(backoffPolicyProvider).get();
    assertThat(taskRunTimes).isEqualTo(3);

    // Start over. Will be on backoffPolicy2
    boe.initialRun(task);
    assertThat(fakeClock.runDueTasks()).isEqualTo(0);
    assertThat(taskRunTimes).isEqualTo(4);

    boe.subsequentRun(task);
    verify(backoffPolicyProvider, times(2)).get();
    verify(backoffPolicy2).nextBackoffNanos();
    verifyTaskRunAfterNanos(15);

    // Suppose task took more than the next delay to run, the delay for the next try will be
    // negative thus will run immdiately
    fakeClock.forwardNanos(105 + 1);
    boe.subsequentRun(task);
    assertThat(fakeClock.runDueTasks()).isEqualTo(0);
    assertThat(taskRunTimes).isEqualTo(6);

    verify(backoffPolicy1, times(2)).nextBackoffNanos();
    verify(backoffPolicy2, times(2)).nextBackoffNanos();
    verify(backoffPolicyProvider, times(2)).get();
  }

  @Test
  public void invalidUsages() {
    try {
      boe.subsequentRun(task);
      fail("Should throw");
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()).contains("the initial run was not scheduled");
    }

    boe.initialRun(task);
    assertThat(taskRunTimes).isEqualTo(1);
    try {
      boe.initialRun(task);
      fail("Should throw");
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()).contains("initial run already scheduled");
    }

    boe.subsequentRun(task);

    try {
      boe.subsequentRun(task);
      fail("Should throw");
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()).contains("previously scheduled task was not run");
    }

    verifyTaskRunAfterNanos(10);
    assertThat(taskRunTimes).isEqualTo(2);

    // reset() is needed to call initialRun() again
    try {
      boe.initialRun(task);
      fail("Should throw");
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()).contains("initial run already scheduled");
    }
    boe.reset();
    boe.initialRun(task);
    assertThat(taskRunTimes).isEqualTo(3);
  }

  @Test
  public void resetBeforeTimerExpires() {
    boe.initialRun(task);
    assertThat(taskRunTimes).isEqualTo(1);
    boe.subsequentRun(task);
    assertThat(fakeClock.numPendingTasks()).isEqualTo(1);
    assertThat(fakeClock.forwardNanos(5)).isEqualTo(0);

    boe.reset();
    assertThat(fakeClock.numPendingTasks()).isEqualTo(0);
    assertThat(taskRunTimes).isEqualTo(1);
  }

  private void verifyTaskRunAfterNanos(int nanos) {
    int initialTimes = taskRunTimes;
    assertThat(fakeClock.forwardNanos(nanos - 1)).isEqualTo(0);
    assertThat(taskRunTimes).isEqualTo(initialTimes);

    assertThat(fakeClock.forwardNanos(1)).isEqualTo(1);
    assertThat(taskRunTimes).isEqualTo(initialTimes + 1);
  }
}
