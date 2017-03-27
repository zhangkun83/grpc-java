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

package io.grpc.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
import com.google.instrumentation.stats.MeasurementDescriptor;
import com.google.instrumentation.stats.MeasurementMap;
import com.google.instrumentation.stats.RpcConstants;
import com.google.instrumentation.stats.StatsContext;
import com.google.instrumentation.stats.StatsContextFactory;
import com.google.instrumentation.stats.TagKey;
import com.google.instrumentation.stats.TagValue;
import io.grpc.CallOptions;
import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.Status;
import io.grpc.ClientStreamTracer;
import io.grpc.ServerStreamTracer;
import io.grpc.StreamTracer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * The stats and tracing information for a stream.
 */
@ThreadSafe
public final class StatsTraceContext extends StreamTracer {
  public static final StatsTraceContext NOOP = new StatsTraceContext(new StreamTracer[0]);

  final StreamTracer[] tracers;

  public static StatsTraceContext newClientContext(CallOptions callOptions, Metadata headers) {
    List<ClientStreamTracer.Factory> factories = callOptions.getStreamTracerFactories();
    // This array will be iterated multiple times per RPC. Use primitive array instead of Collection
    // so that for-each doesn't create an Iterator every time.
    StreamTracer[] tracers = new StreamTracer[factories.size()];
    for (int i = 0; i < tracers.length; i++) {
      tracers[i] = factories.get(i).newClientStreamTracer(headers);
    }
    return new StatsTraceContext(tracers);
  }

  public static StatsTraceContext newServerContext(
      List<ServerStreamTracer.Factory> factories, String fullMethodName, Metadata headers) {
    StreamTracer[] tracers = new StreamTracer[factories.size()];
    for (int i = 0; i < tracers.length; i++) {
      tracers[i] = factories.get(i).newServerStreamTracer(fullMethodName, headers);
    }
    return new StatsTraceContext(tracers);
  }

  @VisibleForTesting
  StatsTraceContext(StreamTracer[] tracers) {
    this.tracers = tracers;
  }

  /**
   * Returns a copy of the tracer list.
   */
  @VisibleForTesting
  List<StreamTracer> getTracersForTest() {
    return new ArrayList<StreamTracer>(Arrays.asList(tracers));
  }

  /**
   * Client-only.
   */
  public void clientHeadersSent() {
    for (StreamTracer tracer : tracers) {
      ((ClientStreamTracer) tracer).headersSent();
    }
  }

  /**
   * Server-only.
   */
  public <ReqT, RespT> Context serverFilterContext(Context context) {
    Context ctx = checkNotNull(context, "context");
    for (StreamTracer tracer : tracers) {
      ctx = ((ServerStreamTracer) tracer).filterContext(ctx);
      checkNotNull(ctx, "%s returns null context", tracer);
    }
    return ctx;
  }

  @Override
  public void streamClosed(Status status) {
    for (StreamTracer tracer : tracers) {
      tracer.streamClosed(status);
    }
  }

  @Override
  public void outboundMessage() {
    for (StreamTracer tracer : tracers) {
      tracer.outboundMessage();
    }
  }

  @Override
  public void inboundMessage() {
    for (StreamTracer tracer : tracers) {
      tracer.inboundMessage();
    }
  }

  @Override
  public void outboundUncompressedSize(long bytes) {
    for (StreamTracer tracer : tracers) {
      tracer.outboundUncompressedSize(bytes);
    }
  }

  @Override
  public void outboundWireSize(long bytes) {
    for (StreamTracer tracer : tracers) {
      tracer.outboundWireSize(bytes);
    }
  }

  @Override
  public void inboundUncompressedSize(long bytes) {
    for (StreamTracer tracer : tracers) {
      tracer.inboundUncompressedSize(bytes);
    }
  }

  @Override
  public void inboundWireSize(long bytes) {
    for (StreamTracer tracer : tracers) {
      tracer.inboundWireSize(bytes);
    }
  }
}
