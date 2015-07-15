package io.grpc.testing;

import static io.grpc.stub.ServerCalls.asyncUnaryRequestCall;
import static io.grpc.stub.ServerCalls.asyncStreamingRequestCall;

@javax.annotation.Generated("by gRPC proto compiler")
public class TestServiceGrpc {

  // Static method descriptors that strictly reflect the proto.
  public static final io.grpc.MethodDescriptor<io.grpc.testing.SimpleRequest,
      io.grpc.testing.SimpleResponse> METHOD_UNARY_CALL =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.UNARY,
          "grpc.testing.TestService", "UnaryCall",
          io.grpc.protobuf.ProtoUtils.marshaller(io.grpc.testing.SimpleRequest.PARSER),
          io.grpc.protobuf.ProtoUtils.marshaller(io.grpc.testing.SimpleResponse.PARSER));
  // Static method descriptors that strictly reflect the proto.
  public static final io.grpc.MethodDescriptor<io.grpc.testing.SimpleRequest,
      io.grpc.testing.SimpleResponse> METHOD_STREAMING_CALL =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.DUPLEX_STREAMING,
          "grpc.testing.TestService", "StreamingCall",
          io.grpc.protobuf.ProtoUtils.marshaller(io.grpc.testing.SimpleRequest.PARSER),
          io.grpc.protobuf.ProtoUtils.marshaller(io.grpc.testing.SimpleResponse.PARSER));

  public static TestServiceStub newStub(io.grpc.Channel channel) {
    return new TestServiceStub(channel, CONFIG);
  }

  public static TestServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    return new TestServiceBlockingStub(channel, CONFIG);
  }

  public static TestServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    return new TestServiceFutureStub(channel, CONFIG);
  }

  // The default service descriptor
  private static final TestServiceServiceDescriptor CONFIG =
      new TestServiceServiceDescriptor();

  @javax.annotation.concurrent.Immutable
  public static class TestServiceServiceDescriptor extends
      io.grpc.stub.AbstractServiceDescriptor<TestServiceServiceDescriptor> {
    public final io.grpc.MethodDescriptor<io.grpc.testing.SimpleRequest,
        io.grpc.testing.SimpleResponse> unaryCall;
    public final io.grpc.MethodDescriptor<io.grpc.testing.SimpleRequest,
        io.grpc.testing.SimpleResponse> streamingCall;

    private TestServiceServiceDescriptor() {
      unaryCall = METHOD_UNARY_CALL;
      streamingCall = METHOD_STREAMING_CALL;
    }

    @SuppressWarnings("unchecked")
    private TestServiceServiceDescriptor(
        java.util.Map<java.lang.String, io.grpc.MethodDescriptor<?, ?>> methodMap) {
      unaryCall = (io.grpc.MethodDescriptor<io.grpc.testing.SimpleRequest,
          io.grpc.testing.SimpleResponse>) methodMap.get(
          CONFIG.unaryCall.getFullMethodName());
      streamingCall = (io.grpc.MethodDescriptor<io.grpc.testing.SimpleRequest,
          io.grpc.testing.SimpleResponse>) methodMap.get(
          CONFIG.streamingCall.getFullMethodName());
    }

    @java.lang.Override
    protected TestServiceServiceDescriptor build(
        java.util.Map<java.lang.String, io.grpc.MethodDescriptor<?, ?>> methodMap) {
      return new TestServiceServiceDescriptor(methodMap);
    }

    @java.lang.Override
    public java.util.Collection<io.grpc.MethodDescriptor<?, ?>> methods() {
      return com.google.common.collect.ImmutableList.<io.grpc.MethodDescriptor<?, ?>>of(
          unaryCall,
          streamingCall);
    }
  }

  public static interface TestService {

    public void unaryCall(io.grpc.testing.SimpleRequest request,
        io.grpc.stub.StreamObserver<io.grpc.testing.SimpleResponse> responseObserver);

    public io.grpc.stub.StreamObserver<io.grpc.testing.SimpleRequest> streamingCall(
        io.grpc.stub.StreamObserver<io.grpc.testing.SimpleResponse> responseObserver);
  }

  public static interface TestServiceBlockingClient {

    public io.grpc.testing.SimpleResponse unaryCall(io.grpc.testing.SimpleRequest request);
  }

  public static interface TestServiceFutureClient {

    public com.google.common.util.concurrent.ListenableFuture<io.grpc.testing.SimpleResponse> unaryCall(
        io.grpc.testing.SimpleRequest request);
  }

  public static class TestServiceStub extends
      io.grpc.stub.AsyncStub<TestServiceStub, TestServiceServiceDescriptor>
      implements TestService {
    private TestServiceStub(io.grpc.Channel channel,
        TestServiceServiceDescriptor config) {
      super(channel, config);
    }

    private TestServiceStub(io.grpc.Channel channel,
        TestServiceServiceDescriptor config,
        io.grpc.CallOptions callOptions) {
      super(channel, config, callOptions);
    }

    @java.lang.Override
    protected TestServiceStub build(io.grpc.Channel channel,
        TestServiceServiceDescriptor config,
        io.grpc.CallOptions callOptions) {
      return new TestServiceStub(channel, config, callOptions);
    }

    @java.lang.Override
    public void unaryCall(io.grpc.testing.SimpleRequest request,
        io.grpc.stub.StreamObserver<io.grpc.testing.SimpleResponse> responseObserver) {
      asyncUnaryCall(
          config.unaryCall, request, responseObserver);
    }

    @java.lang.Override
    public io.grpc.stub.StreamObserver<io.grpc.testing.SimpleRequest> streamingCall(
        io.grpc.stub.StreamObserver<io.grpc.testing.SimpleResponse> responseObserver) {
      return asyncDuplexStreamingCall(
          config.streamingCall, responseObserver);
    }
  }

  public static class TestServiceBlockingStub extends
      io.grpc.stub.BlockingStub<TestServiceBlockingStub, TestServiceServiceDescriptor>
      implements TestServiceBlockingClient {
    private TestServiceBlockingStub(io.grpc.Channel channel,
        TestServiceServiceDescriptor config) {
      super(channel, config);
    }

    private TestServiceBlockingStub(io.grpc.Channel channel,
        TestServiceServiceDescriptor config,
        io.grpc.CallOptions callOptions) {
      super(channel, config, callOptions);
    }

    @java.lang.Override
    protected TestServiceBlockingStub build(io.grpc.Channel channel,
        TestServiceServiceDescriptor config,
        io.grpc.CallOptions callOptions) {
      return new TestServiceBlockingStub(channel, config, callOptions);
    }

    @java.lang.Override
    public io.grpc.testing.SimpleResponse unaryCall(io.grpc.testing.SimpleRequest request) {
      return blockingUnaryCall(
          config.unaryCall, request);
    }
  }

  public static class TestServiceFutureStub extends
      io.grpc.stub.FutureStub<TestServiceFutureStub, TestServiceServiceDescriptor>
      implements TestServiceFutureClient {
    private TestServiceFutureStub(io.grpc.Channel channel,
        TestServiceServiceDescriptor config) {
      super(channel, config);
    }

    private TestServiceFutureStub(io.grpc.Channel channel,
        TestServiceServiceDescriptor config,
        io.grpc.CallOptions callOptions) {
      super(channel, config, callOptions);
    }

    @java.lang.Override
    protected TestServiceFutureStub build(io.grpc.Channel channel,
        TestServiceServiceDescriptor config,
        io.grpc.CallOptions callOptions) {
      return new TestServiceFutureStub(channel, config, callOptions);
    }

    @java.lang.Override
    public com.google.common.util.concurrent.ListenableFuture<io.grpc.testing.SimpleResponse> unaryCall(
        io.grpc.testing.SimpleRequest request) {
      return unaryFutureCall(
          config.unaryCall, request);
    }
  }

  public static io.grpc.ServerServiceDefinition bindService(
      final TestService serviceImpl) {
    return io.grpc.ServerServiceDefinition.builder("grpc.testing.TestService")
      .addMethod(io.grpc.ServerMethodDefinition.create(
          METHOD_UNARY_CALL,
          asyncUnaryRequestCall(
            new io.grpc.stub.ServerCalls.UnaryRequestMethod<
                io.grpc.testing.SimpleRequest,
                io.grpc.testing.SimpleResponse>() {
              @java.lang.Override
              public void invoke(
                  io.grpc.testing.SimpleRequest request,
                  io.grpc.stub.StreamObserver<io.grpc.testing.SimpleResponse> responseObserver) {
                serviceImpl.unaryCall(request, responseObserver);
              }
            })))
      .addMethod(io.grpc.ServerMethodDefinition.create(
          METHOD_STREAMING_CALL,
          asyncStreamingRequestCall(
            new io.grpc.stub.ServerCalls.StreamingRequestMethod<
                io.grpc.testing.SimpleRequest,
                io.grpc.testing.SimpleResponse>() {
              @java.lang.Override
              public io.grpc.stub.StreamObserver<io.grpc.testing.SimpleRequest> invoke(
                  io.grpc.stub.StreamObserver<io.grpc.testing.SimpleResponse> responseObserver) {
                return serviceImpl.streamingCall(responseObserver);
              }
            }))).build();
  }
}
