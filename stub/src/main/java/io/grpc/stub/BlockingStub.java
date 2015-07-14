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

package io.grpc.stub;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Common base type for blocking stub implementations.
 */
public abstract class BlockingStub<S extends AbstractStub<?, ?>,
    C extends AbstractServiceDescriptor<C>> extends FutureStub<S, C> {

  /**
   * @see io.grpc.stub.AbstractStub#AbstractStub(Channel, C)
   */
  protected BlockingStub(Channel channel, C config) {
    super(channel, config);
  }

  /**
   * @see io.grpc.stub.AbstractStub#AbstractStub(Channel, C, CallOptions)
   */
  protected BlockingStub(Channel channel, C config, CallOptions callOptions) {
    super(channel, config, callOptions);
  }

  /**
   * Executes a unary call and blocks on the response.
   * @return the single response message.
   */
  protected final <ReqT, RespT> RespT blockingUnaryCall(
      MethodDescriptor<ReqT, RespT> method, ReqT param) {
    Future<RespT> future = unaryFutureCall(method, param);
    try {
      return getUnchecked(future);
    } catch (Throwable t) {
      future.cancel(true);
      throw Throwables.propagate(t);
    }
  }

  /**
   * Executes a server-streaming call returning a blocking {@link Iterator} over the
   * response stream.
   * @return an iterator over the response stream.
   */
  // TODO(louiscryan): Not clear if we want to use this idiom for 'simple' stubs.
  protected final <ReqT, RespT> Iterator<RespT> blockingServerStreamingCall(
      MethodDescriptor<ReqT, RespT> method, ReqT param) {
    ClientCall<ReqT, RespT> call = channel.newCall(method, callOptions);
    BlockingResponseStream<RespT> result = new BlockingResponseStream<RespT>(call);
    asyncUnaryRequestCall(call, param, result.listener());
    return result;
  }

  /**
   * Returns the result of calling {@link Future#get()} interruptably on a task known not to throw a
   * checked exception.
   *
   * <p>If interrupted, the interrupt is restored before throwing a {@code RuntimeException}.
   *
   * @throws RuntimeException if {@code get} is interrupted
   * @throws CancellationException if {@code get} throws a {@code CancellationException}
   * @throws StatusRuntimeException if {@code get} throws an {@code ExecutionException}
   */
  private static <V> V getUnchecked(Future<V> future) {
    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      throw Status.fromThrowable(e).asRuntimeException();
    }
  }

  /**
   * Convert events on a {@link io.grpc.ClientCall.Listener} into a blocking
   * {@link Iterator}.
   *
   * <p>The class is not thread-safe, but it does permit {@link ClientCall.Listener} calls in a
   * separate thread from {@code Iterator} calls.
   */
  // TODO(ejona86): determine how to allow ClientCall.cancel() in case of application error.
  private static class BlockingResponseStream<T> implements Iterator<T> {
    // Due to flow control, only needs to hold up to 2 items: 1 for value, 1 for close.
    private final BlockingQueue<Object> buffer = new ArrayBlockingQueue<Object>(2);
    private final ClientCall.Listener<T> listener = new QueuingListener();
    private final ClientCall<?, T> call;
    // Only accessed when iterating.
    private Object last;

    private BlockingResponseStream(ClientCall<?, T> call) {
      this.call = call;
    }

    ClientCall.Listener<T> listener() {
      return listener;
    }

    @Override
    public boolean hasNext() {
      try {
        // Will block here indefinitely waiting for content. RPC timeouts defend against permanent
        // hangs here as the call will become closed.
        last = (last == null) ? buffer.take() : last;
      } catch (InterruptedException ie) {
        Thread.interrupted();
        throw new RuntimeException(ie);
      }
      if (last instanceof Status) {
        throw ((Status) last).asRuntimeException();
      }
      return last != this;
    }

    @Override
    public T next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      try {
        call.request(1);
        @SuppressWarnings("unchecked")
        T tmp = (T) last;
        return tmp;
      } finally {
        last = null;
      }
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }

    private class QueuingListener extends ClientCall.Listener<T> {
      private boolean done = false;

      @Override
      public void onHeaders(Metadata.Headers headers) {
      }

      @Override
      public void onPayload(T value) {
        Preconditions.checkState(!done, "ClientCall already closed");
        buffer.add(value);
      }

      @Override
      public void onClose(Status status, Metadata.Trailers trailers) {
        Preconditions.checkState(!done, "ClientCall already closed");
        if (status.isOk()) {
          buffer.add(BlockingResponseStream.this);
        } else {
          buffer.add(status);
        }
        done = true;
      }
    }
  }
}
