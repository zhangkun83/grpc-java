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

  enum State {
    INIT, CONNECTING, NEGOTIATING, WORKING, CLOSED
  }

  private final Object lock = new Object();
  private final String serviceName;
  private final TransportManager tm;

  @GuardedBy("lock")
  private Throwable lastError;
  @GuardedBy("lock")
  private State state = State.INIT;
  @GuardedBy("lock")
  private EquivalentAddressGroup lbAddresses;
  @GuardedBy("lock")
  private ClientTransport currentLbTransport;
  @GuardedBy("lock")
  private final HashMap<SocketAddress, ResolvedServerInfo> servers =
      new HashMap<SocketAddress, ResolvedServerInfo>();
  @GuardedBy("lock")
  private RoundRobinServerList roundRobinServerList;
  @GuardedBy("lock")
  private final BlankFutureProvider<ClientTransport> pendingPicks =
      new BlankFutureProvider<ClientTransport>();
  private ExecutorService executor;
  private ScheduledExecutorService deadlineCancellationExecutor;

  GrpclbLoadBalancer(String serviceName, TransportManager tm) {
    this.serviceName = serviceName;
    this.tm = tm;
    executor = SharedResourceHolder.get(GrpcUtil.SHARED_CHANNEL_EXECUTOR);
    deadlineCancellationExecutor = SharedResourceHolder.get(GrpcUtil.TIMER_SERVICE);
  }

  @VisibleForTesting
  State getState() {
    synchronized (lock) {
      return state;
    }
  }

  @Override
  public ListenableFuture<ClientTransport> pickTransport(@Nullable RequestKey requestKey) {
    RoundRobinServerList serverListCopy;
    synchronized (lock) {
      Preconditions.checkState(state != State.CLOSED, "already closed");
      if (state != State.WORKING) {
        if (lastError != null) {
          return Futures.immediateFailedFuture(lastError);
        }
        return pendingPicks.newBlankFuture();
      }
      serverListCopy = Preconditions.checkNotNull(roundRobinServerList, "roundRobinServerList");
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
      lbAddresses = new EquivalentAddressGroup(addrs);
      if (state == State.INIT) {
        state = State.CONNECTING;
        // TODO(zhangkun83): should use a separate authority for LB servers
        ListenableFuture<ClientTransport> transportFuture = tm.getTransport(lbAddresses);
        Futures.addCallback(transportFuture, new FutureCallback<ClientTransport>() {
          @Override public void onSuccess(ClientTransport transport) {
            synchronized (lock) {
              if (state == State.CLOSED) {
                return;
              }
              Preconditions.checkState(state == State.CONNECTING, "unexpected state %s", state);
              currentLbTransport = transport;
              state = State.NEGOTIATING;
              logger.info("Starting LB negotiation");
              LoadBalanceRequest initRequest = LoadBalanceRequest.newBuilder()
                  .setInitialRequest(InitialLoadBalanceRequest.newBuilder()
                      .setName(serviceName).build())
                  .build();
              sendLbRequest(transport, initRequest);
            }
          }

          @Override public void onFailure(Throwable t) {
            synchronized (lock) {
              // TODO(zhangkun83): try to connect to the next LB server?
              lastError = t;
            }
          }
        });
      }
      // TODO(zhangkun83): otherwise, we need to check if the current LB server is still valid. If
      // not, we need to reconnect.
    }
  }

  @VisibleForTesting
  void sendLbRequest(ClientTransport transport, LoadBalanceRequest request) {
    Channel channel = new SingleTransportChannel(transport, executor,
        deadlineCancellationExecutor, serviceName);
    LoadBalancerGrpc.LoadBalancerStub stub = LoadBalancerGrpc.newStub(channel);
    StreamObserver<LoadBalanceRequest> requestWriter =
        stub.balanceLoad(lbResponseObserver);
    requestWriter.onNext(request);
  }

  @Override
  public void handleNameResolutionError(Status error) {
    handleError(error.augmentDescription("Name resolution failed"));
  }

  @Override
  public void shutdown() {
    synchronized (lock) {
      if (state == State.CLOSED) {
        return;
      }
      state = State.CLOSED;
      // TODO(zhangkun83): clean up connections
      executor = SharedResourceHolder.release(GrpcUtil.SHARED_CHANNEL_EXECUTOR, executor);
      deadlineCancellationExecutor = SharedResourceHolder.release(
          GrpcUtil.TIMER_SERVICE, deadlineCancellationExecutor);
    }
  }

  private void handleError(Status error) {
    FulfillmentBatch<ClientTransport> pendingPicksFulfillmentBatch;
    StatusException statusException = error.asException();
    synchronized (lock) {
      pendingPicksFulfillmentBatch = pendingPicks.createFulfillmentBatch();
      lastError = statusException;
    }
    pendingPicksFulfillmentBatch.fail(statusException);
  }

  @VisibleForTesting
  final StreamObserver<LoadBalanceResponse> lbResponseObserver =
      new StreamObserver<LoadBalanceResponse>() {
        @Override public void onNext(LoadBalanceResponse response) {
          logger.info("Got a LB response: " + response);
          FulfillmentBatch<ClientTransport> pendingPicksFulfillmentBatch;
          final RoundRobinServerList serverListCopy;
          synchronized (lock) {
            if (state == State.NEGOTIATING) {
              InitialLoadBalanceResponse initialResponse = response.getInitialResponse();
              // TODO(zhangkun83): make use of initialResponse
            }
            RoundRobinServerList.Builder listBuilder = new RoundRobinServerList.Builder(tm);
            servers.clear();
            ServerList serverList = response.getServerList();
            for (Server server : serverList.getServersList()) {
              if (server.getDropRequest()) {
                listBuilder.add(null);
              } else {
                InetSocketAddress address = new InetSocketAddress(
                    server.getIpAddress(), server.getPort());
                listBuilder.add(address);
                ResolvedServerInfo serverInfo = servers.get(address);
                if (serverInfo == null) {
                  // TODO(zhangkun83): fill the lb token to the attributes
                  serverInfo = new ResolvedServerInfo(address, Attributes.EMPTY);
                  servers.put(address, serverInfo);
                }
              }
            }
            roundRobinServerList = listBuilder.build();
            tm.updateRetainedTransports(servers.keySet());
            if (roundRobinServerList.size() == 0) {
              state = State.NEGOTIATING;
              return;
            }
            state = State.WORKING;
            pendingPicksFulfillmentBatch = pendingPicks.createFulfillmentBatch();
            serverListCopy = roundRobinServerList;
          }
          pendingPicksFulfillmentBatch.link(
              new Supplier<ListenableFuture<ClientTransport>>() {
                @Override
                public ListenableFuture<ClientTransport> get() {
                  return serverListCopy.getTransportForNextServer();
                }
              });
        }

        @Override public void onError(Throwable error) {
          handleError(Status.fromThrowable(error)
              .augmentDescription("RPC to GRPCLB LoadBalancer failed"));
          // TODO(zhangkun83): re-initiate connecting to LB
        }

        @Override public void onCompleted() {
          // TODO(zhangkun83): re-initiate connecting to LB
        }
      };
}
