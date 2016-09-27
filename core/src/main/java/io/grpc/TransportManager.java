/*
 * Copyright 2015, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *
 *    * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.grpc;

import com.google.common.base.Supplier;

import java.util.Collection;

/**
 * A facade of the channel's connection creation and management capabilities, provided to {@link
 * LoadBalancer}s.
 */
@ExperimentalApi("https://github.com/grpc/grpc-java/issues/1781")
public abstract class TransportManager<T> {
  /**
   * Creates a {@link Subchannel} for the given address group.
   *
   * <p>Calls made by LoadBalancer directly on a Subchannel will not go through the interceptors
   * on the top-level channel.
   *
   * <p>The LoadBalancer should shut it down when no longer used.
   */
  public abstract Subchannel<T> createSubchannel(EquivalentAddressGroup addressGroup);

  /**
   * Creates a transport that would fail all RPCs with the given error.
   */
  public abstract T createFailingTransport(Status error);

  /**
   * Returns a transport that is not associated with any address. It holds RPCs until it's closed,
   * at which moment all held RPCs are transferred to actual transports.
   *
   * <p>This method is typically used in lieu of {@link #getTransport} before server addresses are
   * known.
   *
   * <p>The LoadBalancer should shut it down when no longer used.
   */
  public abstract InterimTransport<T> createInterimTransport();

  /**
   * Creates a channel for out-of-band communications, usually used by a load-balancer that needs to
   * communicate with an external load-balancing service which is under an authority different from
   * what the channel is associated with.
   *
   * <p>Calls made by LoadBalancer directly on this OOB channel will not go through the interceptors
   * on the top-level channel.
   *
   * <p>The LoadBalancer should shut it down when no longer used.
   */
  public abstract ManagedChannel createOobChannel(
      EquivalentAddressGroup addressGroup, String authority);

  /**
   * A transport provided as a temporary holder of new requests, which will be eventually
   * transferred to real transports or fail.
   *
   * @see #createInterimTransport
   */
  public interface InterimTransport<T> {
    /**
     * Returns the transport object.
     *
     * @throws IllegalStateException if {@link #closeWithRealTransports} or {@link #closeWithError}
     *     has been called
     */
    T transport();

    /**
     * Closes the interim transport by transferring pending RPCs to the given real transports.
     *
     * <p>Each pending RPC will result in an invocation to {@link Supplier#get} once.
     */
    void closeWithRealTransports(Supplier<T> realTransports);

    /**
     * Closes the interim transport by failing all pending RPCs with the given error.
     */
    void closeWithError(Status error);
  }

  /**
   * A subchannel that manages the transports for a {@link EquivalentAddressGroup}.
   */
  public static abstract class Subchannel<T> extends ManagedChannel {
    /**
     * Returns a usable transport.
     */
    public abstract T getTransport();

    /**
     * The address group with which this subchannel is associated.
     */
    public abstract EquivalentAddressGroup getAddresses();
  }
}
