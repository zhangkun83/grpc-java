package io.grpc.services.HealthChecker;

/**
 * Manages the health-checking for a single Subchannel.
 *
 * <p>All methods must be called from the same channel executor as the one under the Helper passed
 * to the constructor.
 */
abstract class HealthChecker {
  private static final Logger log = Logger.getLogger(HealthChecker.class.getName());

  private final StreamObserver<HealthCheckResponse> responseObserver =
      new StreamObserver<HealthCheckResponse>() {
        @Override
        public void onNext(final HealthCheckResponse value) {
          helper.runSerialized(
              new Runnable() {
                @Override
                public void run() {
                  handleRpcResponse(value);
                }
              });
        }

        @Override
        public void onError(final Throwable t) {
          helper.runSerialized(
              new Runnable() {
                @Override
                public void run() {
                  handleRpcClosed(Status.fromThrowable(t));
                }
              });
        }

        @Override
        public void onCompleted() {
          helper.runSerialized(
              new Runnable() {
                @Override
                public void run() {
                  handleRpcClosed(Status.OK);
                }
              });
        }
      };

  private final String service;
  private final BackoffPolicy.Provider backoffPolicyProvider;
  private final LoadBalancer.Helper helper;
  private Channel channel;
  private HealthGrpc.HealthStub stub;
  // Non-null if subchannelState == READY
  private CancellableContext requestContext;
  // The underyling subchannel state
  private ConnectivityState subchannelState = ConnectivityState.IDLE;
  // The subchannel state concluded by health checker
  private ConnectivityStateInfo concludedState;
  private boolean disabled;

  HealthChecker(
      String service, BackoffPolicy.Provider backoffPolicyProvider, LoadBalancer.Helper helper,
      ScheduledExecutorService timerService) {
    this.service = checkNotNull(service, "service");
    this.backoffPolicyProvider = checkNotNull(backoffPolicyProvider, "backoffPolicyProvider");
    this.helper = checkNotNull(helper, "helper");
  }

  /**
   * Initializes this checker with the given Subchannel.
   */
  final void init(LoadBalancer.SubChannel subchannel) {
    checkState(channel == null, "init() already called");
    channel = checkNotNull(subchannel.asChannel(), "subchannel.asChannel()");
    stub = HealthGrpc.newStub(channel);
  }

  final void handleSubchannelState(ConnectivityState state) {
    checkNotNull(stub, "init() not called");
    if (disabled || state == subchannelState) {
      return;
    }
    if (state == ConnectivityState.READY) {
      checkState(requestContext == null, "Previous RPC not finished yet");
      requestContext = Context.current().withCancellation();
      Context prevCtx = requestContext.attach();
      try {
        stub.watch(HealthCheckRequest.newBuilder().setService(service).build(), responseObserver);
      } finally {
        requestContext.detach(prevCtx);
      }
    } else if (subchannelState == ConnectivityState.READY) {
      checkState(requestContext != null, "RPC was not started when Subchannel was READY");
      requestContext.close();
      requestContext = null;
    }
    subchannelState = state;
  }

  private void handleRpcClosed(Status status) {
    if (status.getCode() == Status.Code.UNIMPLEMENTED) {
      // TODO(zhangkun): record a channel trace
      logger.warning("Disabling health-checking for " + toString()
          + " because server has responded " + status);
      disabled = true;
      return;
    }
    
  }

  private void handleRpcResponse(HealthCheckResponse response) {
  }

  /**
   * Called when health checker has concluded a new state.
   */
  abstract void handleStateChange(ConnectivityStateInfo newState);

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("service", service)
        .add("subchannel", subchannel)
        .toString();
  }
}
