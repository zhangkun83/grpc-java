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

package io.grpc;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;

import io.grpc.LoadBalancer2.Helper;
import io.grpc.LoadBalancer2.PickResult;
import io.grpc.LoadBalancer2.Subchannel;
import io.grpc.PickFirstBalancerFactory2.PickFirstBalancer;
import io.grpc.PickFirstBalancerFactory2.Picker;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.SocketAddress;
import java.util.List;


/** Unit test for {@link PickFirstBalancerFactory2}. */
@RunWith(JUnit4.class)
public class PickFirstLoadBalancer2Test {
  private PickFirstBalancer loadBalancer;
  private List<ResolvedServerInfoGroup> servers = Lists.newArrayList();
  private List<SocketAddress> socketAddresses = Lists.newArrayList();

  private static Attributes.Key<String> FOO = Attributes.Key.of("foo");
  private Attributes affinity = Attributes.newBuilder().set(FOO, "bar").build();

  @Captor
  private ArgumentCaptor<EquivalentAddressGroup> eagCaptor;
  @Captor
  private ArgumentCaptor<Picker> pickerCaptor;
  @Captor
  private ArgumentCaptor<Attributes> attrsCaptor;
  @Mock
  private Helper mockHelper;
  @Mock
  private Subchannel mockSubchannel;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    for (int i = 0; i < 3; i++) {
      SocketAddress addr = new FakeSocketAddress("server" + i);
      servers.add(ResolvedServerInfoGroup.builder().add(new ResolvedServerInfo(addr)).build());
      socketAddresses.add(addr);
    }

    when(mockSubchannel.getAddresses()).thenReturn(new EquivalentAddressGroup(socketAddresses));
    when(mockHelper.createSubchannel(any(EquivalentAddressGroup.class), any(Attributes.class)))
        .thenReturn(mockSubchannel);

    loadBalancer = (PickFirstBalancer) PickFirstBalancerFactory2.getInstance().newLoadBalancer(
        mockHelper);
  }

  @Test
  public void pickAfterResolved() throws Exception {
    loadBalancer.handleResolvedAddresses(servers, affinity);

    verify(mockHelper).createSubchannel(eagCaptor.capture(), attrsCaptor.capture());
    verify(mockHelper).updatePicker(pickerCaptor.capture());

    assertEquals(new EquivalentAddressGroup(socketAddresses), eagCaptor.getValue());
    assertEquals(pickerCaptor.getValue().pickSubchannel(affinity, new Metadata()),
        pickerCaptor.getValue().pickSubchannel(affinity, new Metadata()));

    verifyNoMoreInteractions(mockHelper);
  }

  @Test
  public void pickAfterResolvedAndUnchanged() throws Exception {
    loadBalancer.handleResolvedAddresses(servers, affinity);
    loadBalancer.handleResolvedAddresses(servers, affinity);

    verify(mockHelper).createSubchannel(any(EquivalentAddressGroup.class),
        any(Attributes.class));
    verify(mockHelper).updatePicker(isA(Picker.class));

    verifyNoMoreInteractions(mockHelper);
  }

  @Test
  public void pickAfterResolvedAndChanged() throws Exception {
    SocketAddress socketAddr = new FakeSocketAddress("newserver");
    List<SocketAddress> newSocketAddresses = Lists.newArrayList(socketAddr);
    List<ResolvedServerInfoGroup> newServers = Lists.newArrayList(
        ResolvedServerInfoGroup.builder().add(new ResolvedServerInfo(socketAddr)).build());

    final Subchannel oldSubchannel = mock(Subchannel.class);
    final EquivalentAddressGroup oldEag = new EquivalentAddressGroup(socketAddresses);
    when(oldSubchannel.getAddresses()).thenReturn(oldEag);

    final Subchannel newSubchannel = mock(Subchannel.class);
    final EquivalentAddressGroup newEag = new EquivalentAddressGroup(newSocketAddresses);
    when(newSubchannel.getAddresses()).thenReturn(newEag);

    when(mockHelper.createSubchannel(eq(oldEag), any(Attributes.class))).thenReturn(oldSubchannel);
    when(mockHelper.createSubchannel(eq(newEag), any(Attributes.class))).thenReturn(newSubchannel);

    InOrder inOrder = inOrder(mockHelper);

    loadBalancer.handleResolvedAddresses(servers, affinity);
    inOrder.verify(mockHelper).createSubchannel(eagCaptor.capture(), any(Attributes.class));
    inOrder.verify(mockHelper).updatePicker(pickerCaptor.capture());
    assertEquals(socketAddresses, eagCaptor.getValue().getAddresses());

    loadBalancer.handleResolvedAddresses(newServers, affinity);
    inOrder.verify(mockHelper).createSubchannel(eagCaptor.capture(), any(Attributes.class));
    inOrder.verify(mockHelper).updatePicker(pickerCaptor.capture());
    assertEquals(newSocketAddresses, eagCaptor.getValue().getAddresses());

    Subchannel subchannel = pickerCaptor.getAllValues().get(0).pickSubchannel(
        affinity, new Metadata()).getSubchannel();
    assertEquals(oldSubchannel, subchannel);

    Subchannel subchannel2 = pickerCaptor.getAllValues().get(1).pickSubchannel(affinity,
        new Metadata()).getSubchannel();
    assertEquals(newSubchannel, subchannel2);
    verify(subchannel2, never()).shutdown();

    verifyNoMoreInteractions(mockHelper);
  }

  @Test
  public void stateChangeBeforeResolution() throws Exception {
    loadBalancer.handleSubchannelState(mockSubchannel,
        ConnectivityStateInfo.forNonError(ConnectivityState.READY));

    verifyNoMoreInteractions(mockHelper);
  }

  @Test
  public void pickAfterStateChangeAfterResolution() throws Exception {
    loadBalancer.handleResolvedAddresses(servers, affinity);
    verify(mockHelper).updatePicker(pickerCaptor.capture());
    Subchannel subchannel = pickerCaptor.getValue().pickSubchannel(affinity,
        new Metadata()).getSubchannel();
    reset(mockHelper);

    InOrder inOrder = inOrder(mockHelper);

    Status error = Status.UNAVAILABLE.withDescription("boom!");
    loadBalancer.handleSubchannelState(subchannel,
        ConnectivityStateInfo.forTransientFailure(error));
    inOrder.verify(mockHelper).updatePicker(pickerCaptor.capture());
    assertEquals(error, pickerCaptor.getValue().pickSubchannel(Attributes.EMPTY,
            new Metadata()).getStatus());

    loadBalancer.handleSubchannelState(subchannel,
        ConnectivityStateInfo.forNonError(ConnectivityState.IDLE));
    inOrder.verify(mockHelper).updatePicker(pickerCaptor.capture());
    assertEquals(Status.OK, pickerCaptor.getValue().pickSubchannel(Attributes.EMPTY,
        new Metadata()).getStatus());

    loadBalancer.handleSubchannelState(subchannel,
        ConnectivityStateInfo.forNonError(ConnectivityState.READY));
    inOrder.verify(mockHelper).updatePicker(pickerCaptor.capture());
    assertEquals(subchannel,
        pickerCaptor.getValue().pickSubchannel(Attributes.EMPTY, new Metadata()).getSubchannel());

    verifyNoMoreInteractions(mockHelper);
  }

  @Test
  public void nameResolutionError() throws Exception {
    Status error = Status.NOT_FOUND.withDescription("nameResolutionError");
    loadBalancer.handleNameResolutionError(error);
    verify(mockHelper).updatePicker(pickerCaptor.capture());
    PickResult pickResult = pickerCaptor.getValue().pickSubchannel(Attributes.EMPTY,
        new Metadata());
    assertEquals(null, pickResult.getSubchannel());
    assertEquals(error, pickResult.getStatus());
    verifyNoMoreInteractions(mockHelper);
  }

  @Test
  public void nameResolutionSuccessAfterError() throws Exception {
    InOrder inOrder = inOrder(mockHelper);

    loadBalancer.handleNameResolutionError(Status.NOT_FOUND.withDescription("nameResolutionError"));
    inOrder.verify(mockHelper).updatePicker(any(Picker.class));

    loadBalancer.handleResolvedAddresses(servers, affinity);
    inOrder.verify(mockHelper).createSubchannel(eq(new EquivalentAddressGroup(socketAddresses)),
        eq(Attributes.EMPTY));
    inOrder.verify(mockHelper).updatePicker(pickerCaptor.capture());

    assertEquals(mockSubchannel,
        pickerCaptor.getValue().pickSubchannel(Attributes.EMPTY, new Metadata()).getSubchannel());

    assertEquals(pickerCaptor.getValue().pickSubchannel(Attributes.EMPTY, new Metadata()),
        pickerCaptor.getValue().pickSubchannel(Attributes.EMPTY, new Metadata()));

    verifyNoMoreInteractions(mockHelper);
  }

  @Test
  public void nameResolutionErrorWithStateChanges() throws Exception {
    InOrder inOrder = inOrder(mockHelper);

    loadBalancer.handleSubchannelState(mockSubchannel,
        ConnectivityStateInfo.forTransientFailure(Status.UNAVAILABLE));
    Status error = Status.NOT_FOUND.withDescription("nameResolutionError");
    loadBalancer.handleNameResolutionError(error);
    inOrder.verify(mockHelper).updatePicker(pickerCaptor.capture());

    PickResult pickResult = pickerCaptor.getValue().pickSubchannel(Attributes.EMPTY,
        new Metadata());
    assertEquals(null, pickResult.getSubchannel());
    assertEquals(error, pickResult.getStatus());

    loadBalancer.handleSubchannelState(mockSubchannel,
        ConnectivityStateInfo.forNonError(ConnectivityState.READY));
    Status error2 = Status.NOT_FOUND.withDescription("nameResolutionError2");
    loadBalancer.handleNameResolutionError(error2);
    inOrder.verify(mockHelper).updatePicker(pickerCaptor.capture());

    pickResult = pickerCaptor.getValue().pickSubchannel(Attributes.EMPTY,
        new Metadata());
    assertEquals(null, pickResult.getSubchannel());
    assertEquals(error2, pickResult.getStatus());

    verifyNoMoreInteractions(mockHelper);
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
}
