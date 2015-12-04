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

package io.grpc.grpclb;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import io.grpc.Attributes;
import io.grpc.Channel;
import io.grpc.EquivalentAddressGroup;
import io.grpc.ExperimentalApi;
import io.grpc.LoadBalancer;
import io.grpc.RequestKey;
import io.grpc.ResolvedServerInfo;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.TransportManager;
import io.grpc.internal.BlankFutureProvider;
import io.grpc.internal.BlankFutureProvider.FulfillmentBatch;
import io.grpc.internal.ClientTransport;
import io.grpc.internal.GrpcUtil;
import io.grpc.internal.SharedResourceHolder;
import io.grpc.internal.SingleTransportChannel;
import io.grpc.stub.StreamObserver;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * A {@link LoadBalancer} that uses the GRPCLB protocol.
 */
@ExperimentalApi
class GrpclbLoadBalancer extends LoadBalancer {
  private static final Logger logger = Logger.getLogger(GrpclbLoadBalancer.class.getName());

  private final Object lock = new Object();
  private final String serviceName;
  private final TransportManager tm;

  // General states
  @GuardedBy("lock")
  private final BlankFutureProvider<ClientTransport> pendingPicks =
      new BlankFutureProvider<ClientTransport>();
  @GuardedBy("lock")
  private Throwable lastError;

  @GuardedBy("lock")
  private boolean closed;

  // Load-balancer service states
  @GuardedBy("lock")
  private EquivalentAddressGroup lbAddresses;
  @GuardedBy("lock")
  private ClientTransport lbTransport;
  @GuardedBy("lock")
  private StreamObserver<LoadBalanceResponse> lbResponseObserver;
  @GuardedBy("lock")
  private StreamObserver<LoadBalanceRequest> lbRequestWriter;

  // Server list states
  @GuardedBy("lock")
  private HashMap<SocketAddress, ResolvedServerInfo> servers;
  @GuardedBy("lock")
  @VisibleForTesting
  private RoundRobinServerList roundRobinServerList;

  private ExecutorService executor;
  private ScheduledExecutorService deadlineCancellationExecutor;

  GrpclbLoadBalancer(String serviceName, TransportManager tm) {
    this.serviceName = serviceName;
    this.tm = tm;
    executor = SharedResourceHolder.get(GrpcUtil.SHARED_CHANNEL_EXECUTOR);
    deadlineCancellationExecutor = SharedResourceHolder.get(GrpcUtil.TIMER_SERVICE);
  }

  @VisibleForTesting
  StreamObserver<LoadBalanceResponse> getLbResponseObserver() {
    synchronized (lock) {
      return lbResponseObserver;
    }
  }

  @VisibleForTesting
  RoundRobinServerList getRoundRobinServerList() {
    synchronized (lock) {
      return roundRobinServerList;
    }
  }

  @Override
  public ListenableFuture<ClientTransport> pickTransport(@Nullable RequestKey requestKey) {
    RoundRobinServerList serverListCopy;
    synchronized (lock) {
      Preconditions.checkState(!closed, "already closed");
      if (roundRobinServerList == null) {
        if (lastError == null) {
          return pendingPicks.newBlankFuture();
        } else {
          return Futures.immediateFailedFuture(lastError);
        }
      }
      serverListCopy = roundRobinServerList;
    }
    return serverListCopy.getTransportForNextServer();
  }

  @Override
  public void handleResolvedAddresses(
      List<ResolvedServerInfo> updatedServers, Attributes config) {
    synchronized (lock) {
      ArrayList<SocketAddress> addrs = new ArrayList<SocketAddress>(updatedServers.size());
      for (ResolvedServerInfo serverInfo : updatedServers) {
        addrs.add(serverInfo.getAddress());
      }
      EquivalentAddressGroup newLbAddresses = new EquivalentAddressGroup(addrs);
      if (!newLbAddresses.equals(lbAddresses)) {
        lbAddresses = newLbAddresses;
        connectToLb();
      }
    }
  }

  @GuardedBy("lock")
  private void connectToLb() {
    if (closed) {
      return;
    }
    lbResponseObserver = null;
    // TODO(zhangkun83): should use a separate authority for LB servers
    Preconditions.checkNotNull(lbAddresses, "lbAddresses");
    ListenableFuture<ClientTransport> transportFuture = tm.getTransport(lbAddresses);
    Futures.addCallback(
        Preconditions.checkNotNull(transportFuture),
        new FutureCallback<ClientTransport>() {
          @Override public void onSuccess(ClientTransport transport) {
            synchronized (lock) {
              if (closed) {
                return;
              }
              lbTransport = transport;
              startNegotiation();
            }
          }

          @Override public void onFailure(Throwable t) {
            handleError(Status.fromThrowable(t).augmentDescription(
                "Failed to connect to LB server"));
          }
        },
        executor);
  }

  @GuardedBy("lock")
  private void startNegotiation() {
    Preconditions.checkState(lbTransport != null, "currentLbTransport must be available");
    logger.info("Starting LB negotiation");
    LoadBalanceRequest initRequest = LoadBalanceRequest.newBuilder()
        .setInitialRequest(InitialLoadBalanceRequest.newBuilder()
            .setName(serviceName).build())
        .build();
    lbResponseObserver = new LbResponseObserver();
    sendLbRequest(lbTransport, initRequest);
  }

  @VisibleForTesting  // to be mocked in tests
  @GuardedBy("lock")
  void sendLbRequest(ClientTransport transport, LoadBalanceRequest request) {
    Channel channel = new SingleTransportChannel(transport, executor,
        deadlineCancellationExecutor, serviceName);
    LoadBalancerGrpc.LoadBalancerStub stub = LoadBalancerGrpc.newStub(channel);
    lbRequestWriter = stub.balanceLoad(lbResponseObserver);
    lbRequestWriter.onNext(request);
  }

  @Override
  public void handleNameResolutionError(Status error) {
    handleError(error.augmentDescription("Name resolution failed"));
  }

  @Override
  public void shutdown() {
    synchronized (lock) {
      if (closed) {
        return;
      }
      closed = true;
      if (lbRequestWriter != null) {
        lbRequestWriter.onCompleted();
      }
      executor = SharedResourceHolder.release(GrpcUtil.SHARED_CHANNEL_EXECUTOR, executor);
      deadlineCancellationExecutor = SharedResourceHolder.release(
          GrpcUtil.TIMER_SERVICE, deadlineCancellationExecutor);
    }
  }

  @Override
  public void transportShutdown(
      EquivalentAddressGroup addressGroup, ClientTransport transport, Status status) {
    handleError(status.augmentDescription("Transport to LB server closed"));
    synchronized (lock) {
      if (transport == lbTransport) {
        connectToLb();
      }
    }
  }

  private void handleError(Status error) {
    FulfillmentBatch<ClientTransport> pendingPicksFulfillmentBatch;
    StatusException statusException = error.asException();
    synchronized (lock) {
      lastError = statusException;
      pendingPicksFulfillmentBatch = pendingPicks.createFulfillmentBatch();
    }
    pendingPicksFulfillmentBatch.fail(statusException);
  }

  private class LbResponseObserver implements StreamObserver<LoadBalanceResponse> {
    @Override public void onNext(LoadBalanceResponse response) {
      logger.info("Got a LB response: " + response);
      FulfillmentBatch<ClientTransport> pendingPicksFulfillmentBatch;
      final RoundRobinServerList newRoundRobinServerList;
      HashMap<SocketAddress, ResolvedServerInfo> newServerMap =
          new HashMap<SocketAddress, ResolvedServerInfo>();
      synchronized (lock) {
        InitialLoadBalanceResponse initialResponse = response.getInitialResponse();
        // TODO(zhangkun83): make use of initialResponse
        RoundRobinServerList.Builder listBuilder = new RoundRobinServerList.Builder(tm);
        ServerList serverList = response.getServerList();
        for (Server server : serverList.getServersList()) {
          if (server.getDropRequest()) {
            listBuilder.add(null);
          } else {
            InetSocketAddress address = new InetSocketAddress(
                server.getIpAddress(), server.getPort());
            listBuilder.add(address);
            // TODO(zhangkun83): fill the lb token to the attributes
            if (!newServerMap.containsKey(address)) {
              newServerMap.put(address, new ResolvedServerInfo(address, Attributes.EMPTY));
            }
          }
        }
        newRoundRobinServerList = listBuilder.build();
        if (newRoundRobinServerList.size() == 0) {
          return;
        }
        roundRobinServerList = newRoundRobinServerList;
        servers = newServerMap;
        pendingPicksFulfillmentBatch = pendingPicks.createFulfillmentBatch();
      }
      tm.updateRetainedTransports(newServerMap.keySet());
      pendingPicksFulfillmentBatch.link(
          new Supplier<ListenableFuture<ClientTransport>>() {
            @Override
            public ListenableFuture<ClientTransport> get() {
              return newRoundRobinServerList.getTransportForNextServer();
            }
          });
    }

    @Override public void onError(Throwable error) {
      onStreamClosed(Status.fromThrowable(error)
          .augmentDescription("Stream to GRPCLB LoadBalancer had an error"));
    }

    @Override public void onCompleted() {
      onStreamClosed(Status.UNAVAILABLE.augmentDescription(
          "Stream to GRPCLB LoadBalancer was closed"));
    }

    private void onStreamClosed(Status status) {
      handleError(status);
      synchronized (this) {
        if (lbResponseObserver == this) {
          // I am still the active LB stream. Reopen the stream.
          startNegotiation();
        }
      }
    }
  }
}
