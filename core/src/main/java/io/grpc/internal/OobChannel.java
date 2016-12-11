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

import static com.google.common.base.Preconditions.checkNotNull;
import static io.grpc.ConnectivityState.READY;
import static io.grpc.ConnectivityState.TRANSIENT_FAILURE;

import com.google.census.CensusContextFactory;
import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;

import io.grpc.Attributes;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ConnectivityStateInfo;
import io.grpc.LoadBalancer2.Helper;
import io.grpc.LoadBalancer2.PickResult;
import io.grpc.LoadBalancer2.SubchannelPicker;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.internal.ClientCallImpl.ClientTransportProvider;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.concurrent.ThreadSafe;

/**
 * A ManagedChannel backed by a single {@link InternalSubchannel} and used for {@link LoadBalancer2}
 * to its own RPC needs.
 */
@ThreadSafe
final class OobChannel extends ManagedChannel implements WithLogId {
  private static final Logger log = Logger.getLogger(OobChannel.class.getName());

  private final InternalSubchannel subchannel;
  private final Helper helper;
  private final CensusContextFactory censusFactory;
  private final String authority;
  private final DelayedClientTransport2 delayedTransport;
  private final Executor executor;
  private final ScheduledExecutorService deadlineCancellationExecutor;
  private final Supplier<Stopwatch> stopwatchSupplier;
  private final CountDownLatch terminatedLatch = new CountDownLatch(1);

  private final ClientTransportProvider transportProvider = new ClientTransportProvider() {
    @Override
    public ClientTransport get(CallOptions callOptions, Metadata headers) {
      // delayed transport's newStream() always acquires a lock, but concurrent performance doesn't
      // matter here because OOB communication should be sparse, and it's not on application RPC's
      // critical path.
      return delayedTransport;
    }
  };

  private final SubchannelPicker subchannelPicker = new SubchannelPicker() {
      final PickResult result = PickResult.withSubchannel(subchannel);

      @Override
      public PickResult pickSubchannel(Attributes affinity, Metadata headers) {
        return result;
      }
    };

  OobChannel(CensusContextFactory censusFactory, Helper helper, InternalSubchannel subchannel,
      String authority, Executor executor, ScheduledExecutorService deadlineCancellationExecutor,
      Supplier<Stopwatch> stopwatchSupplier,
      ChannelExecutor channelExecutor) {
    this.censusFactory = checkNotNull(censusFactory, "censusFactory");
    this.subchannel = checkNotNull(subchannel, "subchannel");
    this.helper = checkNotNull(helper, "helper");
    this.authority = checkNotNull(authority, "authority");
    this.executor = checkNotNull(executor, "executor");
    this.deadlineCancellationExecutor = checkNotNull(
        deadlineCancellationExecutor, "deadlineCancellationExecutor");
    this.stopwatchSupplier = checkNotNull(stopwatchSupplier, "stopwatchSupplier");
    log.log(Level.FINE, "[{0}] Created with [{1}]", new Object[] {this, subchannel});
  }

  @Override
  public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(
      MethodDescriptor<RequestT, ResponseT> methodDescriptor, CallOptions callOptions) {
    StatsTraceContext statsTraceCtx = StatsTraceContext.newClientContext(
        methodDescriptor.getFullMethodName(), censusFactory, stopwatchSupplier);
    return new ClientCallImpl<RequestT, ResponseT>(methodDescriptor,
        new SerializingExecutor(executor), callOptions, statsTraceCtx, transportProvider,
        deadlineCancellationExecutor);
  }

  @Override
  public String authority() {
    return authority;
  }

  @Override
  public boolean isTerminated() {
    return terminatedLatch.getCount() == 0;
  }

  @Override
  public boolean awaitTermination(long time, TimeUnit unit) {
    return terminatedLatch.await(time, unit);
  }

  @Override
  public boolean isShutdown() {
    return shutdown;
  }

  @Override
  public ManagedChannel shutdownNow() {
    delayedTransport.shutdownNow(
        Status.UNAVAILABLE.withDescription("OobChannel.shutdownNow() called"));
    return this;
  }

  void handleSubchannelStateChange(ConnectivityStateInfo newState) {
    switch (newState.getState()) {
      case READY:
      case IDLE:
        helper.updatePicker(subchannelPicker);
        break;
      case TRANSIENT_FAILURE:
        helper.updatePicker(new SubchannelPicker() {
            final PickResult errorResult = PickResult.withError(newState.getStatus());

            @Override
            public PickResult pickSubchannel(Attributes affinity, Metadata headers) {
              return errorResult;
            }
          });
        break;
    }
    // TODO(zhangkun83): how should SHUTDOWN be handled?
  }

  void handleSubchannelTerminated() {
    terminatedLatch.countDown();
  }
}
