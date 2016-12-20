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

import static io.grpc.ConnectivityState.SHUTDOWN;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;

import java.net.SocketAddress;
import java.util.List;

/**
 * A {@link LoadBalancer} that provides no load balancing mechanism over the
 * addresses from the {@link NameResolver}.  The channel's default behavior
 * (currently pick-first) is used for all addresses found.
 */
@ExperimentalApi("https://github.com/grpc/grpc-java/issues/1771")
public final class PickFirstBalancerFactory2 extends LoadBalancer2.Factory {

  private static final PickFirstBalancerFactory2 INSTANCE = new PickFirstBalancerFactory2();

  private PickFirstBalancerFactory2() {
  }

  public static PickFirstBalancerFactory2 getInstance() {
    return INSTANCE;
  }

  @Override
  public LoadBalancer2 newLoadBalancer(LoadBalancer2.Helper helper) {
    return new PickFirstBalancer(helper);
  }

  @VisibleForTesting
  static class PickFirstBalancer extends LoadBalancer2 {
    private final Helper helper;
    private Subchannel subchannel;

    public PickFirstBalancer(Helper helper) {
      this.helper = helper;
    }

    @Override
    public void handleResolvedAddresses(List<ResolvedServerInfoGroup> servers,
        Attributes attributes) {
      // Flatten servers list received from name resolver into single address group. This means that
      // as far as load balancer is concerned, there's virtually one single server with multiple
      // addresses so the connection will be created only for the first address (pick first).
      EquivalentAddressGroup newEag =
          flattenResolvedServerInfoGroupsIntoEquivalentAddressGroup(servers);
      if (subchannel == null || !newEag.equals(subchannel.getAddresses())) {
        if (subchannel != null) {
          subchannel.shutdown();
        }

        subchannel = helper.createSubchannel(newEag, Attributes.EMPTY);
        helper.updatePicker(new Picker(PickResult.withSubchannel(subchannel)));
      }
    }

    @Override
    public void handleNameResolutionError(Status error) {
      // NB(lukaszx0) Whether we should propagate the error unconditionally is arguable.
      // It's fine for time being, but we should probably shutdown and discard subchannel if there
      // is one. Otherwise we end up keeping a connection that you will never use.
      helper.updatePicker(new Picker(PickResult.withError(error)));
    }

    @Override
    public void handleSubchannelState(Subchannel subchannel, ConnectivityStateInfo stateInfo) {
      ConnectivityState currentState = stateInfo.getState();
      if (subchannel != this.subchannel || currentState == SHUTDOWN) {
        return;
      }

      PickResult pickResult;
      switch (currentState) {
        case CONNECTING:
          pickResult = PickResult.withNoResult();
          break;
        case READY:
        case IDLE:
          pickResult = PickResult.withSubchannel(subchannel);
          break;
        case TRANSIENT_FAILURE:
          pickResult = PickResult.withError(stateInfo.getStatus());
          break;
        default:
          throw new IllegalStateException();
      }

      helper.updatePicker(new Picker(pickResult));
    }

    @Override
    public void shutdown() {
      if (subchannel != null) {
        subchannel.shutdown();
      }
    }

    /**
     * Flattens list of ResolvedServerInfoGroup objects into one EquivalentAddressGroup object.
     */
    private static EquivalentAddressGroup flattenResolvedServerInfoGroupsIntoEquivalentAddressGroup(
        List<ResolvedServerInfoGroup> groupList) {
      List<SocketAddress> addrs = Lists.newArrayList();
      for (ResolvedServerInfoGroup group : groupList) {
        for (ResolvedServerInfo srv : group.getResolvedServerInfoList()) {
          addrs.add(srv.getAddress());
        }
      }
      return new EquivalentAddressGroup(addrs);
    }

  }

  /**
   * No-op picker which doesn't add any custom picking logic. It just passes already known result
   * received in constructor.
   */
  @VisibleForTesting
  static class Picker extends LoadBalancer2.SubchannelPicker {
    private final LoadBalancer2.PickResult result;

    Picker(LoadBalancer2.PickResult result) {
      this.result = result;
    }

    @Override
    public LoadBalancer2.PickResult pickSubchannel(Attributes affinity, Metadata headers) {
      return result;
    }
  }
}
