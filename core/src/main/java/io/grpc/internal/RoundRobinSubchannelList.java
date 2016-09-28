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

package io.grpc.internal;

import com.google.common.annotations.VisibleForTesting;

import io.grpc.Status;
import io.grpc.TransportManager;
import io.grpc.TransportManager.Subchannel;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Manages a list of subchannels to round-robin on.  It can be created in two modes:
 * <ol>
 *   <li>Normal mode: it's initialized with a list of Subchannels. {@link #getNextTransport}
 *       round-robins on the list. In this mode, {@link #getError} returns {@code null}</li>
 *   <li>Error mode: it's initialized with an error Status. {@link #getError} returns the error,
 *       and {@link #getNextTransport} always returns a failing transport that would fail requests
 *       with that error.</li>
 * </ol>
 */
@ThreadSafe
public final class RoundRobinSubchannelList<T> {
  private final TransportManager<T> tm;
  private final List<Subchannel<T>> list;
  private final Iterator<Subchannel<T>> cyclingIter;
  private final T requestDroppingTransport;

  private final T erroringTransport;
  private final Status errorStatus;

  /**
   * Creates an instance in normal mode.
   */
  RoundRobinSubchannelList(TransportManager<T> tm, List<Subchannel<T>> list) {
    this.tm = checkNotNull(tm, "tm");
    this.list = Collections.unmodifiableList(new ArrayList(list));
    this.cyclingIter = new CycleIterator<Subchannel<T>>(list);
    this.requestDroppingTransport =
      tm.createFailingTransport(Status.UNAVAILABLE.withDescription("Throttled by LB"));
    this.erroringTransport = null;
    this.errorStatus = null;
  }

  /**
   * Creates an instance in error mode.
   */
  RoundRobinSubchannelList(TransportManager<T> tm, Status error) {
    this.tm = checkNotNull(tm, "tm");
    this.list = null;
    this.cyclingIter = null;
    this.requestDroppingTransport = null;

    this.erroringTransport = tm.createFailingTransport(error);
    this.errorStatus = error;
  }

  /**
   * Returns the transport of the next subchannel, or a failing transport if in error mode.
   */
  public T getNextTransport() {
    if (erroringTransport != null) {
      return erroringTransport;
    }
    Subchannel<T> next;
    synchronized (cyclingIter) {
      next = cyclingIter.next();
    }
    if (next == null) {
      return requestDroppingTransport;
    }
    return next.getTransport();
  }

  /**
   * Returns the error if in error mode, or {@code null} if in normal mode.
   */
  @Nullable
  public Status getError() {
    return errorStatus;
  }

  /**
   * Returns the size of the list, or 0 if in error mode.
   */
  public int size() {
    if (list == null) {
      return 0;
    }
    return list.size();
  }

  private static final class CycleIterator<T> implements Iterator<T> {
    private final List<T> list;
    private int index;

    public CycleIterator(List<T> list) {
      this.list = list;
    }

    @Override
    public boolean hasNext() {
      return !list.isEmpty();
    }

    @Override
    public T next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      T val = list.get(index);
      index++;
      if (index >= list.size()) {
        index = 0;
      }
      return val;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}
