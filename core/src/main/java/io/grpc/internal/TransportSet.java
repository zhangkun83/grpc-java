/*
 * Copyright 2015, Google Inc. All rights reserved.
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
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ConnectivityState;
import io.grpc.ConnectivityStateInfo;
import io.grpc.EquivalentAddressGroup;
import io.grpc.LoadBalancer;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.internal.ClientCallImpl.ClientTransportProvider;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Transports for a single {@link SocketAddress}.
 */
@ThreadSafe
final class TransportSet implements WithLogId {
  private static final Logger log = Logger.getLogger(TransportSet.class.getName());

  private final Object lock = new Object();
  private final EquivalentAddressGroup addressGroup;
  private final String authority;
  private final String userAgent;
  private final BackoffPolicy.Provider backoffPolicyProvider;
  private final Callback callback;
  private final ClientTransportFactory transportFactory;
  private final ScheduledExecutorService scheduledExecutor;
  private final SerializingExecutor channelExecutor;

  @GuardedBy("lock")
  private int nextAddressIndex;

  /**
   * The policy to control back off between reconnects. Non-{@code null} when last connect failed.
   */
  @GuardedBy("lock")
  private BackoffPolicy reconnectPolicy;

  /**
   * Timer monitoring duration since entering CONNECTING state.
   */
  @GuardedBy("lock")
  private final Stopwatch connectingTimer;

  @GuardedBy("lock")
  @Nullable
  private ScheduledFuture<?> reconnectTask;

  /**
   * All transports that are not terminated. At the very least the value of {@link #activeTransport}
   * will be present, but previously used transports that still have streams or are stopping may
   * also be present.
   */
  @GuardedBy("lock")
  private final Collection<ManagedClientTransport> transports =
      new ArrayList<ManagedClientTransport>();

  @GuardedBy("lock")
  private final InUseStateAggregator<ManagedClientTransport> inUseStateAggregator =
      new InUseStateAggregator<ManagedClientTransport>() {
        @Override
        void handleInUse() {
          callback.onInUse(TransportSet.this);
        }

        @Override
        void handleNotInUse() {
          callback.onNotInUse(TransportSet.this);
        }
      };

  /**
   * The to-be active transport, which is not ready yet.
   */
  @GuardedBy("lock")
  @Nullable
  private ConnectionClientTransport pendingTransport;

  private final LoadBalancer<ClientTransport> loadBalancer;

  @GuardedBy("lock")
  private boolean shutdown;

  /**
   * The transport for new outgoing requests. 'lock' must be held when assigning to it. Non-null
   * only in READY state.
   */
  @Nullable
  private volatile ManagedClientTransport activeTransport;

  @GuardedBy("lock")
  private final ConnectivityStateManager stateManager =
      new ConnectivityStateManager(ConnectivityState.IDLE);

  TransportSet(EquivalentAddressGroup addressGroup, String authority, String userAgent,
      LoadBalancer<ClientTransport> loadBalancer, BackoffPolicy.Provider backoffPolicyProvider,
      ClientTransportFactory transportFactory, ScheduledExecutorService scheduledExecutor,
      Supplier<Stopwatch> stopwatchSupplier, SerializingExecutor channelExecutor,
      final Callback callback) {
    this.addressGroup = Preconditions.checkNotNull(addressGroup, "addressGroup");
    this.authority = authority;
    this.userAgent = userAgent;
    this.loadBalancer = loadBalancer;
    this.backoffPolicyProvider = backoffPolicyProvider;
    this.transportFactory = transportFactory;
    this.scheduledExecutor = scheduledExecutor;
    this.connectingTimer = stopwatchSupplier.get();
    this.channelExecutor = channelExecutor;
    this.callback = callback;
    this.stateManager.addListener(new ConnectivityStateManager.StateListener() {
        @Override
        public void onStateChange(ConnectivityStateInfo newState) {
          callback.onStateChange(TransportSet.this, newState);
        }
      }, channelExecutor);
  }

  /**
   * Returns a READY transport that will be used to create new streams.
   *
   * <p>Returns {@code null} if the state is not READY.
   */
  @Nullable
  final ClientTransport obtainActiveTransport() {
    ClientTransport savedTransport = activeTransport;
    if (savedTransport != null) {
      return savedTransport;
    }
    Runnable runnable;
    synchronized (lock) {
      // Check again, since it could have changed before acquiring the lock
      savedTransport = activeTransport;
      if (savedTransport != null) {
        return savedTransport;
      }
      if (shutdown) {
        return null;
      }
      stateManager.gotoNonErrorState(ConnectivityState.CONNECTING);
      runnable = startNewTransport();
    }
    if (runnable != null) {
      runnable.run();
    }
    return savedTransport;
  }

  @CheckReturnValue
  @GuardedBy("lock")
  private Runnable startNewTransport() {
    Preconditions.checkState(reconnectTask == null, "Should have no reconnectTask scheduled");

    if (nextAddressIndex == 0) {
      connectingTimer.reset().start();
    }
    List<SocketAddress> addrs = addressGroup.getAddresses();
    final SocketAddress address = addrs.get(nextAddressIndex++);
    if (nextAddressIndex >= addrs.size()) {
      nextAddressIndex = 0;
    }

    ConnectionClientTransport transport =
        transportFactory.newClientTransport(address, authority, userAgent);
    if (log.isLoggable(Level.FINE)) {
      log.log(Level.FINE, "[{0}] Created {1} for {2}",
          new Object[] {getLogId(), transport.getLogId(), address});
    }
    pendingTransport = transport;
    transports.add(transport);
    return transport.start(new TransportListener(transport, address));
  }

  /**
   * Only called after all addresses attempted and failed (TRANSIENT_FAILURE).
   * @param status the causal status when the channel begins transition to
   *     TRANSIENT_FAILURE.
   */
  private void scheduleBackoff(final Status status) {
    class EndOfCurrentBackoff implements Runnable {
      @Override
      public void run() {
        try {
          Runnable runnable = null;
          synchronized (lock) {
            reconnectTask = null;
            if (!shutdown) {
              stateManager.gotoNonErrorState(ConnectivityState.CONNECTING);
            }
            runnable = startNewTransport();
          }
          if (runnable != null) {
            runnable.run();
          }
        } catch (Throwable t) {
          log.log(Level.WARNING, "Exception handling end of backoff", t);
        }
      }
    }

    synchronized (lock) {
      if (shutdown) {
        return;
      }
      stateManager.gotoTransientFailureState(status);
      if (reconnectPolicy == null) {
        reconnectPolicy = backoffPolicyProvider.get();
      }
      long delayMillis =
          reconnectPolicy.nextBackoffMillis() - connectingTimer.elapsed(TimeUnit.MILLISECONDS);
      if (log.isLoggable(Level.FINE)) {
        log.log(Level.FINE, "[{0}] Scheduling backoff for {1} ms",
            new Object[]{getLogId(), delayMillis});
      }
      Preconditions.checkState(reconnectTask == null, "previous reconnectTask is not done");
      reconnectTask = scheduledExecutor.schedule(
          new LogExceptionRunnable(new EndOfCurrentBackoff()),
          delayMillis,
          TimeUnit.MILLISECONDS);
    }
  }

  public void shutdown() {
    ManagedClientTransport savedActiveTransport;
    ConnectionClientTransport savedPendingTransport;
    boolean terminated = false;
    synchronized (lock) {
      if (shutdown) {
        return;
      }
      stateManager.gotoNonErrorState(ConnectivityState.SHUTDOWN);
      shutdown = true;
      savedActiveTransport = activeTransport;
      savedPendingTransport = pendingTransport;
      activeTransport = null;
      pendingTransport = null;
      if (transports.isEmpty()) {
        terminated = true;
        if (log.isLoggable(Level.FINE)) {
          log.log(Level.FINE, "[{0}] Terminated in shutdown()", getLogId());
        }
        Preconditions.checkState(reconnectTask == null, "Should have no reconnectTask scheduled");
      }  // else: the callback will be run once all transports have been terminated
    }
    if (savedActiveTransport != null) {
      savedActiveTransport.shutdown();
    }
    if (savedPendingTransport != null) {
      savedPendingTransport.shutdown();
    }
    if (terminated) {
      handleTermination();
    }
    return;
  }

  private void handleTermination() {
    channelExecutor.execute(new Runnable() {
        @Override
        public void run() {
          callback.onTerminated(TransportSet.this);
        }
      });
  }

  private void handleTransportInUseState(
      final ManagedClientTransport transport, final boolean inUse) {
    channelExecutor.execute(new Runnable() {
        @Override
        public void run() {
          inUseStateAggregator.updateObjectInUse(transport, inUse);
        }
      });
  }

  void shutdownNow(Status reason) {
    shutdown();
    Collection<ManagedClientTransport> transportsCopy;
    synchronized (lock) {
      transportsCopy = new ArrayList<ManagedClientTransport>(transports);
    }
    for (ManagedClientTransport transport : transportsCopy) {
      transport.shutdownNow(reason);
    }
  }

  @GuardedBy("lock")
  private void cancelReconnectTask() {
    if (reconnectTask != null) {
      reconnectTask.cancel(false);
      reconnectTask = null;
    }
  }

  @Override
  public String getLogId() {
    return GrpcUtil.getLogId(this);
  }

  public boolean isShutdown() {
    synchronized (lock) {
      return shutdown;
    }
  }

  public String authority() {
    return authority;
  }

  @VisibleForTesting
  ConnectivityState getState() {
    synchronized (lock) {
      return stateManager.getState();
    }
  }

  /** Shared base for both delayed and real transports. */
  private class BaseTransportListener implements ManagedClientTransport.Listener {
    protected final ManagedClientTransport transport;

    public BaseTransportListener(ManagedClientTransport transport) {
      this.transport = transport;
    }

    @Override
    public void transportReady() {}

    @Override
    public void transportInUse(final boolean inUse) {
      handleTransportInUseState(transport, inUse);
    }

    @Override
    public void transportShutdown(Status status) {}

    @Override
    public void transportTerminated() {
      boolean terminated = false;
      handleTransportInUseState(transport, false);
      synchronized (lock) {
        transports.remove(transport);
        if (shutdown && transports.isEmpty()) {
          if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE, "[{0}] Terminated in transportTerminated()", getLogId());
          }
          terminated = true;
          cancelReconnectTask();
        }
      }
      if (terminated) {
        handleTermination();
      }
    }
  }

  /** Listener for real transports. */
  private class TransportListener extends BaseTransportListener {
    private final SocketAddress address;

    public TransportListener(ManagedClientTransport transport, SocketAddress address) {
      super(transport);
      this.address = address;
    }

    @Override
    public void transportReady() {
      if (log.isLoggable(Level.FINE)) {
        log.log(Level.FINE, "[{0}] {1} for {2} is ready",
            new Object[] {getLogId(), transport.getLogId(), address});
      }
      super.transportReady();
      boolean savedShutdown;
      synchronized (lock) {
        savedShutdown = shutdown;
        reconnectPolicy = null;
        nextAddressIndex = 0;
        if (shutdown) {
          // activeTransport should have already been set to null by shutdown(). We keep it null.
          Preconditions.checkState(activeTransport == null,
              "Unexpected non-null activeTransport");
        } else if (pendingTransport == transport) {
          stateManager.gotoNonErrorState(ConnectivityState.READY);
          activeTransport = transport;
          pendingTransport = null;
        }
      }
      if (savedShutdown) {
        // See comments in the synchronized block above on why we shutdown here.
        transport.shutdown();
      }
      loadBalancer.handleTransportReady(addressGroup);
    }

    @Override
    public void transportShutdown(Status s) {
      boolean allAddressesFailed = false;
      boolean closedByServer = false;
      if (log.isLoggable(Level.FINE)) {
        log.log(Level.FINE, "[{0}] {1} for {2} is being shutdown with status {3}",
            new Object[] {getLogId(), transport.getLogId(), address, s});
      }
      super.transportShutdown(s);
      Runnable runnable = null;
      synchronized (lock) {
        if (activeTransport == transport) {
          // This is true only if the transport was ready.
          // shutdown() should have set activeTransport to null
          Preconditions.checkState(!shutdown, "unexpected shutdown state");
          stateManager.gotoNonErrorState(ConnectivityState.IDLE);
          activeTransport = null;
          closedByServer = true;
        } else if (pendingTransport == transport) {
          // shutdown() should have set pendingTransport to null
          Preconditions.checkState(!shutdown, "unexpected shutdown state");
          // Continue reconnect if there are still addresses to try.
          if (nextAddressIndex == 0) {
            allAddressesFailed = true;
          } else {
            Preconditions.checkState(stateManager.getState() == ConnectivityState.CONNECTING,
                "Expected state is CONNECTING, actual state is %s", stateManager.getState());
            runnable = startNewTransport();
          }
        }
      }
      if (allAddressesFailed) {
        // Initiate backoff
        // Transition to TRANSIENT_FAILURE
        scheduleBackoff(s);
      }
      if (runnable != null) {
        runnable.run();
      }
      loadBalancer.handleTransportShutdown(addressGroup, s);
    }

    @Override
    public void transportTerminated() {
      if (log.isLoggable(Level.FINE)) {
        log.log(Level.FINE, "[{0}] {1} for {2} is terminated",
            new Object[] {getLogId(), transport.getLogId(), address});
      }
      super.transportTerminated();
      Preconditions.checkState(activeTransport != transport,
          "activeTransport still points to this transport. "
          + "Seems transportShutdown() was not called.");
    }
  }

  // All methods are called in channelExecutor, which is a serializing executor.
  abstract static class Callback {
    /**
     * Called when the TransportSet is terminated, which means it's shut down and all transports
     * have been terminated.
     */
    public void onTerminated(TransportSet ts) { }

    /**
     * Called when the TransportSet's connectivity state has changed.
     */
    public void onStateChange(TransportSet ts, ConnectivityStateInfo newState) { }

    /**
     * Called when the TransportSet's in-use state has changed to true, which means at least one
     * transport is in use. This method is called under a lock thus externally synchronized.
     */
    public void onInUse(TransportSet ts) { }

    /**
     * Called when the TransportSet's in-use state has changed to false, which means no transport is
     * in use. This method is called under a lock thus externally synchronized.
     */
    public void onNotInUse(TransportSet ts) { }
  }
}
