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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import io.grpc.Attributes;
import io.grpc.EquivalentAddressGroup;
import io.grpc.ResolvedServerInfo;
import io.grpc.Status;
import io.grpc.TransportManager;
import io.grpc.grpclb.GrpclbLoadBalancer.State;
import io.grpc.internal.ClientTransport;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link GrpclbLoadBalancer}. */
@RunWith(JUnit4.class)
public class GrpclbLoadBalancerTest {

  private static final String serviceName = "testlbservice";
  private final TransportManager mockTransportManager = mock(TransportManager.class);

  private TestGrpclbLoadBalancer loadBalancer = new TestGrpclbLoadBalancer();

  private static class SendLbRequestArgs {
    final ClientTransport transport;
    final LoadBalanceRequest request;

    SendLbRequestArgs(ClientTransport transport, LoadBalanceRequest request) {
      this.transport = transport;
      this.request = request;
    }
  }

  private class TestGrpclbLoadBalancer extends GrpclbLoadBalancer {
    final LinkedBlockingQueue<SendLbRequestArgs> sentLbRequests =
        new LinkedBlockingQueue<SendLbRequestArgs>();

    TestGrpclbLoadBalancer() {
      super(serviceName, mockTransportManager);
    }

    @Override void sendLbRequest(ClientTransport transport, LoadBalanceRequest request) {
      sentLbRequests.add(new SendLbRequestArgs(transport, request));
    }
  }

  private LoadBalanceResponse buildLbResponse(List<ResolvedServerInfo> servers) {
    ServerList.Builder serverListBuilder = ServerList.newBuilder();
    for (ResolvedServerInfo server : servers) {
      InetSocketAddress addr = (InetSocketAddress) server.getAddress();
      serverListBuilder.addServers(Server.newBuilder()
          .setIpAddress(addr.getHostString())
          .setPort(addr.getPort())
          .build());
    }
    return LoadBalanceResponse.newBuilder()
        .setServerList(serverListBuilder.build())
        .build();
  }

  @Test public void balancing() throws Exception {
    List<ResolvedServerInfo> servers = createResolvedServerInfoList(
        4000, 4001, 4001, 4002, 4003, 4004, 4005, 4006, 4007, 4008, 4009);

    // Set up mocks
    InetSocketAddress lbserverAddr = new InetSocketAddress("127.0.0.1", 30001);
    EquivalentAddressGroup lbservers = new EquivalentAddressGroup(lbserverAddr);
    ClientTransport lbTransport = mock(ClientTransport.class);
    SettableFuture<ClientTransport> lbTransportFuture = SettableFuture.create();
    when(mockTransportManager.getTransport(eq(lbservers))).thenReturn(lbTransportFuture);

    List<ClientTransport> transports = new ArrayList<ClientTransport>(servers.size());
    List<SettableFuture<ClientTransport>> transportFutures =
        new ArrayList<SettableFuture<ClientTransport>>(servers.size());

    for (ResolvedServerInfo server : servers) {
      transports.add(mock(ClientTransport.class));
      SettableFuture<ClientTransport> future = SettableFuture.create();
      transportFutures.add(future);
      when(mockTransportManager.getTransport(eq(new EquivalentAddressGroup(server.getAddress()))))
          .thenReturn(future);
    }

    // Pick before name resolved
    ListenableFuture<ClientTransport> pick0 = loadBalancer.pickTransport(null);

    // Name resolved
    loadBalancer.handleResolvedAddresses(
        Collections.singletonList(new ResolvedServerInfo(lbserverAddr, Attributes.EMPTY)),
        Attributes.EMPTY);

    // Pick after name resolved
    ListenableFuture<ClientTransport> pick1 = loadBalancer.pickTransport(null);
    verify(mockTransportManager).getTransport(eq(lbservers));

    // Make the transport for LB server ready
    lbTransportFuture.set(lbTransport);
    // An LB request is sent
    SendLbRequestArgs sentLbRequest = loadBalancer.sentLbRequests.poll(1000, TimeUnit.SECONDS);
    assertNotNull(sentLbRequest);
    assertSame(lbTransport, sentLbRequest.transport);

    // Simulate that the LB server reponses, with servers 0, 1, 2
    List<ResolvedServerInfo> servers1 = servers.subList(0, 3);
    assertNotNull(loadBalancer.lbResponseObserver);
    loadBalancer.lbResponseObserver.onNext(buildLbResponse(servers1));

    assertFalse(pick0.isDone());
    assertFalse(pick1.isDone());

    // Make the transports for servers1 ready
    for (int i = 0; i < 3; i++) {
      transportFutures.get(i).set(transports.get(i));
    }
    assertTrue(pick0.isDone());
    assertTrue(pick1.isDone());
    assertEquals(transports.get(0), pick0.get());
    assertEquals(transports.get(1), pick1.get());

    // Pick after LB server responded
    ListenableFuture<ClientTransport> pick2 = loadBalancer.pickTransport(null);
    assertTrue(pick2.isDone());
    assertEquals(transports.get(2), pick2.get());

    // Round-robin
    pick2 = loadBalancer.pickTransport(null);
    assertTrue(pick2.isDone());
    assertEquals(transports.get(0), pick2.get());

    // Only one LB request has ever been sent at this point
    assertEquals(0, loadBalancer.sentLbRequests.size());

    // Simulate an update LB reponse, with servers 3, 4
    List<ResolvedServerInfo> servers2 = servers.subList(3, 5);
    assertNotNull(loadBalancer.lbResponseObserver);
    loadBalancer.lbResponseObserver.onNext(buildLbResponse(servers2));

    // Subsequent picks will be on the new list
    ListenableFuture<ClientTransport> pick3 = loadBalancer.pickTransport(null);
    assertFalse(pick3.isDone());

    // Make the transports for servers2 ready
    for (int i = 3; i < 5; i++) {
      transportFutures.get(i).set(transports.get(i));
    }
    assertTrue(pick3.isDone());
    assertEquals(transports.get(2), pick3.get());

    // Then fail the LB stream
    loadBalancer.lbResponseObserver.onError(
        Status.UNAVAILABLE.withDescription("simulated").asException());

    // Another LB request is sent
    sentLbRequest = loadBalancer.sentLbRequests.poll(1000, TimeUnit.SECONDS);
    assertNotNull(sentLbRequest);

    // Subsequent pick will be pending
    ListenableFuture<ClientTransport> pick4 = loadBalancer.pickTransport(null);
  }

  @Test public void lbStreamFailedWithoutResponse() throws Exception {
    // Set up mocks
    InetSocketAddress lbserverAddr = new InetSocketAddress("127.0.0.1", 30001);
    EquivalentAddressGroup lbservers = new EquivalentAddressGroup(lbserverAddr);
    ClientTransport lbTransport = mock(ClientTransport.class);
    SettableFuture<ClientTransport> lbTransportFuture = SettableFuture.create();
    when(mockTransportManager.getTransport(eq(lbservers))).thenReturn(lbTransportFuture);

    // Name resolved
    loadBalancer.handleResolvedAddresses(
        Collections.singletonList(new ResolvedServerInfo(lbserverAddr, Attributes.EMPTY)),
        Attributes.EMPTY);

    // First pick, will be pending
    ListenableFuture<ClientTransport> pick0 = loadBalancer.pickTransport(null);

    // Make the transport for LB server ready
    lbTransportFuture.set(lbTransport);

    // An LB request is sent
    SendLbRequestArgs sentLbRequest = loadBalancer.sentLbRequests.poll(1000, TimeUnit.SECONDS);
    assertNotNull(sentLbRequest);
    assertSame(lbTransport, sentLbRequest.transport);

    // Simulate that the LB stream fails
    loadBalancer.lbResponseObserver.onError(
        Status.UNAVAILABLE.withDescription("simulated").asException());

    // The pending pick will fail
    assertTrue(pick0.isDone());
    try {
      pick0.get();
      fail("Should have thrown");
    } catch (ExecutionException e) {
      Status s = Status.fromThrowable(e);
      assertEquals(Status.Code.UNAVAILABLE, s.getCode());
      assertTrue(s.getDescription(),
          s.getDescription().contains("simulated")
          && s.getDescription().contains("RPC to GRPCLB LoadBalancer failed"));
    }

    // Another LB request is sent
    sentLbRequest = loadBalancer.sentLbRequests.poll(1000, TimeUnit.SECONDS);
    assertNotNull(sentLbRequest);
  }

  @Test public void lbConnectionClosed() {
  }

  private static List<ResolvedServerInfo> createResolvedServerInfoList(int ... ports) {
    List<ResolvedServerInfo> result = new ArrayList<ResolvedServerInfo>(ports.length);
    for (int port : ports) {
      InetSocketAddress inetSocketAddress = new InetSocketAddress("127.0.0.1", port);
      result.add(new ResolvedServerInfo(inetSocketAddress, Attributes.EMPTY));
    }
    return result;
  }
}
