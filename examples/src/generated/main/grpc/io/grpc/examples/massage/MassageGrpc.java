package io.grpc.examples.massage;

import static io.grpc.stub.ClientCalls.asyncUnaryCall;
import static io.grpc.stub.ClientCalls.asyncServerStreamingCall;
import static io.grpc.stub.ClientCalls.asyncClientStreamingCall;
import static io.grpc.stub.ClientCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ClientCalls.blockingUnaryCall;
import static io.grpc.stub.ClientCalls.blockingServerStreamingCall;
import static io.grpc.stub.ClientCalls.futureUnaryCall;
import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncServerStreamingCall;
import static io.grpc.stub.ServerCalls.asyncClientStreamingCall;
import static io.grpc.stub.ServerCalls.asyncBidiStreamingCall;

@javax.annotation.Generated("by gRPC proto compiler")
public class MassageGrpc {

  private MassageGrpc() {}

  public static final String SERVICE_NAME = "loadbalancer_gslb.client.grpc.Massage";

  // Static method descriptors that strictly reflect the proto.
  @io.grpc.ExperimentalApi
  public static final io.grpc.MethodDescriptor<io.grpc.examples.massage.MassageRequest,
      io.grpc.examples.massage.MassageReply> METHOD_GET_MASSAGE =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.UNARY,
          generateFullMethodName(
              "loadbalancer_gslb.client.grpc.Massage", "GetMassage"),
          io.grpc.protobuf.ProtoUtils.marshaller(io.grpc.examples.massage.MassageRequest.getDefaultInstance()),
          io.grpc.protobuf.ProtoUtils.marshaller(io.grpc.examples.massage.MassageReply.getDefaultInstance()));

  public static MassageStub newStub(io.grpc.Channel channel) {
    return new MassageStub(channel);
  }

  public static MassageBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    return new MassageBlockingStub(channel);
  }

  public static MassageFutureStub newFutureStub(
      io.grpc.Channel channel) {
    return new MassageFutureStub(channel);
  }

  public static interface Massage {

    public void getMassage(io.grpc.examples.massage.MassageRequest request,
        io.grpc.stub.StreamObserver<io.grpc.examples.massage.MassageReply> responseObserver);
  }

  public static interface MassageBlockingClient {

    public io.grpc.examples.massage.MassageReply getMassage(io.grpc.examples.massage.MassageRequest request);
  }

  public static interface MassageFutureClient {

    public com.google.common.util.concurrent.ListenableFuture<io.grpc.examples.massage.MassageReply> getMassage(
        io.grpc.examples.massage.MassageRequest request);
  }

  public static class MassageStub extends io.grpc.stub.AbstractStub<MassageStub>
      implements Massage {
    private MassageStub(io.grpc.Channel channel) {
      super(channel);
    }

    private MassageStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected MassageStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new MassageStub(channel, callOptions);
    }

    @java.lang.Override
    public void getMassage(io.grpc.examples.massage.MassageRequest request,
        io.grpc.stub.StreamObserver<io.grpc.examples.massage.MassageReply> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_GET_MASSAGE, getCallOptions()), request, responseObserver);
    }
  }

  public static class MassageBlockingStub extends io.grpc.stub.AbstractStub<MassageBlockingStub>
      implements MassageBlockingClient {
    private MassageBlockingStub(io.grpc.Channel channel) {
      super(channel);
    }

    private MassageBlockingStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected MassageBlockingStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new MassageBlockingStub(channel, callOptions);
    }

    @java.lang.Override
    public io.grpc.examples.massage.MassageReply getMassage(io.grpc.examples.massage.MassageRequest request) {
      return blockingUnaryCall(
          getChannel().newCall(METHOD_GET_MASSAGE, getCallOptions()), request);
    }
  }

  public static class MassageFutureStub extends io.grpc.stub.AbstractStub<MassageFutureStub>
      implements MassageFutureClient {
    private MassageFutureStub(io.grpc.Channel channel) {
      super(channel);
    }

    private MassageFutureStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected MassageFutureStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new MassageFutureStub(channel, callOptions);
    }

    @java.lang.Override
    public com.google.common.util.concurrent.ListenableFuture<io.grpc.examples.massage.MassageReply> getMassage(
        io.grpc.examples.massage.MassageRequest request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_GET_MASSAGE, getCallOptions()), request);
    }
  }

  public static io.grpc.ServerServiceDefinition bindService(
      final Massage serviceImpl) {
    return io.grpc.ServerServiceDefinition.builder(SERVICE_NAME)
      .addMethod(
        METHOD_GET_MASSAGE,
        asyncUnaryCall(
          new io.grpc.stub.ServerCalls.UnaryMethod<
              io.grpc.examples.massage.MassageRequest,
              io.grpc.examples.massage.MassageReply>() {
            @java.lang.Override
            public void invoke(
                io.grpc.examples.massage.MassageRequest request,
                io.grpc.stub.StreamObserver<io.grpc.examples.massage.MassageReply> responseObserver) {
              serviceImpl.getMassage(request, responseObserver);
            }
          })).build();
  }
}
