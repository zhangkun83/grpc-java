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

package io.grpc.grpclb;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;

import io.grpc.EquivalentAddressGroup;
import io.grpc.TransportManager;
import io.grpc.internal.ClientTransport;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Manages a list of server addresses to round-robin on.
 */
@ThreadSafe
class RoundRobinServerList {
  private final TransportManager tm;
  private final List<EquivalentAddressGroup> list;
  @GuardedBy("list")
  private int index;

  private RoundRobinServerList(TransportManager tm, List<EquivalentAddressGroup> list) {
    this.tm = tm;
    this.list = list;
  }

  ListenableFuture<ClientTransport> getTransportForNextServer() {
    EquivalentAddressGroup currentServer;
    synchronized (list) {
      currentServer = list.get(index);
      index++;
      if (index >= list.size()) {
        index = 0;
      }
    }
    if (currentServer == null) {
      // TODO(zhangkun83): drop the request by returnning a fake transport that would fail
      // streams.
      throw new UnsupportedOperationException("server dropping not implemented yet");
    }
    return Preconditions.checkNotNull(tm.getTransport(currentServer),
        "TransportManager returned null for %s", currentServer);
  }

  int size() {
    return list.size();
  }

  @NotThreadSafe
  static class Builder {
    private final List<EquivalentAddressGroup> list = new ArrayList<EquivalentAddressGroup>();
    private final TransportManager tm;
    private boolean built;

    Builder(TransportManager tm) {
      this.tm = tm;
    }

    /**
     * Adds a server to the list, or {@code null} for a drop entry.
     */
    void add(@Nullable InetSocketAddress addr) {
      list.add(new EquivalentAddressGroup(addr));
    }

    RoundRobinServerList build() {
      Preconditions.checkState(!built, "already built");
      built = true;
      return new RoundRobinServerList(tm, list);
    }
  }
}
