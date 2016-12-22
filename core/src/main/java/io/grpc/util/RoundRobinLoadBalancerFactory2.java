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

package io.grpc.util;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.grpc.ConnectivityState.IDLE;
import static io.grpc.ConnectivityState.READY;
import static io.grpc.ConnectivityState.TRANSIENT_FAILURE;

import com.google.common.annotations.VisibleForTesting;

import io.grpc.Attributes;
import io.grpc.ConnectivityStateInfo;
import io.grpc.EquivalentAddressGroup;
import io.grpc.ExperimentalApi;
import io.grpc.LoadBalancer;
import io.grpc.LoadBalancer2;
import io.grpc.LoadBalancer2.PickResult;
import io.grpc.LoadBalancer2.Subchannel;
import io.grpc.LoadBalancer2.SubchannelPicker;
import io.grpc.Metadata;
import io.grpc.NameResolver;
import io.grpc.ResolvedServerInfo;
import io.grpc.ResolvedServerInfoGroup;
import io.grpc.Status;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

/**
 * A {@link LoadBalancer} that provides round-robin load balancing mechanism over the
 * addresses from the {@link NameResolver}.  The sub-lists received from the name resolver
 * are considered to be an {@link EquivalentAddressGroup} and each of these sub-lists is
 * what is then balanced across.
 */
@ExperimentalApi("https://github.com/grpc/grpc-java/issues/1771")
public class RoundRobinLoadBalancerFactory2 extends LoadBalancer2.Factory {
  private static final RoundRobinLoadBalancerFactory2 INSTANCE =
      new RoundRobinLoadBalancerFactory2();

  private RoundRobinLoadBalancerFactory2() {
  }

  public static RoundRobinLoadBalancerFactory2 getInstance() {
    return INSTANCE;
  }

  @Override
  public LoadBalancer2 newLoadBalancer(LoadBalancer2.Helper helper) {
    return new RoundRobinLoadBalancer(helper);
  }

  @VisibleForTesting
  static class RoundRobinLoadBalancer extends LoadBalancer2 {
    private final Helper helper;
    private final Map<EquivalentAddressGroup, Subchannel> subchannels =
        new HashMap<EquivalentAddressGroup, Subchannel>();

    @VisibleForTesting
    static final Attributes.Key<AtomicReference<ConnectivityStateInfo>> STATE_INFO =
        Attributes.Key.of("state-info");

    public RoundRobinLoadBalancer(Helper helper) {
      this.helper = helper;
    }

    @Override
    public void handleResolvedAddresses(List<ResolvedServerInfoGroup> servers,
        Attributes attributes) {
      Set<EquivalentAddressGroup> currentAddrs = subchannels.keySet();
      Set<EquivalentAddressGroup> latestAddrs =
          resolvedServerInfoGroupToEquivalentAddressGroup(servers);
      Set<EquivalentAddressGroup> addedAddrs = setsDifference(latestAddrs, currentAddrs);
      Set<EquivalentAddressGroup> removedAddrs = setsDifference(currentAddrs, latestAddrs);

      // NB(lukaszx0): we don't merge `attributes` with `subchannelAttr` because subchannel doesn't
      // need them. They're describing the resolved server list but we're not taking any action
      // based on this information.
      Attributes subchannelAttrs = Attributes.newBuilder()
          // NB(lukaszx0): because attributes are immutable we can't set new value for the key
          // after creation but since we can mutate the values we leverge that and set
          // AtomicReference which will allow mutating state info for given channel.
          .set(STATE_INFO, new AtomicReference<ConnectivityStateInfo>(
              ConnectivityStateInfo.forNonError(IDLE)))
          .build();

      // Create new subchannels for new addresses.
      for (EquivalentAddressGroup addressGroup : addedAddrs) {
        Subchannel subchannel = checkNotNull(helper.createSubchannel(addressGroup, subchannelAttrs),
            "subchannel");
        subchannels.put(addressGroup, subchannel);
        subchannel.requestConnection();
      }

      // Shutdown subchannels for removed addresses.
      for (EquivalentAddressGroup addressGroup : removedAddrs) {
        Subchannel subchannel = subchannels.remove(addressGroup);
        subchannel.shutdown();
      }

      updatePicker(getAggregatedError());
    }

    @Override
    public void handleNameResolutionError(Status error) {
      updatePicker(error);
    }

    @Override
    public void handleSubchannelState(Subchannel subchannel, ConnectivityStateInfo stateInfo) {
      if (!subchannels.containsValue(subchannel)) {
        return;
      }
      if (stateInfo.getState() == IDLE) {
        subchannel.requestConnection();
      }
      getSubchannelStateInfoRef(subchannel).set(stateInfo);
      updatePicker(getAggregatedError());
    }

    @Override
    public void shutdown() {
      for (Subchannel subchannel : getSubchannels()) {
        subchannel.shutdown();
      }
    }

    /**
     * Updates picker with the list of active subchannels (state == READY).
     */
    private void updatePicker(@Nullable Status error) {
      List<Subchannel> activeList = filterNonFailingSubchannels(getSubchannels());
      helper.updatePicker(new Picker(activeList, error));
    }

    /**
     * Filters out non-ready subchannels.
     */
    private static List<Subchannel> filterNonFailingSubchannels(
        Collection<Subchannel> subchannels) {
      List<Subchannel> readySubchannels = new ArrayList<Subchannel>();
      for (Subchannel subchannel : subchannels) {
        if (getSubchannelStateInfoRef(subchannel).get().getState() == READY) {
          readySubchannels.add(subchannel);
        }
      }
      return readySubchannels;
    }

    /**
     * Converts list of {@link ResolvedServerInfoGroup} to {@link EquivalentAddressGroup} set.
     */
    private static Set<EquivalentAddressGroup> resolvedServerInfoGroupToEquivalentAddressGroup(
        List<ResolvedServerInfoGroup> groupList) {
      Set<EquivalentAddressGroup> addrs = new HashSet<EquivalentAddressGroup>();
      for (ResolvedServerInfoGroup group : groupList) {
        for (ResolvedServerInfo server : group.getResolvedServerInfoList()) {
          addrs.add(new EquivalentAddressGroup(server.getAddress()));
        }
      }
      return addrs;
    }

    /**
     * If all subchannels are TRANSIENT_FAILURE, return the Status associated with an arbitrary
     * subchannel otherwise, return null.
     */
    @Nullable
    private Status getAggregatedError() {
      Status status = null;
      for (Subchannel subchannel : getSubchannels()) {
        ConnectivityStateInfo stateInfo = getSubchannelStateInfoRef(subchannel).get();
        if (stateInfo.getState() != TRANSIENT_FAILURE) {
          return null;
        } else {
          status = stateInfo.getStatus();
        }
      }
      return status;
    }

    @VisibleForTesting
    Collection<Subchannel> getSubchannels() {
      return subchannels.values();
    }

    private static AtomicReference<ConnectivityStateInfo> getSubchannelStateInfoRef(
        Subchannel subchannel) {
      return checkNotNull(subchannel.getAttributes().get(STATE_INFO), "STATE_INFO");
    }

    private static <T> Set<T> setsDifference(Set<T> a, Set<T> b) {
      Set<T> aCopy = new HashSet<T>(a);
      aCopy.removeAll(b);
      return aCopy;
    }
  }

  @VisibleForTesting
  static class Picker extends SubchannelPicker {
    @Nullable
    final Status status;
    private final List<Subchannel> list;
    private int index;
    private final boolean empty;

    Picker(List<Subchannel> list, @Nullable Status status) {
      this.empty = list.isEmpty();
      this.list = list;
      this.status = status;
    }

    @Override
    public PickResult pickSubchannel(Attributes affinity, Metadata headers) {
      if (!empty) {
        return PickResult.withSubchannel(nextSubchannel());
      } else {
        if (status != null) {
          return PickResult.withError(status);
        }
        return PickResult.withNoResult();
      }
    }

    private Subchannel nextSubchannel() {
      if (empty) {
        throw new NoSuchElementException();
      }
      synchronized (this) {
        Subchannel val = list.get(index);
        index++;
        if (index >= list.size()) {
          index -= list.size();
        }
        return val;
      }
    }

    @VisibleForTesting
    List<Subchannel> getList() {
      return Collections.unmodifiableList(list);
    }
  }
}
