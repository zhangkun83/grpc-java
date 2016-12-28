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

import static com.google.common.base.Charsets.UTF_8;
import static io.grpc.ConnectivityState.CONNECTING;
import static io.grpc.ConnectivityState.IDLE;
import static io.grpc.ConnectivityState.READY;
import static io.grpc.ConnectivityState.SHUTDOWN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.io.ByteStreams;
import com.google.protobuf.ByteString;

import io.grpc.Attributes;
import io.grpc.CallOptions;
import io.grpc.ConnectivityStateInfo;
import io.grpc.EquivalentAddressGroup;
import io.grpc.LoadBalancer2.Helper;
import io.grpc.LoadBalancer2.PickResult;
import io.grpc.LoadBalancer2.Subchannel;
import io.grpc.LoadBalancer2.SubchannelPicker;
import io.grpc.LoadBalancer2;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor.Marshaller;
import io.grpc.MethodDescriptor;
import io.grpc.ResolvedServerInfo;
import io.grpc.ResolvedServerInfoGroup;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.grpclb.GrpclbConstants.LbPolicy;
import io.grpc.grpclb.GrpclbLoadBalancer2.ErrorPicker;
import io.grpc.grpclb.GrpclbLoadBalancer2.RoundRobinPicker;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.internal.ObjectPool;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.StreamObserver;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;

/** Unit tests for {@link GrpclbLoadBalancer2}. */
@RunWith(JUnit4.class)
public class GrpclbLoadBalancer2Test {
  private static final Attributes.Key<String> RESOLUTION_ATTR =
      Attributes.Key.of("resolution-attr");
  private static final String SERVICE_AUTHORITY = "api.google.com";

  private static final MethodDescriptor<String, String> TRASH_METHOD = MethodDescriptor.create(
      MethodDescriptor.MethodType.UNARY, "/service/trashmethod",
      new StringMarshaller(), new StringMarshaller());

  private static class StringMarshaller implements Marshaller<String> {
    static final StringMarshaller INSTANCE = new StringMarshaller();

    @Override
    public InputStream stream(String value) {
      return new ByteArrayInputStream(value.getBytes(UTF_8));
    }

    @Override
    public String parse(InputStream stream) {
      try {
        return new String(ByteStreams.toByteArray(stream), UTF_8);
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }
  }

  @Mock
  private Helper helper;
  @Mock
  private Subchannel mockSubchannel;
  @Mock
  private LoadBalancerGrpc.LoadBalancerImplBase mockLbService;
  @Captor
  private ArgumentCaptor<StreamObserver<LoadBalanceResponse>> lbResponseObserverCaptor;
  @Mock
  private StreamObserver<LoadBalanceRequest> lbRequestObserver;
  private final LinkedList<Subchannel> mockSubchannels = new LinkedList<Subchannel>();
  private final LinkedList<ManagedChannel> fakeOobChannels = new LinkedList<ManagedChannel>();
  private final ArrayList<Subchannel> subchannelTracker = new ArrayList<Subchannel>();
  private final ArrayList<ManagedChannel> oobChannelTracker = new ArrayList<ManagedChannel>();
  private final ArrayList<String> failingLbAuthorities = new ArrayList<String>();
  private io.grpc.Server fakeLbServer;
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
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(executorPool.getObject()).thenReturn(executor);
    when(pickFirstBalancerFactory.newLoadBalancer(any(Helper.class)))
        .thenReturn(pickFirstBalancer);
    when(roundRobinBalancerFactory.newLoadBalancer(any(Helper.class)))
        .thenReturn(roundRobinBalancer);
    when(mockLbService.balanceLoad(any(StreamObserver.class))).thenReturn(lbRequestObserver);
    fakeLbServer = InProcessServerBuilder.forName("fakeLb")
        .directExecutor().addService(mockLbService).build().start();
    doAnswer(new Answer<ManagedChannel>() {
        @Override
        public ManagedChannel answer(InvocationOnMock invocation) throws Throwable {
          String authority = (String) invocation.getArguments()[1];
          ManagedChannel channel;
          if (failingLbAuthorities.contains(authority)) {
            channel = InProcessChannelBuilder.forName("nonExistFakeLb").directExecutor().build();
          } else {
            channel = InProcessChannelBuilder.forName("fakeLb").directExecutor().build();
          }
          // TODO(zhangkun83): #2444: non-determinism of Channel due to starting NameResolver on the
          // timer "Prime" it before use.  Remove it after #2444 is resolved.
          try {
            ClientCalls.blockingUnaryCall(channel, TRASH_METHOD, CallOptions.DEFAULT, "trash");
          } catch (StatusRuntimeException ignored) {
            // Ignored
          }
          fakeOobChannels.add(channel);
          oobChannelTracker.add(channel);
          return channel;
        }
      }).when(helper).createOobChannel(
          any(EquivalentAddressGroup.class), any(String.class), any(Executor.class));
    doAnswer(new Answer<Subchannel>() {
        @Override
        public Subchannel answer(InvocationOnMock invocation) throws Throwable {
          Subchannel subchannel = mock(Subchannel.class);
          EquivalentAddressGroup eag = (EquivalentAddressGroup) invocation.getArguments()[0];
          Attributes attrs = (Attributes) invocation.getArguments()[1];
          when(subchannel.getAddresses()).thenReturn(eag);
          when(subchannel.getAttributes()).thenReturn(attrs);
          mockSubchannels.add(subchannel);
          subchannelTracker.add(subchannel);
          return subchannel;
        }
      }).when(helper).createSubchannel(any(EquivalentAddressGroup.class), any(Attributes.class));
    doAnswer(new Answer<Void>() {
        @Override
        public Void answer(InvocationOnMock invocation) throws Throwable {
          Runnable task = (Runnable) invocation.getArguments()[0];
          task.run();
          return null;
        }
      }).when(helper).runSerialized(any(Runnable.class));
    when(helper.getAuthority()).thenReturn(SERVICE_AUTHORITY);
    balancer = new GrpclbLoadBalancer2(helper, executorPool, pickFirstBalancerFactory,
        roundRobinBalancerFactory);
  }

  @After
  public void tearDown() {
    if (balancer != null) {
      balancer.shutdown();
    }
    for (ManagedChannel channel : oobChannelTracker) {
      assertTrue(channel + " is shutdown", channel.isShutdown());
    }
    for (Subchannel subchannel: subchannelTracker) {
      verify(subchannel).shutdown();
    }
    fakeLbServer.shutdownNow();
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
    Status error = Status.NOT_FOUND.withDescription("www.google.com not found");
    balancer.handleNameResolutionError(error);
    verify(helper).updatePicker(pickerCaptor.capture());
    ErrorPicker errorPicker = (ErrorPicker) pickerCaptor.getValue();
    assertSame(error, errorPicker.result.getStatus());

    // Recover with a subsequent success
    List<ResolvedServerInfoGroup> resolvedServers = createResolvedServerInfoGroupList(true);
    EquivalentAddressGroup eag = resolvedServers.get(0).toEquivalentAddressGroup();

    Attributes resolutionAttrs = Attributes.newBuilder()
        .set(GrpclbConstants.ATTR_LB_POLICY, LbPolicy.GRPCLB).build();
    balancer.handleResolvedAddresses(resolvedServers, resolutionAttrs);

    assertSame(LbPolicy.GRPCLB, balancer.getLbPolicy());
    assertNull(balancer.getDelegate());
    verify(helper).createOobChannel(eq(eag), eq(lbAuthority(0)), same(executor));
    verify(mockLbService).balanceLoad(lbResponseObserverCaptor.capture());

    verifyNoMoreInteractions(pickFirstBalancerFactory);
    verifyNoMoreInteractions(pickFirstBalancer);
    verifyNoMoreInteractions(roundRobinBalancerFactory);
    verifyNoMoreInteractions(roundRobinBalancer);
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
    InOrder inOrder = inOrder(helper);
    // Go to GRPCLB first
    List<ResolvedServerInfoGroup> grpclbResolutionList =
        createResolvedServerInfoGroupList(true, true);
    Attributes grpclbResolutionAttrs = Attributes.newBuilder()
        .set(GrpclbConstants.ATTR_LB_POLICY, LbPolicy.GRPCLB).build();
    balancer.handleResolvedAddresses(grpclbResolutionList, grpclbResolutionAttrs);

    assertSame(LbPolicy.GRPCLB, balancer.getLbPolicy());
    assertNull(balancer.getDelegate());
    verify(helper).createOobChannel(eq(grpclbResolutionList.get(0).toEquivalentAddressGroup()),
        eq(lbAuthority(0)), same(executor));
    assertEquals(1, fakeOobChannels.size());
    ManagedChannel oobChannel = fakeOobChannels.poll();
    verify(mockLbService).balanceLoad(lbResponseObserverCaptor.capture());
    StreamObserver<LoadBalanceResponse> lbResponseObserver = lbResponseObserverCaptor.getValue();

    // Let name resolution fail before round-robin list is ready
    Status error = Status.NOT_FOUND.withDescription("www.google.com not found");
    balancer.handleNameResolutionError(error);

    inOrder.verify(helper).updatePicker(pickerCaptor.capture());
    ErrorPicker errorPicker = (ErrorPicker) pickerCaptor.getValue();
    assertSame(error, errorPicker.result.getStatus());

    // Simulate receiving LB response
    List<InetSocketAddress> backends = Arrays.asList(
        new InetSocketAddress("127.0.0.1", 2000),
        new InetSocketAddress("127.0.0.1", 2010));
    verify(helper, never()).runSerialized(any(Runnable.class));
    lbResponseObserver.onNext(buildInitialResponse());
    lbResponseObserver.onNext(buildLbResponse(backends));

    verify(helper, times(2)).runSerialized(any(Runnable.class));
    inOrder.verify(helper).createSubchannel(
        eq(new EquivalentAddressGroup(backends.get(0))), any(Attributes.class));
    inOrder.verify(helper).createSubchannel(
        eq(new EquivalentAddressGroup(backends.get(1))), any(Attributes.class));
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
        eq(lbAuthority(0)), same(executor));
    assertEquals(1, fakeOobChannels.size());
    ManagedChannel oobChannel = fakeOobChannels.poll();
    verify(mockLbService).balanceLoad(lbResponseObserverCaptor.capture());

    // Switch to PICK_FIRST
    List<ResolvedServerInfoGroup> pickFirstResolutionList =
        createResolvedServerInfoGroupList(true, false, true);
    Attributes pickFirstResolutionAttrs = Attributes.newBuilder()
        .set(GrpclbConstants.ATTR_LB_POLICY, LbPolicy.PICK_FIRST).build();
    verify(pickFirstBalancerFactory, never()).newLoadBalancer(any(Helper.class));
    verify(mockLbService).balanceLoad(lbResponseObserverCaptor.capture());
    StreamObserver<LoadBalanceResponse> lbResponseObserver = lbResponseObserverCaptor.getValue();

    verify(lbRequestObserver, never()).onCompleted();
    assertFalse(oobChannel.isShutdown());
    balancer.handleResolvedAddresses(pickFirstResolutionList, pickFirstResolutionAttrs);

    verify(pickFirstBalancerFactory).newLoadBalancer(same(helper));
    // Only non-LB addresses are passed to the delegate
    verify(pickFirstBalancer).handleResolvedAddresses(
        eq(Arrays.asList(pickFirstResolutionList.get(1))), same(pickFirstResolutionAttrs));
    assertSame(LbPolicy.PICK_FIRST, balancer.getLbPolicy());
    assertSame(pickFirstBalancer, balancer.getDelegate());
    // GRPCLB connection is closed
    verify(lbRequestObserver).onCompleted();
    assertTrue(oobChannel.isShutdown());

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
        eq(lbAuthority(0)), same(executor));
    verify(helper, times(2)).createOobChannel(any(EquivalentAddressGroup.class), any(String.class),
        any(Executor.class));
    assertEquals(1, fakeOobChannels.size());
    oobChannel = fakeOobChannels.poll();
    verify(mockLbService, times(2)).balanceLoad(lbResponseObserverCaptor.capture());

    // Special case: PICK_FIRST is the default
    pickFirstResolutionList = createResolvedServerInfoGroupList(true, false, false);
    pickFirstResolutionAttrs = Attributes.EMPTY;
    verify(pickFirstBalancerFactory).newLoadBalancer(any(Helper.class));
    assertFalse(oobChannel.isShutdown());
    balancer.handleResolvedAddresses(pickFirstResolutionList, pickFirstResolutionAttrs);

    verify(pickFirstBalancerFactory, times(2)).newLoadBalancer(same(helper));
    // Only non-LB addresses are passed to the delegate
    verify(pickFirstBalancer).handleResolvedAddresses(
        eq(pickFirstResolutionList.subList(1, 3)), same(pickFirstResolutionAttrs));
    assertSame(LbPolicy.PICK_FIRST, balancer.getLbPolicy());
    assertSame(pickFirstBalancer, balancer.getDelegate());
    // GRPCLB connection is closed
    assertTrue(oobChannel.isShutdown());
  }

  @Test
  public void grpclbWorking() {
    InOrder inOrder = inOrder(helper);
    List<ResolvedServerInfoGroup> grpclbResolutionList =
        createResolvedServerInfoGroupList(true, true);
    Attributes grpclbResolutionAttrs = Attributes.newBuilder()
        .set(GrpclbConstants.ATTR_LB_POLICY, LbPolicy.GRPCLB).build();
    balancer.handleResolvedAddresses(grpclbResolutionList, grpclbResolutionAttrs);

    assertSame(LbPolicy.GRPCLB, balancer.getLbPolicy());
    assertNull(balancer.getDelegate());
    verify(helper).createOobChannel(eq(grpclbResolutionList.get(0).toEquivalentAddressGroup()),
        eq(lbAuthority(0)), same(executor));
    assertEquals(1, fakeOobChannels.size());
    ManagedChannel oobChannel = fakeOobChannels.poll();
    verify(mockLbService).balanceLoad(lbResponseObserverCaptor.capture());
    StreamObserver<LoadBalanceResponse> lbResponseObserver = lbResponseObserverCaptor.getValue();

    // Simulate receiving LB response
    List<InetSocketAddress> backends1 = Arrays.asList(
        new InetSocketAddress("127.0.0.1", 2000),
        new InetSocketAddress("127.0.0.1", 2010));
    inOrder.verify(helper, never()).updatePicker(any(SubchannelPicker.class));
    lbResponseObserver.onNext(buildInitialResponse());
    lbResponseObserver.onNext(buildLbResponse(backends1));

    inOrder.verify(helper).createSubchannel(
        eq(new EquivalentAddressGroup(backends1.get(0))), any(Attributes.class));
    inOrder.verify(helper).createSubchannel(
        eq(new EquivalentAddressGroup(backends1.get(1))), any(Attributes.class));
    assertEquals(2, mockSubchannels.size());
    Subchannel subchannel1 = mockSubchannels.poll();
    Subchannel subchannel2 = mockSubchannels.poll();
    verify(subchannel1).requestConnection();
    verify(subchannel2).requestConnection();
    assertEquals(new EquivalentAddressGroup(backends1.get(0)), subchannel1.getAddresses());
    assertEquals(new EquivalentAddressGroup(backends1.get(1)), subchannel2.getAddresses());

    // Before any subchannel is READY, a buffer picker will be provided
    inOrder.verify(helper).updatePicker(same(GrpclbLoadBalancer2.BUFFER_PICKER));

    balancer.handleSubchannelState(subchannel1, ConnectivityStateInfo.forNonError(CONNECTING));
    balancer.handleSubchannelState(subchannel2, ConnectivityStateInfo.forNonError(CONNECTING));
    inOrder.verify(helper, times(2)).updatePicker(same(GrpclbLoadBalancer2.BUFFER_PICKER));

    // Let subchannels be connected
    balancer.handleSubchannelState(subchannel2, ConnectivityStateInfo.forNonError(READY));
    inOrder.verify(helper).updatePicker(pickerCaptor.capture());
    RoundRobinPicker picker1 = (RoundRobinPicker) pickerCaptor.getValue();
    assertRoundRobinList(picker1, subchannel2);

    balancer.handleSubchannelState(subchannel1, ConnectivityStateInfo.forNonError(READY));
    inOrder.verify(helper).updatePicker(pickerCaptor.capture());
    RoundRobinPicker picker2 = (RoundRobinPicker) pickerCaptor.getValue();
    assertRoundRobinList(picker2, subchannel1, subchannel2);

    // Disconnected subchannels
    verify(subchannel1).requestConnection();
    balancer.handleSubchannelState(subchannel1, ConnectivityStateInfo.forNonError(IDLE));
    verify(subchannel1, times(2)).requestConnection();
    inOrder.verify(helper).updatePicker(pickerCaptor.capture());
    RoundRobinPicker picker3 = (RoundRobinPicker) pickerCaptor.getValue();
    assertRoundRobinList(picker3, subchannel2);

    balancer.handleSubchannelState(subchannel1, ConnectivityStateInfo.forNonError(CONNECTING));
    inOrder.verify(helper).updatePicker(pickerCaptor.capture());
    RoundRobinPicker picker4 = (RoundRobinPicker) pickerCaptor.getValue();
    assertRoundRobinList(picker4, subchannel2);

    // As long as there is at least one READY subchannel, round robin will work.
    Status error1 = Status.UNAVAILABLE.withDescription("error1");
    balancer.handleSubchannelState(subchannel1, ConnectivityStateInfo.forTransientFailure(error1));
    inOrder.verify(helper).updatePicker(pickerCaptor.capture());
    RoundRobinPicker picker5 = (RoundRobinPicker) pickerCaptor.getValue();
    assertRoundRobinList(picker5, subchannel2);

    // If no subchannel is READY, will propagate an error from an arbitrary subchannel (but here
    // only subchannel1 has error).
    verify(subchannel2).requestConnection();
    balancer.handleSubchannelState(subchannel2, ConnectivityStateInfo.forNonError(IDLE));
    verify(subchannel2, times(2)).requestConnection();
    inOrder.verify(helper).updatePicker(pickerCaptor.capture());
    ErrorPicker picker6 = (ErrorPicker) pickerCaptor.getValue();
    assertNull(picker6.result.getSubchannel());
    assertSame(error1, picker6.result.getStatus());

    // Update backends, with a drop entry
    List<InetSocketAddress> backends2 = Arrays.asList(
        new InetSocketAddress("127.0.0.1", 2030), null);
    verify(subchannel1, never()).shutdown();
    verify(subchannel2, never()).shutdown();

    lbResponseObserver.onNext(buildLbResponse(backends2));
    verify(subchannel1).shutdown();
    verify(subchannel2).shutdown();

    inOrder.verify(helper).createSubchannel(
        eq(new EquivalentAddressGroup(backends2.get(0))), any(Attributes.class));
    assertEquals(1, mockSubchannels.size());
    Subchannel subchannel3 = mockSubchannels.poll();
    verify(subchannel3).requestConnection();
    assertEquals(new EquivalentAddressGroup(backends2.get(0)), subchannel3.getAddresses());
    inOrder.verify(helper).updatePicker(pickerCaptor.capture());
    RoundRobinPicker picker7 = (RoundRobinPicker) pickerCaptor.getValue();
    assertRoundRobinList(picker7, (Subchannel) null);

    // State updates on obsolete subchannels will have no effect
    balancer.handleSubchannelState(subchannel1, ConnectivityStateInfo.forNonError(READY));
    balancer.handleSubchannelState(subchannel1, ConnectivityStateInfo.forNonError(SHUTDOWN));
    balancer.handleSubchannelState(subchannel2, ConnectivityStateInfo.forNonError(SHUTDOWN));
    inOrder.verifyNoMoreInteractions();

    balancer.handleSubchannelState(subchannel3, ConnectivityStateInfo.forNonError(READY));
    inOrder.verify(helper).updatePicker(pickerCaptor.capture());
    RoundRobinPicker picker8 = (RoundRobinPicker) pickerCaptor.getValue();
    assertRoundRobinList(picker8, subchannel3, null);

    verify(subchannel3, never()).shutdown();
    assertFalse(oobChannel.isShutdown());
    verify(lbRequestObserver, never()).onCompleted();
  }

  @Test
  public void grpclbBalanerCommErrors() {
    InOrder inOrder = inOrder(helper, mockLbService);
    // Make the first LB address fail to connect
    failingLbAuthorities.add(lbAuthority(0));
    List<ResolvedServerInfoGroup> grpclbResolutionList =
        createResolvedServerInfoGroupList(true, true, true);

    Attributes grpclbResolutionAttrs = Attributes.newBuilder()
        .set(GrpclbConstants.ATTR_LB_POLICY, LbPolicy.GRPCLB).build();
    balancer.handleResolvedAddresses(grpclbResolutionList, grpclbResolutionAttrs);

    // First LB addr fails to connect
    inOrder.verify(helper).createOobChannel(
        eq(grpclbResolutionList.get(0).toEquivalentAddressGroup()),
        eq(lbAuthority(0)), same(executor));
    inOrder.verify(helper).updatePicker(isA(ErrorPicker.class));

    assertEquals(2, fakeOobChannels.size());
    assertTrue(fakeOobChannels.poll().isShutdown());
    // Will move on to second LB addr
    inOrder.verify(helper).createOobChannel(
        eq(grpclbResolutionList.get(1).toEquivalentAddressGroup()),
        eq(lbAuthority(1)), same(executor));
    inOrder.verify(mockLbService).balanceLoad(lbResponseObserverCaptor.capture());
    assertEquals(1, fakeOobChannels.size());
    assertFalse(fakeOobChannels.peek().isShutdown());

    StreamObserver<LoadBalanceResponse> lbResponseObserver = lbResponseObserverCaptor.getValue();
    Status error1 = Status.UNAVAILABLE.withDescription("error1");
    // Simulate that the stream on the second LB failed
    lbResponseObserver.onError(error1.asException());
    assertTrue(fakeOobChannels.poll().isShutdown());

    inOrder.verify(helper).updatePicker(pickerCaptor.capture());
    ErrorPicker errorPicker = (ErrorPicker) pickerCaptor.getValue();
    assertEquals(error1.getCode(), errorPicker.result.getStatus().getCode());
    assertTrue(errorPicker.result.getStatus().getDescription().contains(error1.getDescription()));
    // Move on to the third LB.
    inOrder.verify(helper).createOobChannel(
        eq(grpclbResolutionList.get(2).toEquivalentAddressGroup()),
        eq(lbAuthority(2)), same(executor));

    inOrder.verify(mockLbService).balanceLoad(lbResponseObserverCaptor.capture());
    lbResponseObserver = lbResponseObserverCaptor.getValue();
    assertEquals(1, fakeOobChannels.size());
    assertFalse(fakeOobChannels.peek().isShutdown());

    // Simulate that the stream on the third LB closed without error.  It is treated
    // as an error.
    lbResponseObserver.onCompleted();
    assertTrue(fakeOobChannels.poll().isShutdown());

    // Loop back to the first LB addr, which still fails.
    inOrder.verify(helper).createOobChannel(
        eq(grpclbResolutionList.get(0).toEquivalentAddressGroup()),
        eq(lbAuthority(0)), same(executor));
    inOrder.verify(helper).updatePicker(isA(ErrorPicker.class));

    assertEquals(2, fakeOobChannels.size());
    assertTrue(fakeOobChannels.poll().isShutdown());
    // Will move on to second LB addr
    inOrder.verify(helper).createOobChannel(
        eq(grpclbResolutionList.get(1).toEquivalentAddressGroup()),
        eq(lbAuthority(1)), same(executor));
    inOrder.verify(mockLbService).balanceLoad(lbResponseObserverCaptor.capture());
    assertEquals(1, fakeOobChannels.size());
    assertFalse(fakeOobChannels.peek().isShutdown());

    // Finally it works.
    lbResponseObserver = lbResponseObserverCaptor.getValue();
    lbResponseObserver.onNext(buildInitialResponse());
    List<InetSocketAddress> backends = Arrays.asList(
        new InetSocketAddress("127.0.0.1", 2000),
        new InetSocketAddress("127.0.0.1", 2010));
    lbResponseObserver.onNext(buildLbResponse(backends));
    inOrder.verify(helper).createSubchannel(
        eq(new EquivalentAddressGroup(backends.get(0))), any(Attributes.class));
    inOrder.verify(helper).createSubchannel(
        eq(new EquivalentAddressGroup(backends.get(1))), any(Attributes.class));
    inOrder.verify(helper).updatePicker(same(GrpclbLoadBalancer2.BUFFER_PICKER));
    inOrder.verifyNoMoreInteractions();
  }

  private List<ResolvedServerInfoGroup> createResolvedServerInfoGroupList(boolean ... isLb) {
    ArrayList<ResolvedServerInfoGroup> list = new ArrayList<ResolvedServerInfoGroup>();
    for (int i = 0; i < isLb.length; i++) {
      SocketAddress addr = new FakeSocketAddress("fake-address-" + i);
      ResolvedServerInfoGroup serverInfoGroup = ResolvedServerInfoGroup
          .builder(isLb[i] ? Attributes.newBuilder()
              .set(GrpclbConstants.ATTR_LB_ADDR_AUTHORITY, lbAuthority(i))
              .build()
              : Attributes.EMPTY)
          .add(new ResolvedServerInfo(addr))
          .build();
      list.add(serverInfoGroup);
    }
    return list;
  }

  private static String lbAuthority(int i) {
    return "lb" + i + ".google.com";
  }

  private static LoadBalanceResponse buildInitialResponse() {
    return LoadBalanceResponse.newBuilder().setInitialResponse(
        InitialLoadBalanceResponse.getDefaultInstance())
        .build();
  }

  private static LoadBalanceResponse buildLbResponse(List<InetSocketAddress> addrs) {
    ServerList.Builder serverListBuilder = ServerList.newBuilder();
    for (InetSocketAddress addr : addrs) {
      if (addr != null) {
        serverListBuilder.addServers(Server.newBuilder()
            .setIpAddress(ByteString.copyFrom(addr.getAddress().getAddress()))
            .setPort(addr.getPort())
            .build());
      } else {
        serverListBuilder.addServers(Server.newBuilder().setDropRequest(true).build());
      }
    }
    return LoadBalanceResponse.newBuilder()
        .setServerList(serverListBuilder.build())
        .build();
  }

  private static void assertRoundRobinList(RoundRobinPicker picker, Subchannel ... subchannels) {
    assertEquals(subchannels.length, picker.list.size());
    for (int i = 0; i < subchannels.length; i++) {
      Subchannel subchannel = subchannels[i];
      if (subchannel == null) {
        assertSame("list[" + i + "] should be drop",
            GrpclbLoadBalancer2.THROTTLED_RESULT, picker.list.get(i));
      } else {
        assertEquals("list[" + i + "] should be Subchannel",
            subchannel, picker.list.get(i).getSubchannel());
      }
    }
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

    @Override
    public boolean equals(Object other) {
      if (other instanceof FakeSocketAddress) {
        FakeSocketAddress otherAddr = (FakeSocketAddress) other;
        return name.equals(otherAddr.name);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }
  }

  private static class FakeExecutor implements Executor {
    @Override
    public void execute(Runnable runnable) {
      runnable.run();
    }
  }
}
