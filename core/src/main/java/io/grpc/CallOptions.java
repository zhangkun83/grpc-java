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

  public abstract static class AbstractBuilder<T extends AbstractBuilder> {
    private final CallOptions product;
    private boolean built = false;

    protected AbstractBuilder(CallOptions prototype) {
      this.product = new CallOptions(prototype);
    }

    public final T deadline(long deadline) {
      product.deadline = Optional.of(deadline);
      return (T) this;
    }

    protected final CallOptions build() {
      Preconditions.checkState(!built);
      built = true;
      return product;
    }
  }

  public static final class Builder<T extends Builder> extends AbstractBuilder<T> {
    protected Builder(CallOptions prototype) {
      super(prototype);
    }

    public CallOptions done() {
      return build();
    }
  }
}
