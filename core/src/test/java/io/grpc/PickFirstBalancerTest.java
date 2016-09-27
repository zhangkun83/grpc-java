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

package io.grpc;

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

import io.grpc.TransportManager.InterimTransport;
import io.grpc.TransportManager.Subchannel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.SocketAddress;
import java.util.List;

/** Unit test for {@link PickFirstBalancerFactory}. */
@RunWith(JUnit4.class)
public class PickFirstBalancerTest {
  private LoadBalancer<Transport> loadBalancer;
  private NameResolver.Listener nameResolverListener;

  private List<ResolvedServerInfoGroup> servers;
  private EquivalentAddressGroup addressGroup;

  @Mock private TransportManager<Transport> mockTransportManager;
  @Mock private Transport mockTransport;
  @Mock private Subchannel<Transport> mockSubchannel;
  @Mock private InterimTransport<Transport> mockInterimTransport;
  @Mock private Transport mockInterimTransportAsTransport;
  @Mock private Transport mockFailingTransport;
  @Captor private ArgumentCaptor<Supplier<Transport>> transportSupplierCaptor;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    loadBalancer = PickFirstBalancerFactory.getInstance().newLoadBalancer(
        "fakeservice", mockTransportManager);
    nameResolverListener = loadBalancer.getNameResolverListener();
    servers = Lists.newArrayList();
    List<ResolvedServerInfo> resolvedServerInfoList = Lists.newArrayList();
    for (int i = 0; i < 3; i++) {
      resolvedServerInfoList.add(new ResolvedServerInfo(new FakeSocketAddress("server" + i)));
    }
    ResolvedServerInfoGroup resolvedServerInfoGroup = ResolvedServerInfoGroup.builder().addAll(
        resolvedServerInfoList).build();
    servers.add(resolvedServerInfoGroup);
    addressGroup = resolvedServerInfoGroup.toEquivalentAddressGroup();
    when(mockTransportManager.createSubchannel(eq(addressGroup))).thenReturn(mockSubchannel);
    when(mockSubchannel.getTransport()).thenReturn(mockTransport);
    when(mockTransportManager.createInterimTransport()).thenReturn(mockInterimTransport);
    when(mockTransportManager.createFailingTransport(any(Status.class)))
        .thenReturn(mockFailingTransport);
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
    verify(mockInterimTransport).closeWithRealTransports(transportSupplierCaptor.capture());
    for (int i = 0; i < 2; i++) {
      assertSame(mockTransport, transportSupplierCaptor.getValue().get());
    }
    verify(mockTransportManager).createSubchannel(eq(addressGroup));
    verify(mockSubchannel, times(2)).getTransport();
    verifyNoMoreInteractions(mockTransportManager);
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
  public void pickAfterShutdown() {
    loadBalancer.shutdown();
    Transport t1 = loadBalancer.pickTransport(null);
    verify(mockTransportManager).createFailingTransport(any(Status.class));
    verifyNoMoreInteractions(mockTransportManager);
  }

  @Test
  public void nameResolvedAfterShutdown() {
    loadBalancer.shutdown();
    nameResolverListener.onUpdate(servers, Attributes.EMPTY);
    Transport t = loadBalancer.pickTransport(null);
    verify(mockTransportManager).createFailingTransport(any(Status.class));
    assertSame(mockFailingTransport, t);
  }

  @Test
  public void pickAfterResolved() throws Exception {
    nameResolverListener.onUpdate(servers, Attributes.EMPTY);
    Transport t = loadBalancer.pickTransport(null);
    assertSame(mockTransport, t);
    verify(mockTransportManager).createSubchannel(addressGroup);
    verify(mockSubchannel).getTransport();
    verifyNoMoreInteractions(mockTransportManager);
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
