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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListenableFuture;

import io.grpc.Attributes;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.DecompressorRegistry;
import io.grpc.IntegerMarshaller;
import io.grpc.LoadBalancer;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.NameResolver;
import io.grpc.NameResolver.Factory;
import io.grpc.ResolvedServerInfo;
import io.grpc.Status;
import io.grpc.StringMarshaller;
import io.grpc.TransportManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Unit tests for {@link ManagedChannelImpl}'s transport retention logic.
 *
 * <p>Such logic is exposed via the {@link TransportManager} interface, which the tests are written
 * against.
 */
@RunWith(JUnit4.class)
public class ManagedChannelImplTransportRetentionTest {

  private static final String authority = "fakeauthority";
  private static final NameResolver.Factory nameResolverFactory = new NameResolver.Factory() {
    @Override
    public NameResolver newNameResolver(final URI targetUri, Attributes params) {
      return new NameResolver() {
        @Override public void start(final Listener listener) {
        }

        @Override public String getServiceAuthority() {
          return authority;
        }

        @Override public void shutdown() {
        }
      };
    }
  };

  private static final BackoffPolicy.Provider backoffPolicyProvider = new BackoffPolicy.Provider() {
    @Override
    public BackoffPolicy get() {
      return new BackoffPolicy() {
        @Override
        public long nextBackoffMillis() {
          return 1;
        }
      };
    }
  };

  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  private ManagedChannelImpl channel;

  @Mock private ClientTransport mockTransport;
  @Mock private ClientTransportFactory mockTransportFactory;
  @Mock private LoadBalancer.Factory mockLoadBalancerFactory;
  @Mock private SocketAddress addr;

  private TransportManager tm;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    when(mockTransportFactory.newClientTransport(any(SocketAddress.class), any(String.class)))
        .thenReturn(mockTransport);

    channel = new ManagedChannelImpl("fake://target", backoffPolicyProvider,
        nameResolverFactory, Attributes.EMPTY, mockLoadBalancerFactory,
        mockTransportFactory, executor, null, Collections.<ClientInterceptor>emptyList());

    ArgumentCaptor<TransportManager> tmCaptor = ArgumentCaptor.forClass(TransportManager.class);
    verify(mockLoadBalancerFactory).newLoadBalancer(anyString(), tmCaptor.capture());
    tm = tmCaptor.getValue();
  }

  @Test
  public void getTransportCreatesPassiveRetention() {
    ListenableFuture<ClientTransport> transportFuture = tm.getTransport(addr);
    verify(mockTransportFactory).newClientTransport(addr, authority);
  }

  @Test
  public void activeRetentionCreatesNewTransport() {
  }

  @Test
  public void passiveRetentionCreatesNewTransport() {
  }

  @Test
  public void passiveRetentionToActiveRetention() {
  }

  @Test
  public void activeRetentionToPassiveRetention() {
  }

  @Test
  public void removeRetention() {
  }
}
