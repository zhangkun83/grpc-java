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

package io.grpc.examples.massage;

import io.grpc.Attributes;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.NameResolver;
import io.grpc.ResolvedServerInfo;
import io.grpc.grpclb.GrpclbLoadBalancerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple client that demonstrate GRPCLB protocol.
 */
public class MassageClient {
  private static final Logger logger = Logger.getLogger(MassageClient.class.getName());

  private static final NameResolver nameResolver = new NameResolver() {
    @Override public String getServiceAuthority() {
      return "helloworld";
    }

    @Override public void start(NameResolver.Listener listener) {
      listener.onUpdate(
          Collections.singletonList(new ResolvedServerInfo(
              new InetSocketAddress("127.0.0.1", 50010), Attributes.EMPTY)),
          Attributes.EMPTY);
    }

    @Override public void shutdown() {
    }
  };

  private static final NameResolver.Factory nameResolverFactory = new NameResolver.Factory() {
    @Override public NameResolver newNameResolver(URI targetUri, Attributes params) {
      return nameResolver;
    }
  };

  private final ManagedChannel channel;
  private final MassageGrpc.MassageBlockingStub blockingStub;

  MassageClient() {
    channel = ManagedChannelBuilder.forTarget("helloworld")
        .usePlaintext(true)
        .nameResolverFactory(nameResolverFactory)
        .loadBalancerFactory(GrpclbLoadBalancerFactory.getInstance())
        .build();
    blockingStub = MassageGrpc.newBlockingStub(channel);
  }

  public void shutdown() throws InterruptedException {
    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
  }

  void massage(int times) throws Exception {
    try {
      for (int i = 0; i < times; i++) {
        logger.info("Massaging " + i);
        Thread.sleep(100);
        MassageRequest request = MassageRequest.newBuilder().setRequest("Message " + i).build();
        MassageReply reply = blockingStub.getMassage(request);
        logger.info("Replied: " + reply.getReply());
      }
    } catch (RuntimeException e) {
      logger.log(Level.WARNING, "RPC failed", e);
      return;
    }
  }

  private static void setLogLevel(Level level) {
    Logger.getLogger("").setLevel(level);
    for (Handler handler : Logger.getLogger("").getHandlers()) {
      handler.setLevel(level);
    }
  }

  /**
   * Starts a MassageClient and send a bunch of load-balanced requests.
   */
  public static void main(String[] args) throws Exception {
    setLogLevel(Level.INFO);
    MassageClient client = new MassageClient();
    try {
      client.massage(100);
    } finally {
      client.shutdown();
    }
  }
}
