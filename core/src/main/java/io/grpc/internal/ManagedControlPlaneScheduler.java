package io.grpc.internal;

public abstract class ManagedControlPlaneScheduler extends ControlPlaneScheduler {
  /**
   * Shut down this scheduler and release all resources.
   */
  public abstract void shutdown();
}
