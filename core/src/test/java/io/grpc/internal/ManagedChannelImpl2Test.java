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

package io.grpc.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;

import io.grpc.Attributes;
import io.grpc.CallCredentials.MetadataApplier;
import io.grpc.CallCredentials;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.Compressor;
import io.grpc.CompressorRegistry;
import io.grpc.Context;
import io.grpc.DecompressorRegistry;
import io.grpc.EquivalentAddressGroup;
import io.grpc.IntegerMarshaller;
import io.grpc.LoadBalancer2;
import io.grpc.LoadBalancer2.Helper;
import io.grpc.LoadBalancer2.PickResult;
import io.grpc.LoadBalancer2.Subchannel;
import io.grpc.LoadBalancer2.SubchannelPicker;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.NameResolver;
import io.grpc.ResolvedServerInfo;
import io.grpc.ResolvedServerInfoGroup;
import io.grpc.SecurityLevel;
import io.grpc.Status;
import io.grpc.StringMarshaller;
import io.grpc.internal.testing.CensusTestUtils.FakeCensusContextFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Unit tests for {@link ManagedChannelImpl2}. */
@RunWith(JUnit4.class)
public class ManagedChannelImpl2Test {
  private static final List<ClientInterceptor> NO_INTERCEPTOR =
      Collections.<ClientInterceptor>emptyList();
  private static final Attributes NAME_RESOLVER_PARAMS =
      Attributes.newBuilder().set(NameResolver.Factory.PARAMS_DEFAULT_PORT, 447).build();
  private final MethodDescriptor<String, Integer> method = MethodDescriptor.create(
      MethodDescriptor.MethodType.UNKNOWN, "/service/method",
      new StringMarshaller(), new IntegerMarshaller());
  private final String serviceName = "fake.example.com";
  private final String authority = serviceName;
  private final String userAgent = "userAgent";
  private final String target = "fake://" + serviceName;
  private URI expectedUri;
  private final SocketAddress socketAddress = new SocketAddress() {};
  private final EquivalentAddressGroup addressGroup = new EquivalentAddressGroup(socketAddress);
  private final ResolvedServerInfo server = new ResolvedServerInfo(socketAddress, Attributes.EMPTY);
  private final FakeClock timer = new FakeClock();
  private final FakeClock executor = new FakeClock();
  private final FakeCensusContextFactory censusCtxFactory = new FakeCensusContextFactory();

  @Rule public final ExpectedException thrown = ExpectedException.none();

  private ManagedChannelImpl2 channel;
  private Helper helper;
  @Captor
  private ArgumentCaptor<Status> statusCaptor;
  @Captor
  private ArgumentCaptor<StatsTraceContext> statsTraceCtxCaptor;
  @Mock
  private LoadBalancer2.Factory mockLoadBalancerFactory;
  @Mock
  private LoadBalancer2 mockLoadBalancer;
  @Mock
  private SubchannelPicker mockPicker;
  @Mock
  private ConnectionClientTransport mockTransport;
  @Mock
  private ClientTransportFactory mockTransportFactory;
  @Mock
  private ClientCall.Listener<Integer> mockCallListener;
  @Mock
  private ClientCall.Listener<Integer> mockCallListener2;
  @Mock
  private ClientCall.Listener<Integer> mockCallListener3;
  @Mock
  private SharedResourceHolder.Resource<ScheduledExecutorService> timerService;
  @Mock
  private CallCredentials creds;

  private ArgumentCaptor<ManagedClientTransport.Listener> transportListenerCaptor =
      ArgumentCaptor.forClass(ManagedClientTransport.Listener.class);
  private ArgumentCaptor<ClientStreamListener> streamListenerCaptor =
      ArgumentCaptor.forClass(ClientStreamListener.class);

  private void createChannel(
      NameResolver.Factory nameResolverFactory, List<ClientInterceptor> interceptors) {
    channel = new ManagedChannelImpl2(target, new FakeBackoffPolicyProvider(),
        nameResolverFactory, NAME_RESOLVER_PARAMS, mockLoadBalancerFactory,
        mockTransportFactory, DecompressorRegistry.getDefaultInstance(),
        CompressorRegistry.getDefaultInstance(), timerService, timer.getStopwatchSupplier(),
        ManagedChannelImpl2.IDLE_TIMEOUT_MILLIS_DISABLE,
        executor.getScheduledExecutorService(), userAgent, interceptors, censusCtxFactory);
    // Force-exit the initial idle-mode
    channel.exitIdleMode();
    assertEquals(0, timer.numPendingTasks());
    
    ArgumentCaptor<Helper> helperCaptor = ArgumentCaptor.forClass(null);
    verify(mockLoadBalancerFactory).newLoadBalancer(helperCaptor.capture());
    helper = helperCaptor.getValue();
  }

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    expectedUri = new URI(target);
    when(mockLoadBalancerFactory.newLoadBalancer(any(Helper.class))).thenReturn(mockLoadBalancer);
    when(mockTransportFactory.newClientTransport(
            any(SocketAddress.class), any(String.class), any(String.class)))
        .thenReturn(mockTransport);
    when(timerService.create()).thenReturn(timer.getScheduledExecutorService());
  }

  @After
  public void allPendingTasksAreRun() throws Exception {
    // The "never" verifications in the tests only hold up if all due tasks are done.
    // As for timer, although there may be scheduled tasks in a future time, since we don't test
    // any time-related behavior in this test suite, we only care the tasks that are due. This
    // would ignore any time-sensitive tasks, e.g., back-off and the idle timer.
    assertTrue(timer.getDueTasks() + " should be empty", timer.getDueTasks().isEmpty());
    assertEquals(executor.getPendingTasks() + " should be empty", 0, executor.numPendingTasks());
  }

  /**
   * The counterpart of {@link ManagedChannelImpl2IdlenessTest#enterIdleModeAfterForceExit}.
   */
  @Test
  @SuppressWarnings("unchecked")
  public void idleModeDisabled() {
    createChannel(new FakeNameResolverFactory(true), NO_INTERCEPTOR);

    // In this test suite, the channel is always created with idle mode disabled.
    // No task is scheduled to enter idle mode
    assertEquals(0, timer.numPendingTasks());
    assertEquals(0, executor.numPendingTasks());
  }

  @Test
  public void immediateDeadlineExceeded() {
    createChannel(new FakeNameResolverFactory(true), NO_INTERCEPTOR);
    ClientCall<String, Integer> call =
        channel.newCall(method, CallOptions.DEFAULT.withDeadlineAfter(0, TimeUnit.NANOSECONDS));
    call.start(mockCallListener, new Metadata());
    assertEquals(1, executor.runDueTasks());

    verify(mockCallListener).onClose(statusCaptor.capture(), any(Metadata.class));
    Status status = statusCaptor.getValue();
    assertSame(Status.DEADLINE_EXCEEDED.getCode(), status.getCode());
  }

  @Test
  public void shutdownWithNoTransportsEverCreated() {
    createChannel(new FakeNameResolverFactory(true), NO_INTERCEPTOR);
    verifyNoMoreInteractions(mockTransportFactory);
    channel.shutdown();
    assertTrue(channel.isShutdown());
    assertTrue(channel.isTerminated());
  }

  @Test
  public void callsAndShutdown() {
    subtestCallsAndShutdown(false, false);
  }

  @Test
  public void callsAndShutdownNow() {
    subtestCallsAndShutdown(true, false);
  }

  /** Make sure shutdownNow() after shutdown() has an effect. */
  @Test
  public void callsAndShutdownAndShutdownNow() {
    subtestCallsAndShutdown(false, true);
  }

  private void subtestCallsAndShutdown(boolean shutdownNow, boolean shutdownNowAfterShutdown) {
    FakeNameResolverFactory nameResolverFactory = new FakeNameResolverFactory(true);
    createChannel(nameResolverFactory, NO_INTERCEPTOR);
    ClientStream mockStream = mock(ClientStream.class);
    ClientStream mockStream2 = mock(ClientStream.class);
    Metadata headers = new Metadata();
    Metadata headers2 = new Metadata();
    when(mockTransport.newStream(same(method), same(headers), same(CallOptions.DEFAULT),
            any(StatsTraceContext.class)))
        .thenReturn(mockStream);
    when(mockTransport.newStream(same(method), same(headers2), same(CallOptions.DEFAULT),
            any(StatsTraceContext.class)))
        .thenReturn(mockStream2);

    // Configure the picker so that first RPC goes to delayed transport, and second RPC goes to
    // real transport.
    Subchannel subchannel = helper.createSubchannel(addressGroup, Attributes.EMPTY);
    subchannel.requestConnection();
    verify(mockTransportFactory).newClientTransport(
        any(SocketAddress.class), any(String.class), any(String.class));
    verify(mockTransport).start(transportListenerCaptor.capture());
    ManagedClientTransport.Listener transportListener = transportListenerCaptor.getValue();
    transportListener.transportReady();
    when(mockPicker.pickSubchannel(any(Attributes.class), same(headers))).thenReturn(
        PickResult.withNoResult());
    when(mockPicker.pickSubchannel(any(Attributes.class), same(headers2))).thenReturn(
        PickResult.withSubchannel(subchannel));
    helper.updatePicker(mockPicker);

    // First RPC, will be pending
    ClientCall<String, Integer> call = channel.newCall(method, CallOptions.DEFAULT);
    verifyNoMoreInteractions(mockTransportFactory);
    call.start(mockCallListener, headers);

    verify(mockTransport, never()).newStream(same(method), same(headers), same(CallOptions.DEFAULT),
        any(StatsTraceContext.class));
    censusCtxFactory.pollContextOrFail();

    // Second RPC, will be assigned to the real transport
    ClientCall<String, Integer> call2 = channel.newCall(method, CallOptions.DEFAULT);
    call2.start(mockCallListener2, headers2);
    verify(mockTransport).newStream(same(method), same(headers2), same(CallOptions.DEFAULT),
        statsTraceCtxCaptor.capture());
    assertEquals(censusCtxFactory.pollContextOrFail(),
        statsTraceCtxCaptor.getValue().getCensusContext());
    verify(mockTransport).newStream(same(method), same(headers2), same(CallOptions.DEFAULT),
        statsTraceCtxCaptor.capture());
    verify(mockStream2).start(any(ClientStreamListener.class));

    // Shutdown
    if (shutdownNow) {
      channel.shutdownNow();
    } else {
      channel.shutdown();
      if (shutdownNowAfterShutdown) {
        channel.shutdownNow();
        shutdownNow = true;
      }
    }
    assertTrue(channel.isShutdown());
    assertFalse(channel.isTerminated());
    assertEquals(1, nameResolverFactory.resolvers.size());
    verify(mockLoadBalancerFactory).newLoadBalancer(any(Helper.class));

    // Further calls should fail without going to the transport
    ClientCall<String, Integer> call3 = channel.newCall(method, CallOptions.DEFAULT);
    call3.start(mockCallListener3, headers2);
    timer.runDueTasks();
    executor.runDueTasks();

    verify(mockCallListener3).onClose(statusCaptor.capture(), any(Metadata.class));
    assertSame(Status.Code.UNAVAILABLE, statusCaptor.getValue().getCode());

    if (shutdownNow) {
      // LoadBalancer and NameResolver are shut down as soon as delayed transport is terminated.
      verify(mockLoadBalancer).shutdown();
      assertTrue(nameResolverFactory.resolvers.get(0).shutdown);
      // call should have been aborted by delayed transport
      executor.runDueTasks();
      verify(mockCallListener).onClose(same(ManagedChannelImpl2.SHUTDOWN_NOW_STATUS),
          any(Metadata.class));
    } else {
      // LoadBalancer and NameResolver are still running.
      verify(mockLoadBalancer, never()).shutdown();
      assertFalse(nameResolverFactory.resolvers.get(0).shutdown);
      // call and call2 are still alive, and can still be assigned to a real transport
      SubchannelPicker picker2 = mock(SubchannelPicker.class);
      when(picker2.pickSubchannel(any(Attributes.class), same(headers))).thenReturn(
          PickResult.withSubchannel(subchannel));
      helper.updatePicker(picker2);
      executor.runDueTasks();
      verify(mockTransport).newStream(same(method), same(headers), same(CallOptions.DEFAULT),
          any(StatsTraceContext.class));
      verify(mockStream).start(any(ClientStreamListener.class));
    }

    // After call is moved out of delayed transport, LoadBalancer, NameResolver and the transports
    // will be shutdown.
    verify(mockLoadBalancer).shutdown();
    assertTrue(nameResolverFactory.resolvers.get(0).shutdown);

    if (shutdownNow) {
      // Channel shutdownNow() all subchannels after shutting down LoadBalancer
      verify(mockTransport).shutdownNow(ManagedChannelImpl2.SHUTDOWN_NOW_STATUS);
    } else {
      verify(mockTransport, never()).shutdownNow(any(Status.class));
    }
    // LoadBalancer should shutdown the subchannel
    subchannel.shutdown();
    verify(mockTransport).shutdown();

    // Killing the remaining real transport will terminate the channel
    transportListener.transportShutdown(Status.UNAVAILABLE);
    assertFalse(channel.isTerminated());
    transportListener.transportTerminated();
    assertTrue(channel.isTerminated());

    verify(mockTransportFactory).close();
    verifyNoMoreInteractions(mockTransportFactory);
    verify(mockTransport, atLeast(0)).getLogId();
    verifyNoMoreInteractions(mockTransport);
  }

  @Test
  public void interceptor() throws Exception {
    final AtomicLong atomic = new AtomicLong();
    ClientInterceptor interceptor = new ClientInterceptor() {
      @Override
      public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> interceptCall(
          MethodDescriptor<RequestT, ResponseT> method, CallOptions callOptions,
          Channel next) {
        atomic.set(1);
        return next.newCall(method, callOptions);
      }
    };
    createChannel(new FakeNameResolverFactory(true), Arrays.asList(interceptor));
    assertNotNull(channel.newCall(method, CallOptions.DEFAULT));
    assertEquals(1, atomic.get());
  }

  @Test
  public void callOptionsExecutor() {
    Metadata headers = new Metadata();
    ClientStream mockStream = mock(ClientStream.class);
    when(mockTransport.newStream(same(method), same(headers), any(CallOptions.class),
            any(StatsTraceContext.class)))
        .thenReturn(mockStream);
    FakeClock callExecutor = new FakeClock();
    createChannel(new FakeNameResolverFactory(true), NO_INTERCEPTOR);

    // Start a call with a call executor
    CallOptions options =
        CallOptions.DEFAULT.withExecutor(callExecutor.getScheduledExecutorService());
    ClientCall<String, Integer> call = channel.newCall(method, options);
    call.start(mockCallListener, headers);

    // The stream is buffered in channel's delayed transport until a picker is provided
    verify(mockTransport, never()).newStream(same(method), same(headers), same(options),
        any(StatsTraceContext.class));
    assertEquals(0, callExecutor.numPendingTasks());

    // Make the transport available
    Subchannel subchannel = helper.createSubchannel(addressGroup, Attributes.EMPTY);
    subchannel.requestConnection();
    verify(mockTransportFactory).newClientTransport(
        any(SocketAddress.class), any(String.class), any(String.class));
    verify(mockTransport).start(transportListenerCaptor.capture());
    transportListenerCaptor.getValue().transportReady();
    when(mockPicker.pickSubchannel(any(Attributes.class), any(Metadata.class)))
        .thenReturn(PickResult.withSubchannel(subchannel));
    helper.updatePicker(mockPicker);

    // Real streams are started in the call executor if they were previously buffered.
    assertEquals(1, callExecutor.runDueTasks());
    verify(mockTransport).newStream(same(method), same(headers), same(options),
        any(StatsTraceContext.class));
    verify(mockStream).start(streamListenerCaptor.capture());

    // Call listener callbacks are also run in the call executor
    ClientStreamListener streamListener = streamListenerCaptor.getValue();
    Metadata trailers = new Metadata();
    assertEquals(0, callExecutor.numPendingTasks());
    streamListener.closed(Status.CANCELLED, trailers);
    verify(mockCallListener, never()).onClose(same(Status.CANCELLED), same(trailers));
    assertEquals(1, callExecutor.runDueTasks());
    verify(mockCallListener).onClose(same(Status.CANCELLED), same(trailers));
  }

  @Test
  public void nameResolutionFailed() {
    Status error = Status.UNAVAILABLE.withCause(new Throwable("fake name resolution error"));

    // Name resolution is started as soon as channel is created.
    createChannel(new FailingNameResolverFactory(error), NO_INTERCEPTOR);
    verify(mockLoadBalancer).handleNameResolutionError(same(error));
  }

  @Test
  public void nameResolverReturnsEmptySubLists() {
    String errorDescription = "NameResolver returned an empty list";

    // Pass a FakeNameResolverFactory with an empty list
    createChannel(new FakeNameResolverFactory(), NO_INTERCEPTOR);

    // LoadBalancer received the error
    verify(mockLoadBalancerFactory).newLoadBalancer(any(Helper.class));
    verify(mockLoadBalancer).handleNameResolutionError(statusCaptor.capture());
    Status status = statusCaptor.getValue();
    assertSame(Status.Code.UNAVAILABLE, status.getCode());
    assertEquals(errorDescription, status.getDescription());
  }

  @Test
  public void loadBalancerThrowsInHandleResolvedAddresses() {
    RuntimeException ex = new RuntimeException("simulated");
    // Delay the success of name resolution until allResolved() is called
    FakeNameResolverFactory nameResolverFactory = new FakeNameResolverFactory(false);
    createChannel(nameResolverFactory, NO_INTERCEPTOR);

    verify(mockLoadBalancerFactory).newLoadBalancer(any(Helper.class));
    doThrow(ex).when(mockLoadBalancer).handleResolvedAddresses(
        Matchers.<List<ResolvedServerInfoGroup>>anyObject(), any(Attributes.class));

    // NameResolver returns addresses.
    nameResolverFactory.allResolved();

    // The LoadBalancer will receive the error that it has thrown.
    verify(mockLoadBalancer).handleNameResolutionError(statusCaptor.capture());
    Status status = statusCaptor.getValue();
    assertSame(Status.Code.INTERNAL, status.getCode());
    assertSame(ex, status.getCause());
  }

  @Test
  public void nameResolvedAfterChannelShutdown() {
    // Delay the success of name resolution until allResolved() is called.
    FakeNameResolverFactory nameResolverFactory = new FakeNameResolverFactory(false);
    createChannel(nameResolverFactory, NO_INTERCEPTOR);

    channel.shutdown();

    assertTrue(channel.isShutdown());
    assertTrue(channel.isTerminated());
    verify(mockLoadBalancer).shutdown();
    // Name resolved after the channel is shut down, which is possible if the name resolution takes
    // time and is not cancellable. The resolved address will be dropped.
    nameResolverFactory.allResolved();
    verifyNoMoreInteractions(mockLoadBalancer);
  }

  // TODO(zhangkun83): review the tests from here.
  /**
   * Verify that if the first resolved address points to a server that cannot be connected, the call
   * will end up with the second address which works.
   */
  @Test
  public void firstResolvedServerFailedToConnect() throws Exception {
    final SocketAddress goodAddress = new SocketAddress() {
        @Override public String toString() {
          return "goodAddress";
        }
      };
    final SocketAddress badAddress = new SocketAddress() {
        @Override public String toString() {
          return "badAddress";
        }
      };
    final ResolvedServerInfo goodServer = new ResolvedServerInfo(goodAddress, Attributes.EMPTY);
    final ResolvedServerInfo badServer = new ResolvedServerInfo(badAddress, Attributes.EMPTY);
    final ConnectionClientTransport goodTransport = mock(ConnectionClientTransport.class);
    final ConnectionClientTransport badTransport = mock(ConnectionClientTransport.class);
    when(goodTransport.newStream(
            any(MethodDescriptor.class), any(Metadata.class), any(CallOptions.class),
            any(StatsTraceContext.class)))
        .thenReturn(mock(ClientStream.class));
    when(mockTransportFactory.newClientTransport(
            same(goodAddress), any(String.class), any(String.class)))
        .thenReturn(goodTransport);
    when(mockTransportFactory.newClientTransport(
            same(badAddress), any(String.class), any(String.class)))
        .thenReturn(badTransport);

    FakeNameResolverFactory nameResolverFactory =
        new FakeNameResolverFactory(Arrays.asList(badServer, goodServer));
    createChannel(nameResolverFactory, NO_INTERCEPTOR);
    ClientCall<String, Integer> call = channel.newCall(method, CallOptions.DEFAULT);
    Metadata headers = new Metadata();

    // Start a call. The channel will starts with the first address (badAddress)
    call.start(mockCallListener, headers);
    timer.runDueTasks();
    executor.runDueTasks();

    ArgumentCaptor<ManagedClientTransport.Listener> badTransportListenerCaptor =
        ArgumentCaptor.forClass(ManagedClientTransport.Listener.class);
    verify(badTransport).start(badTransportListenerCaptor.capture());
    verify(mockTransportFactory)
        .newClientTransport(same(badAddress), any(String.class), any(String.class));
    verify(mockTransportFactory, times(0))
          .newClientTransport(same(goodAddress), any(String.class), any(String.class));
    badTransportListenerCaptor.getValue().transportShutdown(Status.UNAVAILABLE);

    // The channel then try the second address (goodAddress)
    ArgumentCaptor<ManagedClientTransport.Listener> goodTransportListenerCaptor =
        ArgumentCaptor.forClass(ManagedClientTransport.Listener.class);
    verify(mockTransportFactory)
          .newClientTransport(same(goodAddress), any(String.class), any(String.class));
    verify(goodTransport).start(goodTransportListenerCaptor.capture());
    goodTransportListenerCaptor.getValue().transportReady();
    executor.runDueTasks();

    verify(goodTransport).newStream(same(method), same(headers), same(CallOptions.DEFAULT),
        any(StatsTraceContext.class));
    // The bad transport was never used.
    verify(badTransport, times(0)).newStream(any(MethodDescriptor.class), any(Metadata.class));
  }

  /**
   * Verify that if all resolved addresses failed to connect, the call will fail.
   */
  @Test
  public void allServersFailedToConnect() throws Exception {
    final SocketAddress addr1 = new SocketAddress() {
        @Override public String toString() {
          return "addr1";
        }
      };
    final SocketAddress addr2 = new SocketAddress() {
        @Override public String toString() {
          return "addr2";
        }
      };
    final ResolvedServerInfo server1 = new ResolvedServerInfo(addr1, Attributes.EMPTY);
    final ResolvedServerInfo server2 = new ResolvedServerInfo(addr2, Attributes.EMPTY);
    final ConnectionClientTransport transport1 = mock(ConnectionClientTransport.class);
    final ConnectionClientTransport transport2 = mock(ConnectionClientTransport.class);
    when(mockTransportFactory.newClientTransport(same(addr1), any(String.class), any(String.class)))
        .thenReturn(transport1);
    when(mockTransportFactory.newClientTransport(same(addr2), any(String.class), any(String.class)))
        .thenReturn(transport2);

    FakeNameResolverFactory nameResolverFactory =
        new FakeNameResolverFactory(Arrays.asList(server1, server2));
    createChannel(nameResolverFactory, NO_INTERCEPTOR);
    ClientCall<String, Integer> call = channel.newCall(method, CallOptions.DEFAULT);
    Metadata headers = new Metadata();

    // Start a call. The channel will starts with the first address, which will fail to connect.
    call.start(mockCallListener, headers);
    timer.runDueTasks();
    executor.runDueTasks();

    verify(transport1).start(transportListenerCaptor.capture());
    verify(mockTransportFactory)
        .newClientTransport(same(addr1), any(String.class), any(String.class));
    verify(mockTransportFactory, times(0))
        .newClientTransport(same(addr2), any(String.class), any(String.class));
    transportListenerCaptor.getValue().transportShutdown(Status.UNAVAILABLE);

    // The channel then try the second address, which will fail to connect too.
    verify(transport2).start(transportListenerCaptor.capture());
    verify(mockTransportFactory)
        .newClientTransport(same(addr2), any(String.class), any(String.class));
    verify(transport2).start(transportListenerCaptor.capture());
    transportListenerCaptor.getValue().transportShutdown(Status.UNAVAILABLE);
    executor.runDueTasks();

    // Call fails
    verify(mockCallListener).onClose(statusCaptor.capture(), any(Metadata.class));
    assertEquals(Status.Code.UNAVAILABLE, statusCaptor.getValue().getCode());
    // No real stream was ever created
    verify(transport1, times(0)).newStream(any(MethodDescriptor.class), any(Metadata.class));
    verify(transport2, times(0)).newStream(any(MethodDescriptor.class), any(Metadata.class));
  }

  /**
   * Verify that if the first resolved address points to a server that is at first connected, but
   * disconnected later, all calls will stick to the first address.
   */
  @Test
  public void firstResolvedServerConnectedThenDisconnected() throws Exception {
    final SocketAddress addr1 = new SocketAddress() {
        @Override public String toString() {
          return "addr1";
        }
      };
    final SocketAddress addr2 = new SocketAddress() {
        @Override public String toString() {
          return "addr2";
        }
      };
    final ResolvedServerInfo server1 = new ResolvedServerInfo(addr1, Attributes.EMPTY);
    final ResolvedServerInfo server2 = new ResolvedServerInfo(addr2, Attributes.EMPTY);
    // Addr1 will have two transports throughout this test.
    final ConnectionClientTransport transport1 = mock(ConnectionClientTransport.class);
    final ConnectionClientTransport transport2 = mock(ConnectionClientTransport.class);
    when(transport1.newStream(
            any(MethodDescriptor.class), any(Metadata.class), any(CallOptions.class),
            any(StatsTraceContext.class)))
        .thenReturn(mock(ClientStream.class));
    when(transport2.newStream(
            any(MethodDescriptor.class), any(Metadata.class), any(CallOptions.class),
            any(StatsTraceContext.class)))
        .thenReturn(mock(ClientStream.class));
    when(mockTransportFactory.newClientTransport(same(addr1), any(String.class), any(String.class)))
        .thenReturn(transport1, transport2);

    FakeNameResolverFactory nameResolverFactory =
        new FakeNameResolverFactory(Arrays.asList(server1, server2));
    createChannel(nameResolverFactory, NO_INTERCEPTOR);
    ClientCall<String, Integer> call = channel.newCall(method, CallOptions.DEFAULT);
    Metadata headers = new Metadata();

    // First call will use the first address
    call.start(mockCallListener, headers);
    timer.runDueTasks();
    executor.runDueTasks();

    verify(mockTransportFactory)
        .newClientTransport(same(addr1), any(String.class), any(String.class));
    verify(transport1).start(transportListenerCaptor.capture());
    transportListenerCaptor.getValue().transportReady();
    executor.runDueTasks();

    verify(transport1).newStream(same(method), same(headers), same(CallOptions.DEFAULT),
        any(StatsTraceContext.class));
    transportListenerCaptor.getValue().transportShutdown(Status.UNAVAILABLE);

    // Second call still use the first address, since it was successfully connected.
    ClientCall<String, Integer> call2 = channel.newCall(method, CallOptions.DEFAULT);
    call2.start(mockCallListener, headers);
    verify(transport2).start(transportListenerCaptor.capture());
    verify(mockTransportFactory, times(2))
        .newClientTransport(same(addr1), any(String.class), any(String.class));
    transportListenerCaptor.getValue().transportReady();
    executor.runDueTasks();

    verify(transport2).newStream(same(method), same(headers), same(CallOptions.DEFAULT),
        any(StatsTraceContext.class));
  }

  @Test
  public void uriPattern() {
    assertTrue(ManagedChannelImpl2.URI_PATTERN.matcher("a:/").matches());
    assertTrue(ManagedChannelImpl2.URI_PATTERN.matcher("Z019+-.:/!@ #~ ").matches());
    assertFalse(ManagedChannelImpl2.URI_PATTERN.matcher("a/:").matches()); // "/:" not matched
    assertFalse(ManagedChannelImpl2.URI_PATTERN.matcher("0a:/").matches()); // '0' not matched
    assertFalse(ManagedChannelImpl2.URI_PATTERN.matcher("a,:/").matches()); // ',' not matched
    assertFalse(ManagedChannelImpl2.URI_PATTERN.matcher(" a:/").matches()); // space not matched
  }

  /**
   * Test that information such as the Call's context, MethodDescriptor, authority, executor are
   * propagated to newStream() and applyRequestMetadata().
   */
  @Test
  public void informationPropagatedToNewStreamAndCallCredentials() {
    createChannel(new FakeNameResolverFactory(true), NO_INTERCEPTOR);
    CallOptions callOptions = CallOptions.DEFAULT.withCallCredentials(creds);
    final Context.Key<String> testKey = Context.key("testing");
    Context ctx = Context.current().withValue(testKey, "testValue");
    final LinkedList<Context> credsApplyContexts = new LinkedList<Context>();
    final LinkedList<Context> newStreamContexts = new LinkedList<Context>();
    doAnswer(new Answer<Void>() {
        @Override
        public Void answer(InvocationOnMock in) throws Throwable {
          credsApplyContexts.add(Context.current());
          return null;
        }
      }).when(creds).applyRequestMetadata(
          any(MethodDescriptor.class), any(Attributes.class), any(Executor.class),
          any(MetadataApplier.class));

    final ConnectionClientTransport transport = mock(ConnectionClientTransport.class);
    when(transport.getAttrs()).thenReturn(Attributes.EMPTY);
    when(mockTransportFactory.newClientTransport(any(SocketAddress.class), any(String.class),
            any(String.class))).thenReturn(transport);
    doAnswer(new Answer<ClientStream>() {
        @Override
        public ClientStream answer(InvocationOnMock in) throws Throwable {
          newStreamContexts.add(Context.current());
          return mock(ClientStream.class);
        }
      }).when(transport).newStream(
          any(MethodDescriptor.class), any(Metadata.class), any(CallOptions.class),
          any(StatsTraceContext.class));

    // First call will be on delayed transport.  Only newCall() is run within the expected context,
    // so that we can verify that the context is explicitly attached before calling newStream() and
    // applyRequestMetadata(), which happens after we detach the context from the thread.
    Context origCtx = ctx.attach();
    assertEquals("testValue", testKey.get());
    ClientCall<String, Integer> call = channel.newCall(method, callOptions);
    ctx.detach(origCtx);
    assertNull(testKey.get());
    call.start(mockCallListener, new Metadata());

    ArgumentCaptor<ManagedClientTransport.Listener> transportListenerCaptor =
        ArgumentCaptor.forClass(ManagedClientTransport.Listener.class);
    verify(mockTransportFactory).newClientTransport(
        same(socketAddress), eq(authority), eq(userAgent));
    verify(transport).start(transportListenerCaptor.capture());
    verify(creds, never()).applyRequestMetadata(
        any(MethodDescriptor.class), any(Attributes.class), any(Executor.class),
        any(MetadataApplier.class));

    // applyRequestMetadata() is called after the transport becomes ready.
    transportListenerCaptor.getValue().transportReady();
    executor.runDueTasks();
    ArgumentCaptor<Attributes> attrsCaptor = ArgumentCaptor.forClass(Attributes.class);
    ArgumentCaptor<MetadataApplier> applierCaptor = ArgumentCaptor.forClass(MetadataApplier.class);
    verify(creds).applyRequestMetadata(same(method), attrsCaptor.capture(),
        same(executor.getScheduledExecutorService()), applierCaptor.capture());
    assertEquals("testValue", testKey.get(credsApplyContexts.poll()));
    assertEquals(authority, attrsCaptor.getValue().get(CallCredentials.ATTR_AUTHORITY));
    assertEquals(SecurityLevel.NONE,
        attrsCaptor.getValue().get(CallCredentials.ATTR_SECURITY_LEVEL));
    verify(transport, never()).newStream(
        any(MethodDescriptor.class), any(Metadata.class), any(CallOptions.class),
        any(StatsTraceContext.class));

    // newStream() is called after apply() is called
    applierCaptor.getValue().apply(new Metadata());
    verify(transport).newStream(same(method), any(Metadata.class), same(callOptions),
        any(StatsTraceContext.class));
    assertEquals("testValue", testKey.get(newStreamContexts.poll()));
    // The context should not live beyond the scope of newStream() and applyRequestMetadata()
    assertNull(testKey.get());


    // Second call will not be on delayed transport
    origCtx = ctx.attach();
    call = channel.newCall(method, callOptions);
    ctx.detach(origCtx);
    call.start(mockCallListener, new Metadata());

    verify(creds, times(2)).applyRequestMetadata(same(method), attrsCaptor.capture(),
        same(executor.getScheduledExecutorService()), applierCaptor.capture());
    assertEquals("testValue", testKey.get(credsApplyContexts.poll()));
    assertEquals(authority, attrsCaptor.getValue().get(CallCredentials.ATTR_AUTHORITY));
    assertEquals(SecurityLevel.NONE,
        attrsCaptor.getValue().get(CallCredentials.ATTR_SECURITY_LEVEL));
    // This is from the first call
    verify(transport).newStream(
        any(MethodDescriptor.class), any(Metadata.class), any(CallOptions.class),
        any(StatsTraceContext.class));

    // Still, newStream() is called after apply() is called
    applierCaptor.getValue().apply(new Metadata());
    verify(transport, times(2)).newStream(same(method), any(Metadata.class), same(callOptions),
        any(StatsTraceContext.class));
    assertEquals("testValue", testKey.get(newStreamContexts.poll()));

    assertNull(testKey.get());
  }

  private static class FakeBackoffPolicyProvider implements BackoffPolicy.Provider {
    @Override
    public BackoffPolicy get() {
      return new BackoffPolicy() {
        @Override
        public long nextBackoffMillis() {
          return 1;
        }
      };
    }
  }

  private class FakeNameResolverFactory extends NameResolver.Factory {
    final List<ResolvedServerInfoGroup> servers;
    final boolean resolvedAtStart;
    final ArrayList<FakeNameResolver> resolvers = new ArrayList<FakeNameResolver>();

    FakeNameResolverFactory(boolean resolvedAtStart) {
      this.resolvedAtStart = resolvedAtStart;
      servers = Collections.singletonList(ResolvedServerInfoGroup.builder().add(server).build());
    }

    FakeNameResolverFactory(List<ResolvedServerInfo> servers) {
      resolvedAtStart = true;
      this.servers = Collections.singletonList(
          ResolvedServerInfoGroup.builder().addAll(servers).build());
    }

    public FakeNameResolverFactory() {
      resolvedAtStart = true;
      this.servers = ImmutableList.of();
    }

    @Override
    public NameResolver newNameResolver(final URI targetUri, Attributes params) {
      if (!expectedUri.equals(targetUri)) {
        return null;
      }
      assertSame(NAME_RESOLVER_PARAMS, params);
      FakeNameResolver resolver = new FakeNameResolver();
      resolvers.add(resolver);
      return resolver;
    }

    @Override
    public String getDefaultScheme() {
      return "fake";
    }

    void allResolved() {
      for (FakeNameResolver resolver : resolvers) {
        resolver.resolved();
      }
    }

    private class FakeNameResolver extends NameResolver {
      Listener listener;
      boolean shutdown;

      @Override public String getServiceAuthority() {
        return expectedUri.getAuthority();
      }

      @Override public void start(final Listener listener) {
        this.listener = listener;
        if (resolvedAtStart) {
          resolved();
        }
      }

      void resolved() {
        listener.onUpdate(servers, Attributes.EMPTY);
      }

      @Override public void shutdown() {
        shutdown = true;
      }
    }
  }

  private static class FailingNameResolverFactory extends NameResolver.Factory {
    final Status error;

    FailingNameResolverFactory(Status error) {
      this.error = error;
    }

    @Override
    public NameResolver newNameResolver(URI notUsedUri, Attributes params) {
      return new NameResolver() {
        @Override public String getServiceAuthority() {
          return "irrelevant-authority";
        }

        @Override public void start(final Listener listener) {
          listener.onError(error);
        }

        @Override public void shutdown() {}
      };
    }

    @Override
    public String getDefaultScheme() {
      return "fake";
    }
  }
}
