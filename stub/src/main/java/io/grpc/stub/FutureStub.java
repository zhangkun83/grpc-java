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

import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.ListenableFuture;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

import javax.annotation.Nullable;

/**
 * Common base type for Future-based stub implementations.
 */
public abstract class FutureStub<S extends AbstractStub<?, ?>,
    C extends AbstractServiceDescriptor<C>> extends AbstractStub<S, C> {

  /**
   * @see io.grpc.stub.AbstractStub#AbstractStub(Channel, C)
   */
  protected FutureStub(Channel channel, C config) {
    super(channel, config);
  }

  /**
   * @see io.grpc.stub.AbstractStub#AbstractStub(Channel, C, CallOptions)
   */
  protected FutureStub(Channel channel, C config, CallOptions callOptions) {
    super(channel, config, callOptions);
  }

  /**
   * Executes a unary call and returns a {@link ListenableFuture} to the response.
   *
   * @return a future for the single response message.
   */
  protected final <ReqT, RespT> ListenableFuture<RespT> unaryFutureCall(
      MethodDescriptor<ReqT, RespT> method,
      ReqT param) {
    ClientCall<ReqT, RespT> call = channel.newCall(method, callOptions);
    GrpcFuture<RespT> responseFuture = new GrpcFuture<RespT>(call);
    asyncUnaryRequestCall(call, param, new FutureCompletingListener<RespT>(responseFuture));
    return responseFuture;
  }

  /**
   * A {@link ClientCall.Listener} that completes a GrpcFuture when a response arrives.
   *
   * <p>It only allows one response. More than one responses will result in runtime exception.
   */
  private static class FutureCompletingListener<RespT> extends ClientCall.Listener<RespT> {
    private final GrpcFuture<RespT> responseFuture;
    private RespT value;

    public FutureCompletingListener(GrpcFuture<RespT> responseFuture) {
      this.responseFuture = responseFuture;
    }

    @Override
    public void onHeaders(Metadata.Headers headers) {
    }

    @Override
    public void onPayload(RespT value) {
      if (this.value != null) {
        throw Status.INTERNAL.withDescription("More than one value received for unary call")
            .asRuntimeException();
      }
      this.value = value;
    }

    @Override
    public void onClose(Status status, Metadata.Trailers trailers) {
      if (status.isOk()) {
        if (value == null) {
          // No value received so mark the future as an error
          responseFuture.setException(
              Status.INTERNAL.withDescription("No value received for unary call")
                  .asRuntimeException());
        }
        responseFuture.set(value);
      } else {
        responseFuture.setException(status.asRuntimeException());
      }
    }
  }

  private static class GrpcFuture<RespT> extends AbstractFuture<RespT> {
    private final ClientCall<?, RespT> call;

    GrpcFuture(ClientCall<?, RespT> call) {
      this.call = call;
    }

    @Override
    protected void interruptTask() {
      call.cancel();
    }

    @Override
    protected boolean set(@Nullable RespT resp) {
      return super.set(resp);
    }

    @Override
    protected boolean setException(Throwable throwable) {
      return super.setException(throwable);
    }
  }
}
