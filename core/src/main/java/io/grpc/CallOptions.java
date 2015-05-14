package io.grpc;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/** TODO: http://go/java-style#javadoc */
public final class CallOptions {

  public static final CallOptions BLANK = new CallOptions();

  private Optional<Long> deadline = Optional.absent();

  private CallOptions() {
  }

  private CallOptions(CallOptions prototype) {
    deadline = prototype.deadline;
  }

  public static Builder builder() {
    return new Builder(new CallOptions());
  }

  public Builder reconfigure() {
    return new Builder(this);
  }

  public static class Builder<T extends Builder> {
    private final CallOptions product;
    private boolean built = false;

    protected Builder(CallOptions prototype) {
      this.product = new CallOptions(prototype);
    }

    public T deadline(long deadline) {
      product.deadline = Optional.of(deadline);
      return (T) this;
    }

    public CallOptions build() {
      Preconditions.checkState(!built);
      built = true;
      return product;
    }
  }
}
