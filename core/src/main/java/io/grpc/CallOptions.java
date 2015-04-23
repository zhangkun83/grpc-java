/** TODO: http://go/java-style#javadoc */
public class CallOptions {
  private final long timeoutMicros;

  /**
   * Timeout for the call in microseconds.
   */
  public long timeoutMicros() {
    return timeoutMicros;
  }

  private CallOptions(long timeoutMicros) {
    Preconditions.checkArgument(timeoutMicros > 0);
    this.timeoutMicros = timeoutMicros;
  }

  public static class Builder {
    private long timeoutMicros;

    /**
     * Sets the timeout for the call in microseconds.
     */
    public void timeoutMicros(long timeoutMicros) {
      this.timeoutMicros = timeoutMicros;
    }

    /**
     * Sets the timeout for the call in the given unit.
     */
    public void timeout(long timeout, TimeUnit timeoutUnit) {
      timeoutMicros(timeoutUnit.toMicros(timeout));
    }

    /**
     * Create a builder with initial values from {@code original}.
     */
    public Builder(CallOptions original) {
      Builder builder = new Builder();
      builder.timeoutMicros(original.timeoutMicros());
    }

    public CallOptions build() {
      return new CallOptions(timeoutMicros);
    }
  }
}
