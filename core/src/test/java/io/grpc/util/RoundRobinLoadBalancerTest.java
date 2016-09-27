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

package io.grpc.util;

import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.base.Supplier;
import com.google.common.collect.Lists;

import io.grpc.Attributes;
import io.grpc.EquivalentAddressGroup;
import io.grpc.LoadBalancer;
import io.grpc.NameResolver;
import io.grpc.ResolvedServerInfo;
import io.grpc.ResolvedServerInfoGroup;
import io.grpc.Status;
import io.grpc.TransportManager;
import io.grpc.TransportManager.InterimTransport;
import io.grpc.TransportManager.Subchannel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.net.SocketAddress;
import java.util.List;

/** Unit test for {@link RoundRobinLoadBalancerFactory}. */
@RunWith(JUnit4.class)
public class RoundRobinLoadBalancerTest {
  private LoadBalancer<Transport> loadBalancer;
  private NameResolver.Listener nameResolverListener;

  private List<ResolvedServerInfoGroup> servers;
  private List<EquivalentAddressGroup> addressGroupList;

  @Mock private TransportManager<Transport> mockTransportManager;
  @Mock private Subchannel<Transport> mockSubchannel0;
  @Mock private Subchannel<Transport> mockSubchannel1;
  @Mock private Subchannel<Transport> mockSubchannel2;
  @Mock private Transport mockTransport0;
  @Mock private Transport mockTransport1;
  @Mock private Transport mockTransport2;
  @Mock private InterimTransport<Transport> mockInterimTransport;
  @Mock private Transport mockInterimTransportAsTransport;
  @Captor private ArgumentCaptor<Supplier<Transport>> transportSupplierCaptor;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    loadBalancer = RoundRobinLoadBalancerFactory.getInstance().newLoadBalancer(
        "fakeservice", mockTransportManager);
    nameResolverListener = loadBalancer.getNameResolverListener();
    addressGroupList = Lists.newArrayList();
    servers = Lists.newArrayList();
    for (int i = 0; i < 3; i++) {
      ResolvedServerInfoGroup.Builder resolvedServerInfoGroup = ResolvedServerInfoGroup.builder();
      for (int j = 0; j < 3; j++) {
        resolvedServerInfoGroup.add(
            new ResolvedServerInfo(new FakeSocketAddress("servergroup" + i + "server" + j)));
      }
      servers.add(resolvedServerInfoGroup.build());
      addressGroupList.add(resolvedServerInfoGroup.build().toEquivalentAddressGroup());
    }
    when(mockTransportManager.createSubchannel(eq(addressGroupList.get(0))))
        .thenReturn(mockSubchannel0);
    when(mockTransportManager.createSubchannel(eq(addressGroupList.get(1))))
        .thenReturn(mockSubchannel1);
    when(mockTransportManager.createSubchannel(eq(addressGroupList.get(2))))
        .thenReturn(mockSubchannel2);
    when(mockSubchannel0.getTransport()).thenReturn(mockTransport0);
    when(mockSubchannel1.getTransport()).thenReturn(mockTransport1);
    when(mockSubchannel2.getTransport()).thenReturn(mockTransport2);
    when(mockTransportManager.createInterimTransport()).thenReturn(mockInterimTransport);
    when(mockInterimTransport.transport()).thenReturn(mockInterimTransportAsTransport);
  }

  @Test
  public void pickBeforeResolved() throws Exception {
    Transport t1 = loadBalancer.pickTransport(null);
    Transport t2 = loadBalancer.pickTransport(null);
    assertSame(mockInterimTransportAsTransport, t1);
    assertSame(mockInterimTransportAsTransport, t2);
    verify(mockTransportManager).createInterimTransport();
    verify(mockTransportManager, never()).createSubchannel(any(EquivalentAddressGroup.class));
    verify(mockInterimTransport, times(2)).transport();

    nameResolverListener.onUpdate(servers, Attributes.EMPTY);
    verify(mockTransportManager).createSubchannel(eq(addressGroupList.get(0)));
    verify(mockTransportManager).createSubchannel(eq(addressGroupList.get(1)));
    verify(mockTransportManager, never()).createSubchannel(eq(addressGroupList.get(2)));

    verify(mockInterimTransport).closeWithRealTransports(transportSupplierCaptor.capture());
    assertSame(mockTransport0, transportSupplierCaptor.getValue().get());
    assertSame(mockTransport1, transportSupplierCaptor.getValue().get());
    InOrder inOrder = Mockito.inOrder(mockSubchannel0, mockSubchannel1, mockSubchannel2);
    inOrder.verify(mockSubchannel0).getTransport();
    inOrder.verify(mockSubchannel1).getTransport();
    inOrder.verifyNoMoreInteractions();
    verifyNoMoreInteractions(mockInterimTransport);
  }

  @Test
  public void pickBeforeNameResolutionError() {
    Transport t1 = loadBalancer.pickTransport(null);
    Transport t2 = loadBalancer.pickTransport(null);
    assertSame(mockInterimTransportAsTransport, t1);
    assertSame(mockInterimTransportAsTransport, t2);
    verify(mockTransportManager).createInterimTransport();
    verify(mockTransportManager, never()).createSubchannel(any(EquivalentAddressGroup.class));
    verify(mockInterimTransport, times(2)).transport();

    nameResolverListener.onError(Status.UNAVAILABLE);
    verify(mockInterimTransport).closeWithError(any(Status.class));
    // Ensure a shutdown after error closes without incident
    loadBalancer.shutdown();
    // Ensure a name resolution error after shutdown does nothing
    nameResolverListener.onError(Status.UNAVAILABLE);
    verifyNoMoreInteractions(mockInterimTransport);
  }

  @Test
  public void pickBeforeShutdown() {
    Transport t1 = loadBalancer.pickTransport(null);
    Transport t2 = loadBalancer.pickTransport(null);
    assertSame(mockInterimTransportAsTransport, t1);
    assertSame(mockInterimTransportAsTransport, t2);
    verify(mockTransportManager).createInterimTransport();
    verify(mockTransportManager, never()).createSubchannel(any(EquivalentAddressGroup.class));
    verify(mockInterimTransport, times(2)).transport();

    loadBalancer.shutdown();
    verify(mockInterimTransport).closeWithError(any(Status.class));
    // Ensure double shutdown just returns immediately without closing again.
    loadBalancer.shutdown();
    verifyNoMoreInteractions(mockInterimTransport);
  }

  @Test
  public void pickAfterResolved() throws Exception {
    nameResolverListener.onUpdate(servers, Attributes.EMPTY);
    verify(mockTransportManager).createSubchannel(eq(addressGroupList.get(0)));
    verify(mockTransportManager).createSubchannel(eq(addressGroupList.get(1)));
    verify(mockTransportManager).createSubchannel(eq(addressGroupList.get(2)));
    InOrder inOrder = Mockito.inOrder(mockSubchannel0, mockSubchannel1, mockSubchannel2);
    for (int i = 0; i < 100; i++) {
      assertSame(mockTransport0, loadBalancer.pickTransport(null));
      inOrder.verify(mockSubchannel0).getTransport();
      assertSame(mockTransport1, loadBalancer.pickTransport(null));
      inOrder.verify(mockSubchannel1).getTransport();
      assertSame(mockTransport2, loadBalancer.pickTransport(null));
      inOrder.verify(mockSubchannel2).getTransport();
    }
    inOrder.verifyNoMoreInteractions();
  }

  private static class FakeSocketAddress extends SocketAddress {
    final String name;

    FakeSocketAddress(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return "FakeSocketAddress-" + name;
    }
  }

  private static class Transport {}
}
