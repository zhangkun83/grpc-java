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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

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

  private EquivalentAddressGroup buildAddressGroup(ResolvedServerInfo serverInfo) {
    return new EquivalentAddressGroup(serverInfo.getAddress());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void balancing() throws Exception {
    List<ResolvedServerInfo> servers = createResolvedServerInfoList(
        4000, 4001, 4002, 4003, 4004, 4005);

    // Set up mocks
    List<ClientTransport> transports = new ArrayList<ClientTransport>(servers.size());
    List<SettableFuture<ClientTransport>> transportFutures =
        new ArrayList<SettableFuture<ClientTransport>>(servers.size());

    for (ResolvedServerInfo server : servers) {
      transports.add(
          mock(ClientTransport.class, withSettings().name("Transport for "  + server.toString())));
      SettableFuture<ClientTransport> future = SettableFuture.create();
      transportFutures.add(future);
      when(mockTransportManager.getTransport(eq(new EquivalentAddressGroup(server.getAddress()))))
          .thenReturn(future);
    }

    ListenableFuture<ClientTransport> pick0, pick1;

    // Pick before name resolved
    pick0 = loadBalancer.pickTransport(null);

    // Name resolved
    ResolvedServerInfo lbServerInfo = new ResolvedServerInfo(
        new InetSocketAddress("127.0.0.1", 30001), Attributes.EMPTY);
    EquivalentAddressGroup lbAddressGroup = buildAddressGroup(lbServerInfo);
    SettableFuture<ClientTransport> lbTransportFuture = SettableFuture.create();
    when(mockTransportManager.getTransport(eq(lbAddressGroup))).thenReturn(lbTransportFuture);
    loadBalancer.handleResolvedAddresses(Collections.singletonList(lbServerInfo), Attributes.EMPTY);

    // Pick after name resolved
    pick1 = loadBalancer.pickTransport(null);
    verify(mockTransportManager).getTransport(eq(lbAddressGroup));

    // Make the transport for LB server ready
    ClientTransport lbTransport = mock(ClientTransport.class);
    lbTransportFuture.set(lbTransport);
    // An LB request is sent
    SendLbRequestArgs sentLbRequest = loadBalancer.sentLbRequests.poll(1000, TimeUnit.SECONDS);
    assertNotNull(sentLbRequest);
    assertSame(lbTransport, sentLbRequest.transport);

    // Simulate that the LB server reponses, with servers 0, 1, 1
    List<ResolvedServerInfo> serverList1 = new ArrayList<ResolvedServerInfo>();
    Collections.addAll(serverList1, servers.get(0), servers.get(1), servers.get(1));
    assertNotNull(loadBalancer.getLbResponseObserver());
    loadBalancer.getLbResponseObserver().onNext(buildLbResponse(serverList1));

    assertFalse(pick0.isDone());
    assertFalse(pick1.isDone());

    verify(mockTransportManager).getTransport(eq(buildAddressGroup(servers.get(0))));
    verify(mockTransportManager).getTransport(eq(buildAddressGroup(servers.get(1))));

    // Make the transports for serverList1 ready
    for (int i = 0; i < 2; i++) {
      transportFutures.get(i).set(transports.get(i));
    }
    assertTrue(pick0.isDone());
    assertTrue(pick1.isDone());
    assertSame(transports.get(0), pick0.get());
    assertSame(transports.get(1), pick1.get());

    // Pick after LB server responded. Server 1 is repeated in the list.
    pick0 = loadBalancer.pickTransport(null);
    assertTrue(pick0.isDone());
    assertSame(transports.get(1), pick0.get());

    // Pick beyond the end of the list. Go back to the beginning.
    pick0 = loadBalancer.pickTransport(null);
    assertTrue(pick0.isDone());
    assertSame(transports.get(0), pick0.get());

    // Only one LB request has ever been sent at this point
    assertEquals(0, loadBalancer.sentLbRequests.size());
  }

  private List<EquivalentAddressGroup> buildRoundRobinList(List<ResolvedServerInfo> serverList) {
    ArrayList<EquivalentAddressGroup> roundRobinList = new ArrayList<EquivalentAddressGroup>();
    for (ResolvedServerInfo serverInfo : serverList) {
      roundRobinList.add(new EquivalentAddressGroup(serverInfo.getAddress()));
    }
    return roundRobinList;
  }

  @Test public void serverListUpdated() throws Exception {
    ResolvedServerInfo lbServerInfo = new ResolvedServerInfo(
        new InetSocketAddress("127.0.0.1", 30001), Attributes.EMPTY);
    EquivalentAddressGroup lbAddressGroup = buildAddressGroup(lbServerInfo);
    SettableFuture<ClientTransport> lbTransportFuture = SettableFuture.create();
    when(mockTransportManager.getTransport(eq(lbAddressGroup))).thenReturn(lbTransportFuture);

    // Simulate the initial set of LB addresses resolved
    loadBalancer.handleResolvedAddresses(Collections.singletonList(lbServerInfo), Attributes.EMPTY);

    // Make the transport for LB server ready
    ClientTransport lbTransport = mock(ClientTransport.class);
    lbTransportFuture.set(lbTransport);

    // An LB request is sent
    SendLbRequestArgs sentLbRequest = loadBalancer.sentLbRequests.poll(1000, TimeUnit.SECONDS);
    assertNotNull(sentLbRequest);
    assertSame(lbTransport, sentLbRequest.transport);

    // Simulate LB server responds a server list
    List<ResolvedServerInfo> serverList = createResolvedServerInfoList(4000, 4001);
    loadBalancer.getLbResponseObserver().onNext(buildLbResponse(serverList));

    // The server list is in effect
    assertEquals(buildRoundRobinList(serverList), loadBalancer.getRoundRobinServerList().getList());

    // Simulate LB server responds another server list
    serverList = createResolvedServerInfoList(4002, 4003);
    loadBalancer.getLbResponseObserver().onNext(buildLbResponse(serverList));

    // The new list is in effect
    assertEquals(buildRoundRobinList(serverList), loadBalancer.getRoundRobinServerList().getList());
  }

  @Test public void newLbAddressesResolved() throws Exception {
    ResolvedServerInfo lbServerInfo = new ResolvedServerInfo(
        new InetSocketAddress("127.0.0.1", 30001), Attributes.EMPTY);
    EquivalentAddressGroup lbAddressGroup = buildAddressGroup(lbServerInfo);
    SettableFuture<ClientTransport> lbTransportFuture = SettableFuture.create();
    when(mockTransportManager.getTransport(eq(lbAddressGroup))).thenReturn(lbTransportFuture);

    // Simulate the initial set of LB addresses resolved
    loadBalancer.handleResolvedAddresses(Collections.singletonList(lbServerInfo), Attributes.EMPTY);

    // Make the transport for LB server ready
    ClientTransport lbTransport = mock(ClientTransport.class);
    lbTransportFuture.set(lbTransport);

    // An LB request is sent
    SendLbRequestArgs sentLbRequest = loadBalancer.sentLbRequests.poll(1000, TimeUnit.SECONDS);
    assertNotNull(sentLbRequest);
    assertSame(lbTransport, sentLbRequest.transport);
    verify(mockTransportManager).getTransport(eq(lbAddressGroup));

    // Simulate a second set of LB addresses resolved
    lbServerInfo = new ResolvedServerInfo(
        new InetSocketAddress("127.0.0.1", 30002), Attributes.EMPTY);
    lbAddressGroup = buildAddressGroup(lbServerInfo);
    lbTransportFuture = SettableFuture.create();
    when(mockTransportManager.getTransport(eq(lbAddressGroup))).thenReturn(lbTransportFuture);
    loadBalancer.handleResolvedAddresses(Collections.singletonList(lbServerInfo), Attributes.EMPTY);

    lbTransport = mock(ClientTransport.class);
    lbTransportFuture.set(lbTransport);

    // Another LB request is sent
    sentLbRequest = loadBalancer.sentLbRequests.poll(1000, TimeUnit.SECONDS);
    assertNotNull(sentLbRequest);
    assertSame(lbTransport, sentLbRequest.transport);
    verify(mockTransportManager).getTransport(eq(lbAddressGroup));

    // Simulate that an identical set of LB addresses is resolved
    lbServerInfo = new ResolvedServerInfo(
        new InetSocketAddress("127.0.0.1", 30002), Attributes.EMPTY);
    loadBalancer.handleResolvedAddresses(Collections.singletonList(lbServerInfo), Attributes.EMPTY);

    // Nothing will happen
    verifyNoMoreInteractions(mockTransportManager);
  }

  @Test public void lbStreamErrorAfterResponse() throws Exception {
    ResolvedServerInfo lbServerInfo = new ResolvedServerInfo(
        new InetSocketAddress("127.0.0.1", 30001), Attributes.EMPTY);
    EquivalentAddressGroup lbAddressGroup = buildAddressGroup(lbServerInfo);
    SettableFuture<ClientTransport> lbTransportFuture = SettableFuture.create();
    when(mockTransportManager.getTransport(eq(lbAddressGroup))).thenReturn(lbTransportFuture);

    // Simulate the initial set of LB addresses resolved
    loadBalancer.handleResolvedAddresses(Collections.singletonList(lbServerInfo), Attributes.EMPTY);

    // Make the transport for LB server ready
    lbTransportFuture.set(mock(ClientTransport.class));

    // An LB request is sent
    assertNotNull(loadBalancer.sentLbRequests.poll(1000, TimeUnit.SECONDS));

    // Simulate LB server responds a server list
    List<ResolvedServerInfo> serverList = createResolvedServerInfoList(4000, 4001);
    loadBalancer.getLbResponseObserver().onNext(buildLbResponse(serverList));
    assertEquals(buildRoundRobinList(serverList), loadBalancer.getRoundRobinServerList().getList());

    // Simulate a stream error
    loadBalancer.getLbResponseObserver().onError(
        Status.UNAVAILABLE.withDescription("simulated").asException());

    // Another LB request is sent
    assertNotNull(loadBalancer.sentLbRequests.poll(1000, TimeUnit.SECONDS));

    // Simulate LB server responds a new list
    serverList = createResolvedServerInfoList(4002, 4003);
    loadBalancer.getLbResponseObserver().onNext(buildLbResponse(serverList));
    assertEquals(buildRoundRobinList(serverList), loadBalancer.getRoundRobinServerList().getList());
  }

  @Test public void lbStreamErrorWithoutResponse() throws Exception {
    // Set up mocks
    ResolvedServerInfo lbServerInfo = new ResolvedServerInfo(
        new InetSocketAddress("127.0.0.1", 30001), Attributes.EMPTY);
    EquivalentAddressGroup lbAddressGroup = buildAddressGroup(lbServerInfo);
    ClientTransport lbTransport = mock(ClientTransport.class);
    SettableFuture<ClientTransport> lbTransportFuture = SettableFuture.create();
    when(mockTransportManager.getTransport(eq(lbAddressGroup))).thenReturn(lbTransportFuture);

    // Name resolved
    loadBalancer.handleResolvedAddresses(Collections.singletonList(lbServerInfo), Attributes.EMPTY);

    // First pick, will be pending
    ListenableFuture<ClientTransport> pick0 = loadBalancer.pickTransport(null);

    // Make the transport for LB server ready
    lbTransportFuture.set(lbTransport);

    // An LB request is sent
    SendLbRequestArgs sentLbRequest = loadBalancer.sentLbRequests.poll(1000, TimeUnit.SECONDS);
    assertNotNull(sentLbRequest);
    assertSame(lbTransport, sentLbRequest.transport);

    // Simulate that the LB stream fails
    loadBalancer.getLbResponseObserver().onError(
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

    // Round-robin list not available at this point
    assertNull(loadBalancer.getRoundRobinServerList());
  }

  @Test public void lbConnectionClosedAfterResponse() throws Exception {
    ResolvedServerInfo lbServerInfo = new ResolvedServerInfo(
        new InetSocketAddress("127.0.0.1", 30001), Attributes.EMPTY);
    EquivalentAddressGroup lbAddressGroup = buildAddressGroup(lbServerInfo);
    SettableFuture<ClientTransport> lbTransportFuture = SettableFuture.create();
    when(mockTransportManager.getTransport(eq(lbAddressGroup))).thenReturn(lbTransportFuture);

    // Simulate the initial set of LB addresses resolved
    loadBalancer.handleResolvedAddresses(Collections.singletonList(lbServerInfo), Attributes.EMPTY);

    verify(mockTransportManager).getTransport(eq(lbAddressGroup));

    // Make the transport for LB server ready
    ClientTransport lbTransport = mock(ClientTransport.class);
    lbTransportFuture.set(lbTransport);

    // An LB request is sent
    assertNotNull(loadBalancer.sentLbRequests.poll(1000, TimeUnit.SECONDS));

    // Simulate LB server responds a server list
    List<ResolvedServerInfo> serverList = createResolvedServerInfoList(4000, 4001);
    loadBalancer.getLbResponseObserver().onNext(buildLbResponse(serverList));
    assertEquals(buildRoundRobinList(serverList), loadBalancer.getRoundRobinServerList().getList());

    // Refresh mocks to prepare for the next getTransport() call
    lbTransportFuture = SettableFuture.create();
    when(mockTransportManager.getTransport(eq(lbAddressGroup))).thenReturn(lbTransportFuture);

    // Simulate transport closes
    loadBalancer.transportShutdown(lbAddressGroup, lbTransport, Status.UNAVAILABLE);

    // Will get another transport
    verify(mockTransportManager, times(2)).getTransport(eq(lbAddressGroup));

    // Make the new transport ready
    lbTransport = mock(ClientTransport.class);
    lbTransportFuture.set(lbTransport);

    // Another LB request is sent
    assertNotNull(loadBalancer.sentLbRequests.poll(1000, TimeUnit.SECONDS));
  }

  @Test public void lbConnectionClosedWithoutResponse() {
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
