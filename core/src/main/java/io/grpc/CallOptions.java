package io.grpc;

import com.google.common.base.Optional;

/**
 * The bundle of immutable options that are used when making an RPC call on the client-side.
 *
 * <p>Each option can be set through {@link Builder}. If an option has not been set, an internally
 * defined default value is returned upon inquiry.
 *
 * <p>It remembers whether each option has been set. A {@code CallOptions} with some options set can
 * be used to overwrite these options on another {@code CallOptions}, by using {@link #apply}.
 *
 * <p>To create a new {@code CallOptions}:
 * <pre>
 * CallOptions options = CallOptions.builder().setTimeout(3, TimeUnit.SECONDS).build();
 * </pre>
 *
 * <p>To apply the options set by the second {@code CallOptions} to the first:
 * <pre>
 * CallOptions newOptions = first.apply(second);
 * </pre>
 *
 * <p>To overwrite some fields and get a new {@code CallOptions}:
 * <pre>
 * CallOptions newOptions = original.alter().setTimeout(3, TimeUnit.SECONDS).build();
 * </pre>
 *
 */
public final class CallOptions {
  private static final long DEFAULT_TIMEOUT_MICROS = 0;

  public static CallOptions BLANK = new CallOptions();

  private Optional<Long> timeoutMicros = Optional.absent();

  /**
   * Timeout for the call in microseconds.
   */
  public long timeoutMicros() {
    return timeoutMicros.or(DEFAULT_TIMEOUT_MICROS);
  }

  private CallOptions() { }

  /**
   * Returns a <b>new</b> {@code CallOptions} that is based on this {@code CallOptions}, while
   * taking the options that are set in {@code overrider}.
   */
  public CallOptions apply(CallOptions overrider) {
    CallOptions product = new CallOptions();
    product.timeoutMicros = overrider.timeoutMicros.or(timeoutMicros);
    return product;
  }

  /**
   * Returns a builder with the initial values identical to this {@code CallOptions}
   */
  public Builder alter() {
    // Make a clone.
    CallOptions unfinishedProduct = apply(BLANK);
    return new Builder(unfinishedProduct);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final CallOptions product;
    private boolean built = false;

    private Builder() {
      product = new CallOptions();
    }

    private Builder(CallOptions unfinishedProduct) {
      product = unfinishedProduct;
    }

    public CallOptions build() {
      Preconditions.checkState(!built);
      built = true;
      return product;
    }

    public void setTimeoutMicros(long timeoutMicros) {
      product.timeoutMicros = Optional.of(timeoutMicros);
    }

    public void setTimeout(long timeout, TimeUnit timeUnit) {
      setTimeoutMicros(timeoutUnit.toMicros(timeout));
    }
}
