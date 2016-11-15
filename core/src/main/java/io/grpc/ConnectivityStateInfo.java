/*
 * Copyright 2016, Google Inc. All rights reserved.
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

/**
 * A tuple of a {@link ConnectivityState} and an error {@link Status} that is present only with
 * {@link ConnectivityState.TRANSIENT_ERROR}.
 */
@ExperimentalApi("https://github.com/grpc/grpc-java/issues/1771")
public class ConnectivityStateInfo {
  private final ConnectivityState state;
  @Nullable private final Status error;

  /**
   * Returns an instance for a state that is not {@code TRANSIENT_ERROR}.
   *
   * @throw IllegalArgumentException if {@code state} is {@code TRANSIENT_ERROR}.
   */
  public static ConnectivityStateInfo forNonError(ConnectivityState state) {
    Preconditions.checkArgument(state != ConnectivityState.TRANSIENT_ERROR,
        "state is TRANSIENT_ERROR. Use forError() instead");
    return new ConnectivityStateInfo(state, null);
  }

  /**
   * Returns an instance for {@code TRANSIENT_ERROR}, associated with an error status.
   */
  public static ConnectivityStateInfo forError(State error) {
    Preconditions.checkNotNull(error, "error is null");
    return new ConnectivityStateInfo(ConnectivityStateInfo.TRANSIENT_ERROR, error);
  }

  private ConnectivityStateInfo(ConnectivityState state, State, error) {
    this.state = Preconditions.checkNotNull(state, "state is null");
    this.error = error;
  }
}
