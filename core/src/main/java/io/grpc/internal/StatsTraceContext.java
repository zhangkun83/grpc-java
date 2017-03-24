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
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StreamTracer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The stats and tracing information for a call.
 */
// TODO(zhangkun83): thread-safety of this class
public final class StatsTraceContext extends StreamTracer {
  private final StreamTracer[] streamTracers;

  /**
   * Creates a {@code StatsTraceContext} from a list of tracers.
   */
  public StatsTraceContext(List<StreamTracer> tracers) {
    streamTracers = tracers.toArray(new StreamTracer[tracers.size()]);
  }

  @Override
  public void headersSent() {
    for (StreamTracer tracer : streamTracers) {
      tracer.headersSent();
    }
  }

  @Override
  public void streamClosed(Status status) {
    for (StreamTracer tracer : streamTracers) {
      tracer.streamClosed(status);
    }
  }

  @Override
  public void outboundMessage() {
    for (StreamTracer tracer : streamTracers) {
      tracer.outboundMessage();
    }
  }

  @Override
  public void inboundMessage() {
    for (StreamTracer tracer : streamTracers) {
      tracer.inboundMessage();
    }
  }

  @Override
  public void outboundUncompressedSize(int bytes) {
    for (StreamTracer tracer : streamTracers) {
      tracer.outboundUncompressedSize(bytes);
    }
  }

  @Override
  public void outboundWireSize(int bytes) {
    for (StreamTracer tracer : streamTracers) {
      tracer.outboundWireSize(bytes);
    }
  }

  @Override
  public void inboundUncompressedSize(int bytes) {
    for (StreamTracer tracer : streamTracers) {
      tracer.inboundUncompressedSize(bytes);
    }
  }

  @Override
  public void inboundWireSize(int bytes) {
    for (StreamTracer tracer : streamTracers) {
      tracer.inboundWireSize(bytes);
    }
  }
}
