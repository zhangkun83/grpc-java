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
 * Manages a list of subchannels to round-robin on.
 */
@ThreadSafe
public class RoundRobinSubchannelList<T> {
  private final TransportManager<T> tm;
  private final List<Subchannel<T>> list;
  private final Iterator<Subchannel<T>> cyclingIter;
  private final T requestDroppingTransport;

  private RoundRobinSubchannelList(TransportManager<T> tm, List<Subchannel<T>> list) {
    this.tm = tm;
    this.list = list;
    this.cyclingIter = new CycleIterator<Subchannel<T>>(list);
    this.requestDroppingTransport =
      tm.createFailingTransport(Status.UNAVAILABLE.withDescription("Throttled by LB"));
  }

  /**
   * Returns the transport of the next subchannel.
   */
  public T getNextTransport() {
    Subchannel<T> next;
    synchronized (cyclingIter) {
      next = cyclingIter.next();
    }
    if (next == null) {
      return requestDroppingTransport;
    }
    return next.getTransport();
  }

  public int size() {
    return list.size();
  }

  @NotThreadSafe
  public static class Builder<T> {
    private final List<Subchannel<T>> list = new ArrayList<Subchannel<T>>();
    private final TransportManager<T> tm;

    public Builder(TransportManager<T> tm) {
      this.tm = tm;
    }

    /**
     * Adds a subchannel to the list, or {@code null} for inserting a dropping token.  A dropping
     * token is a pseudo subchannel that will always fail the request, used as a throttling
     * mechanism.
     *
     * @param addresses the addresses to add
     */
    public Builder<T> add(@Nullable Subchannel<T> addresses) {
      list.add(addresses);
      return this;
    }

    public RoundRobinSubchannelList<T> build() {
      return new RoundRobinSubchannelList<T>(tm,
          Collections.unmodifiableList(new ArrayList<Subchannel<T>>(list)));
    }
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
