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

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

/**
 * Common base type for async stub implementations.
 */
public abstract class AsyncStub<S extends AbstractStub<?, ?>,
    C extends AbstractServiceDescriptor<C>> extends AbstractStub<S, C> {

  /**
   * @see io.grpc.stub.AbstractStub#Constructor(Channel, C)
   */
  protected AsyncStub(Channel channel, C config) {
    super(channel, config);
  }

  /**
   * @see io.grpc.stub.AbstractStub#Constructor(Channel, C, CallOptions)
   */
  protected AsyncStub(Channel channel, C config, CallOptions callOptions) {
    super(channel, config, callOptions);
  }

  /**
   * Executes a unary call with a response {@link StreamObserver}.
   */
  protected final <ReqT, RespT> void asyncUnaryCall(
      MethodDescriptor<ReqT, RespT> method, ReqT param, StreamObserver<RespT> observer) {
    asyncUnaryRequestCall(method, param, observer, false);
  }

  /**
   * Executes a server-streaming call with a response {@link StreamObserver}.
   */
  protected final <ReqT, RespT> void asyncServerStreamingCall(
      MethodDescriptor<ReqT, RespT> method, ReqT param, StreamObserver<RespT> observer) {
    asyncUnaryRequestCall(method, param, observer, true);
  }

  /**
   * Executes a client-streaming call returning a {@link StreamObserver} for the request messages.
   * @return request stream observer.
   */
  protected final <ReqT, RespT> StreamObserver<ReqT> asyncClientStreamingCall(
      MethodDescriptor<ReqT, RespT> method, StreamObserver<RespT> responseObserver) {
    return asyncStreamingRequestCall(method, responseObserver, false);
  }

  /**
   * Executes a duplex-streaming call.
   * @return request stream observer.
   */
  protected final <ReqT, RespT> StreamObserver<ReqT> asyncDuplexStreamingCall(
      MethodDescriptor<ReqT, RespT> method, StreamObserver<RespT> responseObserver) {
    return asyncStreamingRequestCall(method, responseObserver, true);
  }

  private <ReqT, RespT> void asyncUnaryRequestCall(
      MethodDescriptor<ReqT, RespT> method, ReqT param, StreamObserver<RespT> responseObserver,
      boolean streamingResponse) {
    ClientCall<ReqT, RespT> call = channel.newCall(method, callOptions);
    asyncUnaryRequestCall(call, param,
        new StreamObserverToCallListenerAdapter<RespT>(call, responseObserver, streamingResponse));
  }

  private <ReqT, RespT> StreamObserver<ReqT> asyncStreamingRequestCall(
      MethodDescriptor<ReqT, RespT> method, StreamObserver<RespT> responseObserver,
      boolean streamingResponse) {
    ClientCall<ReqT, RespT> call = channel.newCall(method, callOptions);
    call.start(
        new StreamObserverToCallListenerAdapter<RespT>(call, responseObserver, streamingResponse),
        new Metadata.Headers());
    call.request(1);
    return new CallToStreamObserverAdapter<ReqT>(call);
  }

  private static final class StreamObserverToCallListenerAdapter<RespT>
      extends ClientCall.Listener<RespT> {
    private final ClientCall<?, RespT> call;
    private final StreamObserver<RespT> observer;
    private final boolean streamingResponse;

    public StreamObserverToCallListenerAdapter(
        ClientCall<?, RespT> call, StreamObserver<RespT> observer, boolean streamingResponse) {
      this.call = call;
      this.observer = observer;
      this.streamingResponse = streamingResponse;
    }

    @Override
    public void onHeaders(Metadata.Headers headers) {
    }

    @Override
    public void onPayload(RespT payload) {
      observer.onValue(payload);

      if (streamingResponse) {
        // Request delivery of the next inbound message.
        call.request(1);
      }
    }

    @Override
    public void onClose(Status status, Metadata.Trailers trailers) {
      if (status.isOk()) {
        observer.onCompleted();
      } else {
        observer.onError(status.asRuntimeException());
      }
    }
  }

  private static class CallToStreamObserverAdapter<T> implements StreamObserver<T> {
    private final ClientCall<T, ?> call;

    public CallToStreamObserverAdapter(ClientCall<T, ?> call) {
      this.call = call;
    }

    @Override
    public void onValue(T value) {
      call.sendPayload(value);
    }

    @Override
    public void onError(Throwable t) {
      // TODO(ejona86): log?
      call.cancel();
    }

    @Override
    public void onCompleted() {
      call.halfClose();
    }
  }
}
