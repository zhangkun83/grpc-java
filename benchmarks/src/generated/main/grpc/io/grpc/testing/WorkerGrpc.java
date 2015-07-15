package io.grpc.testing;

import static io.grpc.stub.ServerCalls.asyncUnaryRequestCall;
import static io.grpc.stub.ServerCalls.asyncStreamingRequestCall;

@javax.annotation.Generated("by gRPC proto compiler")
public class WorkerGrpc {

  // Static method descriptors that strictly reflect the proto.
  public static final io.grpc.MethodDescriptor<io.grpc.testing.ClientArgs,
      io.grpc.testing.ClientStatus> METHOD_RUN_TEST =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.DUPLEX_STREAMING,
          "grpc.testing.Worker", "RunTest",
          io.grpc.protobuf.ProtoUtils.marshaller(io.grpc.testing.ClientArgs.PARSER),
          io.grpc.protobuf.ProtoUtils.marshaller(io.grpc.testing.ClientStatus.PARSER));
  // Static method descriptors that strictly reflect the proto.
  public static final io.grpc.MethodDescriptor<io.grpc.testing.ServerArgs,
      io.grpc.testing.ServerStatus> METHOD_RUN_SERVER =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.DUPLEX_STREAMING,
          "grpc.testing.Worker", "RunServer",
          io.grpc.protobuf.ProtoUtils.marshaller(io.grpc.testing.ServerArgs.PARSER),
          io.grpc.protobuf.ProtoUtils.marshaller(io.grpc.testing.ServerStatus.PARSER));

  public static WorkerStub newStub(io.grpc.Channel channel) {
    return new WorkerStub(channel, CONFIG);
  }

  public static WorkerBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    return new WorkerBlockingStub(channel, CONFIG);
  }

  public static WorkerFutureStub newFutureStub(
      io.grpc.Channel channel) {
    return new WorkerFutureStub(channel, CONFIG);
  }

  // The default service descriptor
  private static final WorkerServiceDescriptor CONFIG =
      new WorkerServiceDescriptor();

  @javax.annotation.concurrent.Immutable
  public static class WorkerServiceDescriptor extends
      io.grpc.stub.AbstractServiceDescriptor<WorkerServiceDescriptor> {
    public final io.grpc.MethodDescriptor<io.grpc.testing.ClientArgs,
        io.grpc.testing.ClientStatus> runTest;
    public final io.grpc.MethodDescriptor<io.grpc.testing.ServerArgs,
        io.grpc.testing.ServerStatus> runServer;

    private WorkerServiceDescriptor() {
      runTest = METHOD_RUN_TEST;
      runServer = METHOD_RUN_SERVER;
    }

    @SuppressWarnings("unchecked")
    private WorkerServiceDescriptor(
        java.util.Map<java.lang.String, io.grpc.MethodDescriptor<?, ?>> methodMap) {
      runTest = (io.grpc.MethodDescriptor<io.grpc.testing.ClientArgs,
          io.grpc.testing.ClientStatus>) methodMap.get(
          CONFIG.runTest.getFullMethodName());
      runServer = (io.grpc.MethodDescriptor<io.grpc.testing.ServerArgs,
          io.grpc.testing.ServerStatus>) methodMap.get(
          CONFIG.runServer.getFullMethodName());
    }

    @java.lang.Override
    protected WorkerServiceDescriptor build(
        java.util.Map<java.lang.String, io.grpc.MethodDescriptor<?, ?>> methodMap) {
      return new WorkerServiceDescriptor(methodMap);
    }

    @java.lang.Override
    public java.util.Collection<io.grpc.MethodDescriptor<?, ?>> methods() {
      return com.google.common.collect.ImmutableList.<io.grpc.MethodDescriptor<?, ?>>of(
          runTest,
          runServer);
    }
  }

  public static interface Worker {

    public io.grpc.stub.StreamObserver<io.grpc.testing.ClientArgs> runTest(
        io.grpc.stub.StreamObserver<io.grpc.testing.ClientStatus> responseObserver);

    public io.grpc.stub.StreamObserver<io.grpc.testing.ServerArgs> runServer(
        io.grpc.stub.StreamObserver<io.grpc.testing.ServerStatus> responseObserver);
  }

  public static interface WorkerBlockingClient {
  }

  public static interface WorkerFutureClient {
  }

  public static class WorkerStub extends
      io.grpc.stub.AsyncStub<WorkerStub, WorkerServiceDescriptor>
      implements Worker {
    private WorkerStub(io.grpc.Channel channel,
        WorkerServiceDescriptor config) {
      super(channel, config);
    }

    private WorkerStub(io.grpc.Channel channel,
        WorkerServiceDescriptor config,
        io.grpc.CallOptions callOptions) {
      super(channel, config, callOptions);
    }

    @java.lang.Override
    protected WorkerStub build(io.grpc.Channel channel,
        WorkerServiceDescriptor config,
        io.grpc.CallOptions callOptions) {
      return new WorkerStub(channel, config, callOptions);
    }

    @java.lang.Override
    public io.grpc.stub.StreamObserver<io.grpc.testing.ClientArgs> runTest(
        io.grpc.stub.StreamObserver<io.grpc.testing.ClientStatus> responseObserver) {
      return asyncDuplexStreamingCall(
          config.runTest, responseObserver);
    }

    @java.lang.Override
    public io.grpc.stub.StreamObserver<io.grpc.testing.ServerArgs> runServer(
        io.grpc.stub.StreamObserver<io.grpc.testing.ServerStatus> responseObserver) {
      return asyncDuplexStreamingCall(
          config.runServer, responseObserver);
    }
  }

  public static class WorkerBlockingStub extends
      io.grpc.stub.BlockingStub<WorkerBlockingStub, WorkerServiceDescriptor>
      implements WorkerBlockingClient {
    private WorkerBlockingStub(io.grpc.Channel channel,
        WorkerServiceDescriptor config) {
      super(channel, config);
    }

    private WorkerBlockingStub(io.grpc.Channel channel,
        WorkerServiceDescriptor config,
        io.grpc.CallOptions callOptions) {
      super(channel, config, callOptions);
    }

    @java.lang.Override
    protected WorkerBlockingStub build(io.grpc.Channel channel,
        WorkerServiceDescriptor config,
        io.grpc.CallOptions callOptions) {
      return new WorkerBlockingStub(channel, config, callOptions);
    }
  }

  public static class WorkerFutureStub extends
      io.grpc.stub.FutureStub<WorkerFutureStub, WorkerServiceDescriptor>
      implements WorkerFutureClient {
    private WorkerFutureStub(io.grpc.Channel channel,
        WorkerServiceDescriptor config) {
      super(channel, config);
    }

    private WorkerFutureStub(io.grpc.Channel channel,
        WorkerServiceDescriptor config,
        io.grpc.CallOptions callOptions) {
      super(channel, config, callOptions);
    }

    @java.lang.Override
    protected WorkerFutureStub build(io.grpc.Channel channel,
        WorkerServiceDescriptor config,
        io.grpc.CallOptions callOptions) {
      return new WorkerFutureStub(channel, config, callOptions);
    }
  }

  public static io.grpc.ServerServiceDefinition bindService(
      final Worker serviceImpl) {
    return io.grpc.ServerServiceDefinition.builder("grpc.testing.Worker")
      .addMethod(io.grpc.ServerMethodDefinition.create(
          METHOD_RUN_TEST,
          asyncStreamingRequestCall(
            new io.grpc.stub.ServerCalls.StreamingRequestMethod<
                io.grpc.testing.ClientArgs,
                io.grpc.testing.ClientStatus>() {
              @java.lang.Override
              public io.grpc.stub.StreamObserver<io.grpc.testing.ClientArgs> invoke(
                  io.grpc.stub.StreamObserver<io.grpc.testing.ClientStatus> responseObserver) {
                return serviceImpl.runTest(responseObserver);
              }
            })))
      .addMethod(io.grpc.ServerMethodDefinition.create(
          METHOD_RUN_SERVER,
          asyncStreamingRequestCall(
            new io.grpc.stub.ServerCalls.StreamingRequestMethod<
                io.grpc.testing.ServerArgs,
                io.grpc.testing.ServerStatus>() {
              @java.lang.Override
              public io.grpc.stub.StreamObserver<io.grpc.testing.ServerArgs> invoke(
                  io.grpc.stub.StreamObserver<io.grpc.testing.ServerStatus> responseObserver) {
                return serviceImpl.runServer(responseObserver);
              }
            }))).build();
  }
}
