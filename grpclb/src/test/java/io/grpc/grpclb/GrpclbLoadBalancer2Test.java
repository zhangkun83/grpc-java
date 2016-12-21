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

package io.grpc.grpclb;

import static io.grpc.ConnectivityState.READY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.grpc.Attributes;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ConnectivityStateInfo;
import io.grpc.EquivalentAddressGroup;
import io.grpc.LoadBalancer2.Helper;
import io.grpc.LoadBalancer2.PickResult;
import io.grpc.LoadBalancer2.Subchannel;
import io.grpc.LoadBalancer2.SubchannelPicker;
import io.grpc.LoadBalancer2;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ResolvedServerInfo;
import io.grpc.ResolvedServerInfoGroup;
import io.grpc.Status;
import io.grpc.grpclb.GrpclbConstants.LbPolicy;
import io.grpc.grpclb.GrpclbLoadBalancer2.ErrorPicker;
import io.grpc.grpclb.GrpclbLoadBalancer2.RoundRobinPicker;
import io.grpc.internal.ObjectPool;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

/** Unit tests for {@link GrpclbLoadBalancer2}. */
@RunWith(JUnit4.class)
public class GrpclbLoadBalancer2Test {
  private static final Attributes.Key<String> RESOLUTION_ATTR =
      Attributes.Key.of("resolution-attr");
  private static final String SERVICE_AUTHORITY = "api.google.com";
  @Mock
  private Helper helper;
  @Mock
  private Subchannel mockSubchannel;
  @Mock
  private ManagedChannel mockOobChannel;
  @Mock
  private ClientCall<LoadBalanceRequest, LoadBalanceResponse> mockLbCall;
  @Captor
  private ArgumentCaptor<SubchannelPicker> pickerCaptor;
  private final FakeExecutor executor = new FakeExecutor();
  private final Metadata headers = new Metadata();
  @Mock
  private ObjectPool<Executor> executorPool;
  @Mock
  private LoadBalancer2.Factory pickFirstBalancerFactory;
  @Mock
  private LoadBalancer2 pickFirstBalancer;
  @Mock
  private LoadBalancer2.Factory roundRobinBalancerFactory;
  @Mock
  private LoadBalancer2 roundRobinBalancer;
  private GrpclbLoadBalancer2 balancer;

  @SuppressWarnings("unchecked")
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(executorPool.getObject()).thenReturn(executor);
    when(pickFirstBalancerFactory.newLoadBalancer(any(Helper.class)))
        .thenReturn(pickFirstBalancer);
    when(roundRobinBalancerFactory.newLoadBalancer(any(Helper.class)))
        .thenReturn(roundRobinBalancer);
    when(helper.createOobChannel(
            any(EquivalentAddressGroup.class), any(String.class), any(Executor.class)))
        .thenReturn(mockOobChannel);
    when(helper.getAuthority()).thenReturn(SERVICE_AUTHORITY);
    when(mockOobChannel.newCall(any(MethodDescriptor.class), any(CallOptions.class)))
        .thenReturn(mockLbCall);
    balancer = new GrpclbLoadBalancer2(helper, executorPool, pickFirstBalancerFactory,
        roundRobinBalancerFactory);
  }

  @Test
  public void errorPicker() {
    Status error = Status.UNAVAILABLE.withDescription("Just don't know why");
    ErrorPicker picker = new ErrorPicker(error);
    assertSame(error, picker.pickSubchannel(Attributes.EMPTY, headers).getStatus());
  }

  @Test
  public void roundRobinPicker() {
    PickResult pr1 = PickResult.withError(Status.UNAVAILABLE.withDescription("Just error"));
    PickResult pr2 = PickResult.withSubchannel(mockSubchannel);
    List<PickResult> list = Arrays.asList(pr1, pr2);
    RoundRobinPicker picker = new RoundRobinPicker(list);
    assertSame(pr1, picker.pickSubchannel(Attributes.EMPTY, headers));
    assertSame(pr2, picker.pickSubchannel(Attributes.EMPTY, headers));
    assertSame(pr1, picker.pickSubchannel(Attributes.EMPTY, headers));
  }

  @Test
  public void bufferPicker() {
    assertEquals(PickResult.withNoResult(),
        GrpclbLoadBalancer2.BUFFER_PICKER.pickSubchannel(Attributes.EMPTY, headers));
  }

  @Test
  public void nameResolutionFailsThenRecoverToDelegate() {
    Status error = Status.NOT_FOUND.withDescription("www.google.com not found");
    balancer.handleNameResolutionError(error);
    verify(helper).updatePicker(pickerCaptor.capture());
    ErrorPicker errorPicker = (ErrorPicker) pickerCaptor.getValue();
    assertSame(error, errorPicker.result.getStatus());

    // Recover with a subsequent success
    List<ResolvedServerInfoGroup> resolvedServers = createResolvedServerInfoGroupList(false);
    EquivalentAddressGroup eag = resolvedServers.get(0).toEquivalentAddressGroup();

    Attributes resolutionAttrs = Attributes.newBuilder().set(RESOLUTION_ATTR, "yeah").build();
    balancer.handleResolvedAddresses(resolvedServers, resolutionAttrs);

    verify(pickFirstBalancerFactory).newLoadBalancer(helper);
    verify(pickFirstBalancer).handleResolvedAddresses(eq(resolvedServers), eq(resolutionAttrs));
    verifyNoMoreInteractions(roundRobinBalancerFactory);
    verifyNoMoreInteractions(roundRobinBalancer);
  }

  @Test
  public void nameResolutionFailsThenRecoverToGrpclb() {
  }

  @Test
  public void delegatingPickFirstThenNameResolutionFails() {
    List<ResolvedServerInfoGroup> resolvedServers = createResolvedServerInfoGroupList(false);

    Attributes resolutionAttrs = Attributes.newBuilder().set(RESOLUTION_ATTR, "yeah").build();
    balancer.handleResolvedAddresses(resolvedServers, resolutionAttrs);

    verify(pickFirstBalancerFactory).newLoadBalancer(helper);
    verify(pickFirstBalancer).handleResolvedAddresses(eq(resolvedServers), eq(resolutionAttrs));

    // Then let name resolution fail.  The error will be passed directly to the delegate.
    Status error = Status.NOT_FOUND.withDescription("www.google.com not found");
    balancer.handleNameResolutionError(error);
    verify(pickFirstBalancer).handleNameResolutionError(error);
    verify(helper, never()).updatePicker(any(SubchannelPicker.class));
    verifyNoMoreInteractions(roundRobinBalancerFactory);
    verifyNoMoreInteractions(roundRobinBalancer);
  }

  @Test
  public void delegatingRoundRobinThenNameResolutionFails() {
    List<ResolvedServerInfoGroup> resolvedServers = createResolvedServerInfoGroupList(false, false);

    Attributes resolutionAttrs = Attributes.newBuilder()
        .set(RESOLUTION_ATTR, "yeah")
        .set(GrpclbConstants.ATTR_LB_POLICY, LbPolicy.ROUND_ROBIN)
        .build();
    balancer.handleResolvedAddresses(resolvedServers, resolutionAttrs);

    verify(roundRobinBalancerFactory).newLoadBalancer(helper);
    verify(roundRobinBalancer).handleResolvedAddresses(resolvedServers, resolutionAttrs);

    // Then let name resolution fail.  The error will be passed directly to the delegate.
    Status error = Status.NOT_FOUND.withDescription("www.google.com not found");
    balancer.handleNameResolutionError(error);
    verify(roundRobinBalancer).handleNameResolutionError(error);
    verify(helper, never()).updatePicker(any(SubchannelPicker.class));
    verifyNoMoreInteractions(pickFirstBalancerFactory);
    verifyNoMoreInteractions(pickFirstBalancer);
  }

  @Test
  public void grpclbThenNameResolutionFails() {
  }

  @SuppressWarnings("unchecked")
  @Test
  public void switchPolicy() {
    // Go to GRPCLB first
    List<ResolvedServerInfoGroup> grpclbResolutionList =
        createResolvedServerInfoGroupList(true, false, true);
    Attributes grpclbResolutionAttrs = Attributes.newBuilder()
        .set(GrpclbConstants.ATTR_LB_POLICY, LbPolicy.GRPCLB).build();
    balancer.handleResolvedAddresses(grpclbResolutionList, grpclbResolutionAttrs);

    assertSame(LbPolicy.GRPCLB, balancer.getLbPolicy());
    assertNull(balancer.getDelegate());
    verify(helper).createOobChannel(eq(grpclbResolutionList.get(0).toEquivalentAddressGroup()),
        eq("lb0.google.com"), same(executor));
    verify(helper).createOobChannel(any(EquivalentAddressGroup.class), any(String.class),
        any(Executor.class));
    verify(mockOobChannel).newCall(same(LoadBalancerGrpc.METHOD_BALANCE_LOAD), eq(CallOptions.DEFAULT));
    verify(mockOobChannel).newCall(any(MethodDescriptor.class), any(CallOptions.class));

    // Switch to PICK_FIRST
    List<ResolvedServerInfoGroup> pickFirstResolutionList =
        createResolvedServerInfoGroupList(true, false, true);
    Attributes pickFirstResolutionAttrs = Attributes.newBuilder()
        .set(GrpclbConstants.ATTR_LB_POLICY, LbPolicy.PICK_FIRST).build();
    verify(pickFirstBalancerFactory, never()).newLoadBalancer(any(Helper.class));
    verify(mockLbCall, never()).halfClose();
    verify(mockOobChannel, never()).shutdown();
    verify(mockOobChannel, never()).shutdownNow();
    balancer.handleResolvedAddresses(pickFirstResolutionList, pickFirstResolutionAttrs);

    verify(pickFirstBalancerFactory).newLoadBalancer(same(helper));
    // Only non-LB addresses are passed to the delegate
    verify(pickFirstBalancer).handleResolvedAddresses(
        eq(Arrays.asList(pickFirstResolutionList.get(1))), same(pickFirstResolutionAttrs));
    assertSame(LbPolicy.PICK_FIRST, balancer.getLbPolicy());
    assertSame(pickFirstBalancer, balancer.getDelegate());
    // GRPCLB connection is closed
    verify(mockLbCall).halfClose();
    verify(mockOobChannel).shutdown();

    // Switch to ROUND_ROBIN
    List<ResolvedServerInfoGroup> roundRobinResolutionList =
        createResolvedServerInfoGroupList(true, false, false);
    Attributes roundRobinResolutionAttrs = Attributes.newBuilder()
        .set(GrpclbConstants.ATTR_LB_POLICY, LbPolicy.ROUND_ROBIN).build();
    verify(roundRobinBalancerFactory, never()).newLoadBalancer(any(Helper.class));
    balancer.handleResolvedAddresses(roundRobinResolutionList, roundRobinResolutionAttrs);

    verify(roundRobinBalancerFactory).newLoadBalancer(same(helper));
    // Only non-LB addresses are passed to the delegate
    verify(roundRobinBalancer).handleResolvedAddresses(
        eq(roundRobinResolutionList.subList(1, 3)), same(roundRobinResolutionAttrs));
    assertSame(LbPolicy.ROUND_ROBIN, balancer.getLbPolicy());
    assertSame(roundRobinBalancer, balancer.getDelegate());
    
    // Special case: if all addresses are loadbalancers, use GRPCLB no matter what the NameResolver
    // says.
    grpclbResolutionList = createResolvedServerInfoGroupList(true, true, true);
    grpclbResolutionAttrs = Attributes.newBuilder()
        .set(GrpclbConstants.ATTR_LB_POLICY, LbPolicy.PICK_FIRST).build();
    balancer.handleResolvedAddresses(grpclbResolutionList, grpclbResolutionAttrs);

    assertSame(LbPolicy.GRPCLB, balancer.getLbPolicy());
    assertNull(balancer.getDelegate());
    verify(helper, times(2)).createOobChannel(
        eq(grpclbResolutionList.get(0).toEquivalentAddressGroup()),
        eq("lb0.google.com"), same(executor));
    verify(helper).createOobChannel(any(EquivalentAddressGroup.class), any(String.class),
        any(Executor.class));
    verify(mockOobChannel, times(2)).newCall(
        same(LoadBalancerGrpc.METHOD_BALANCE_LOAD), eq(CallOptions.DEFAULT));
    verify(mockOobChannel, times(2)).newCall(any(MethodDescriptor.class), any(CallOptions.class));

    // Special case: PICK_FIRST is the default
    pickFirstResolutionList = createResolvedServerInfoGroupList(true, false, false);
    pickFirstResolutionAttrs = Attributes.EMPTY;
    verify(pickFirstBalancerFactory, never()).newLoadBalancer(any(Helper.class));
    verify(mockLbCall).halfClose();
    verify(mockOobChannel).shutdown();
    verify(mockOobChannel, never()).shutdownNow();
    balancer.handleResolvedAddresses(pickFirstResolutionList, pickFirstResolutionAttrs);

    verify(pickFirstBalancerFactory).newLoadBalancer(same(helper));
    // Only non-LB addresses are passed to the delegate
    verify(pickFirstBalancer).handleResolvedAddresses(
        eq(pickFirstResolutionList.subList(1, 3)), same(pickFirstResolutionAttrs));
    assertSame(LbPolicy.PICK_FIRST, balancer.getLbPolicy());
    assertSame(pickFirstBalancer, balancer.getDelegate());
    // GRPCLB connection is closed
    verify(mockLbCall, times(2)).halfClose();
    verify(mockOobChannel, times(2)).shutdown();
  }

  @Test
  public void grpclbWorking() {
  }

  @Test
  public void grpclbFailedToConnect() {
  }

  @Test
  public void grpclbLbStreamFails() {
  }

  private List<ResolvedServerInfoGroup> createResolvedServerInfoGroupList(boolean ... isLb) {
    ArrayList<ResolvedServerInfoGroup> list = new ArrayList<ResolvedServerInfoGroup>();
    for (int i = 0; i < isLb.length; i++) {
      SocketAddress addr = new FakeSocketAddress("fake-address-" + i);
      ResolvedServerInfoGroup serverInfoGroup = ResolvedServerInfoGroup
          .builder(isLb[i] ? Attributes.newBuilder()
              .set(GrpclbConstants.ATTR_LB_ADDR_AUTHORITY, "lb" + i + ".google.com")
              .build()
              : Attributes.EMPTY)
          .add(new ResolvedServerInfo(addr))
          .build();
      list.add(serverInfoGroup);
    }
    return list;
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

  private static class FakeExecutor implements Executor {
    @Override
    public void execute(Runnable runnable) {
      runnable.run();
    }
  }
}
