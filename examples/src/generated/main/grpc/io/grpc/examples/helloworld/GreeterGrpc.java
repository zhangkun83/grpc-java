package io.grpc.examples.helloworld;

import static io.grpc.stub.ServerCalls.asyncUnaryRequestCall;
import static io.grpc.stub.ServerCalls.asyncStreamingRequestCall;

@javax.annotation.Generated("by gRPC proto compiler")
public class GreeterGrpc {

  // Static method descriptors that strictly reflect the proto.
  public static final io.grpc.MethodDescriptor<io.grpc.examples.helloworld.HelloRequest,
      io.grpc.examples.helloworld.HelloResponse> METHOD_SAY_HELLO =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.UNARY,
          "helloworld.Greeter", "SayHello",
          io.grpc.protobuf.ProtoUtils.marshaller(io.grpc.examples.helloworld.HelloRequest.PARSER),
          io.grpc.protobuf.ProtoUtils.marshaller(io.grpc.examples.helloworld.HelloResponse.PARSER));

  public static GreeterStub newStub(io.grpc.Channel channel) {
    return new GreeterStub(channel, CONFIG);
  }

  public static GreeterBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    return new GreeterBlockingStub(channel, CONFIG);
  }

  public static GreeterFutureStub newFutureStub(
      io.grpc.Channel channel) {
    return new GreeterFutureStub(channel, CONFIG);
  }

  // The default service descriptor
  private static final GreeterServiceDescriptor CONFIG =
      new GreeterServiceDescriptor();

  @javax.annotation.concurrent.Immutable
  public static class GreeterServiceDescriptor extends
      io.grpc.stub.AbstractServiceDescriptor<GreeterServiceDescriptor> {
    public final io.grpc.MethodDescriptor<io.grpc.examples.helloworld.HelloRequest,
        io.grpc.examples.helloworld.HelloResponse> sayHello;

    private GreeterServiceDescriptor() {
      sayHello = METHOD_SAY_HELLO;
    }

    @SuppressWarnings("unchecked")
    private GreeterServiceDescriptor(
        java.util.Map<java.lang.String, io.grpc.MethodDescriptor<?, ?>> methodMap) {
      sayHello = (io.grpc.MethodDescriptor<io.grpc.examples.helloworld.HelloRequest,
          io.grpc.examples.helloworld.HelloResponse>) methodMap.get(
          CONFIG.sayHello.getFullMethodName());
    }

    @java.lang.Override
    protected GreeterServiceDescriptor build(
        java.util.Map<java.lang.String, io.grpc.MethodDescriptor<?, ?>> methodMap) {
      return new GreeterServiceDescriptor(methodMap);
    }

    @java.lang.Override
    public java.util.Collection<io.grpc.MethodDescriptor<?, ?>> methods() {
      return com.google.common.collect.ImmutableList.<io.grpc.MethodDescriptor<?, ?>>of(
          sayHello);
    }
  }

  public static interface Greeter {

    public void sayHello(io.grpc.examples.helloworld.HelloRequest request,
        io.grpc.stub.StreamObserver<io.grpc.examples.helloworld.HelloResponse> responseObserver);
  }

  public static interface GreeterBlockingClient {

    public io.grpc.examples.helloworld.HelloResponse sayHello(io.grpc.examples.helloworld.HelloRequest request);
  }

  public static interface GreeterFutureClient {

    public com.google.common.util.concurrent.ListenableFuture<io.grpc.examples.helloworld.HelloResponse> sayHello(
        io.grpc.examples.helloworld.HelloRequest request);
  }

  public static class GreeterStub extends
      io.grpc.stub.AsyncStub<GreeterStub, GreeterServiceDescriptor>
      implements Greeter {
    private GreeterStub(io.grpc.Channel channel,
        GreeterServiceDescriptor config) {
      super(channel, config);
    }

    private GreeterStub(io.grpc.Channel channel,
        GreeterServiceDescriptor config,
        io.grpc.CallOptions callOptions) {
      super(channel, config, callOptions);
    }

    @java.lang.Override
    protected GreeterStub build(io.grpc.Channel channel,
        GreeterServiceDescriptor config,
        io.grpc.CallOptions callOptions) {
      return new GreeterStub(channel, config, callOptions);
    }

    @java.lang.Override
    public void sayHello(io.grpc.examples.helloworld.HelloRequest request,
        io.grpc.stub.StreamObserver<io.grpc.examples.helloworld.HelloResponse> responseObserver) {
      asyncUnaryCall(
          config.sayHello, request, responseObserver);
    }
  }

  public static class GreeterBlockingStub extends
      io.grpc.stub.BlockingStub<GreeterBlockingStub, GreeterServiceDescriptor>
      implements GreeterBlockingClient {
    private GreeterBlockingStub(io.grpc.Channel channel,
        GreeterServiceDescriptor config) {
      super(channel, config);
    }

    private GreeterBlockingStub(io.grpc.Channel channel,
        GreeterServiceDescriptor config,
        io.grpc.CallOptions callOptions) {
      super(channel, config, callOptions);
    }

    @java.lang.Override
    protected GreeterBlockingStub build(io.grpc.Channel channel,
        GreeterServiceDescriptor config,
        io.grpc.CallOptions callOptions) {
      return new GreeterBlockingStub(channel, config, callOptions);
    }

    @java.lang.Override
    public io.grpc.examples.helloworld.HelloResponse sayHello(io.grpc.examples.helloworld.HelloRequest request) {
      return blockingUnaryCall(
          config.sayHello, request);
    }
  }

  public static class GreeterFutureStub extends
      io.grpc.stub.FutureStub<GreeterFutureStub, GreeterServiceDescriptor>
      implements GreeterFutureClient {
    private GreeterFutureStub(io.grpc.Channel channel,
        GreeterServiceDescriptor config) {
      super(channel, config);
    }

    private GreeterFutureStub(io.grpc.Channel channel,
        GreeterServiceDescriptor config,
        io.grpc.CallOptions callOptions) {
      super(channel, config, callOptions);
    }

    @java.lang.Override
    protected GreeterFutureStub build(io.grpc.Channel channel,
        GreeterServiceDescriptor config,
        io.grpc.CallOptions callOptions) {
      return new GreeterFutureStub(channel, config, callOptions);
    }

    @java.lang.Override
    public com.google.common.util.concurrent.ListenableFuture<io.grpc.examples.helloworld.HelloResponse> sayHello(
        io.grpc.examples.helloworld.HelloRequest request) {
      return unaryFutureCall(
          config.sayHello, request);
    }
  }

  public static io.grpc.ServerServiceDefinition bindService(
      final Greeter serviceImpl) {
    return io.grpc.ServerServiceDefinition.builder("helloworld.Greeter")
      .addMethod(io.grpc.ServerMethodDefinition.create(
          METHOD_SAY_HELLO,
          asyncUnaryRequestCall(
            new io.grpc.stub.ServerCalls.UnaryRequestMethod<
                io.grpc.examples.helloworld.HelloRequest,
                io.grpc.examples.helloworld.HelloResponse>() {
              @java.lang.Override
              public void invoke(
                  io.grpc.examples.helloworld.HelloRequest request,
                  io.grpc.stub.StreamObserver<io.grpc.examples.helloworld.HelloResponse> responseObserver) {
                serviceImpl.sayHello(request, responseObserver);
              }
            }))).build();
  }
}
