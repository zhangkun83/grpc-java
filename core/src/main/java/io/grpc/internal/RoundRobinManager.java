/*
 * Copyright 2016, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *
 *    * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.grpc.internal;

import com.google.common.annotations.VisibleForTesting;

import io.grpc.Status;
import io.grpc.TransportManager;
import io.grpc.TransportManager.Subchannel;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Implements the round-robin behavior and manages subchannels for round-robin LoadBalancers.
 */
@ThreadSafe
public final class RoundRobinManager<T> {
  private final TransportManager<T> tm;

  // Mutations to them must be done in runSequentially()
  private final HashMap<EquivalentAddressGroup, SubchannelState<T>> subchannels =
      new HashMap<EquivalentAddressGroup, SubchannelState<T>>();
  private List<EquivalentAddressGroup> addressList = Collections.emptyList();
  private volatile RoundRobinSubchannelList<T> roundRobinList;

  private final BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>();

  public RoundRobinManager(TransportManager<T> tm) {
    this.tm = tm;
    rebuildRoundRobinList();
  }

  /**
   * Update the round-robin address list.
   */
  public void updateAddressList(final List<EquivalentAddressGroup> latestAddressList) {
    runSequentially(new Runnable() {
        @Override
        public void run() {
          HashSet<EquivalentAddressGroup> latestAddressSet =
              new HashSet<EquivalentAddressGroup>(latestAddressList);

          // Create subchannels for the added addresses
          for (EquivalentAddressGroup address : latestAddressSet) {
            if (!subchannels.containsKey(address)) {
              SubchannelState<T> newSubchannelState =
                  new SubchannelState<T>(tm.createSubchannel(addressGroup));
              subchannels.put(address, newSubchannelState);
              new ChannelStateWatcher(newSubchannelState, stopwatchSupplier).run();
            }
          }

          // Shutdown the subchannels for the removed addresses
          for (Iterator<Entry<EquivalentAddressGroup, SubchannelState<T>>> it =
                   subchannels.entrySet().iterator(); it.hasNext();) {
            Entry<EquivalentAddressGroup, SubchannelState<T>> entry = it.next();
            if (!latestAddressSet.contains(entry.getKey())) {
              it.remove();
              entry.getValue().subchannel.shutdown();
            }
          }

          this.addressList = Collections.unmodifiableList(
              new ArrayList<EquivalentAddressGroup>(latestAddressList));

          rebuildRoundRobinList();
        }
      });
  }

  private void rebuildRoundRobinList() {
    List<Subchannel<T>> newList = new ArrayList<Subchannel<T>>();
    if (addressList.isEmpty()) {
      return new RoundRobinSubchannelList<T>(
          tm, Status.UNAVAILABLE.withDescription("No address available"));
    }
    for (EquivalentAddressGroup address : addressList) {
      SubchannelState<T> subchannelState = subchannels.get(address);
      checkState(subchannelState != null, "Cannot find subchannel for %s", address);
      if (subchannelState.healthy) {
        newList.add(subchannelState.subchannel);
      }
    }
    if (newList.isEmpty()) {
      return new RoundRobinSubchannelList<T>(tm, Status.UNAVAILABLE.withDescription(
              "None of the " + addressList.size() + " subchannels are healthy"));
    }
    this.roundRobinList = new RoundRobinSubchannelList<T>(tm, newList);
  }

  /**
   * Picks a transport for the next RPC.
   */
  public PickResult<T> getNextTransport() {
    RoundRobinSubchannelList<T> savedList = roundRobinList;
    if (savedList.getError() != null) {
      return new PickResult<T>(savedList.getError());
    }
    return new PickResult<T>(savedList.getNextTransport());
  }

  public static final class PickResult<T> {
    private final Status status;
    @Nullable private final T transport;

    private PickResult(Status status) {
      checkArgument(!status.isOk(), "status should not be OK");
      this.status = status;
      this.transport = null;
    }

    private PickResult(T transport) {
      this.status = Status.OK;
      this.transport = checkNotNull(transport, "transport");
    }

    public Status getStatus() {
      return status;
    }

    /**
     * {@code null} if {@link #getStatus} returns a non-OK status.
     */
    @Nullable
    public T getTransport() {
      return transport;
    }
  }

  private static final long MAX_CONNECTING_TIME_SECONDS = 20;

  private static class SubchannelState<T> implements Runnable {
    final Subchannel<T> subchannel;
    // Suppose it's READY initially, but will be updated in run() right after this subchannel is
    // created.
    ConnectivityState state = ConnectivityState.READY;
    boolean healthyConnecting;
    
    final Stopwatch healthyConnectingDuration;

    SubchannelState(Subchannel<T> subchannel, Supplier<Stopwatch> stopwatchSupplier) {
      this.subchannel = subchannel;
      this.healthyConnectionDuration = stopwatchSupplier.get();
    }

    boolean isHealthy() {
      return healthyConnecting || state == ConnectivityState.READY;
    }

    @Override
    public void run() {
      runSequentially(new Runnable() {
          @Override
          public void run() {
            boolean prevIsHealthy = isHealthy();
            ConnectivityState prevState = state;
            state = subchannel.getState(true);

            if (prevState == ConnectivityState.READY && state == ConnectivityState.CONNECTING) {
              healthyConnecting = true;
              healthyConnectingDuration.reset().start();
            } else if (healthyConnectingDuration.elapsed(TimeUnit.SECONDS)
                > MAX_CONNECTING_TIME_SECONDS) {
              healthyConnecting = false;
              healthyConnectingDuration.stop();
            }

            if (isHealth() != prevIsHealthy) {
              rebuildRoundRobinList();
            }
            if (state != ConnectivityState.SHUTDOWN) {
              subchannel.notifyWhenStateChanged(state, SubchannelState.this);
            }
          }
        });
    }
  }

  private void runSequentially(Runnable work) {
    workQueue.add(work);
    Runnable toRun = workQueue.poll();
    while (toRun != null) {
      toRun.run();
    }
  }
}
