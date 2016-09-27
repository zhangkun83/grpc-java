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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Supplier;

import io.grpc.Attributes;
import io.grpc.EquivalentAddressGroup;
import io.grpc.ExperimentalApi;
import io.grpc.LoadBalancer;
import io.grpc.NameResolver;
import io.grpc.ResolvedServerInfoGroup;
import io.grpc.Status;
import io.grpc.TransportManager.Subchannel;
import io.grpc.TransportManager;
import io.grpc.TransportManager.InterimTransport;
import io.grpc.internal.RoundRobinSubchannelList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import javax.annotation.concurrent.GuardedBy;


/**
 * A {@link LoadBalancer} that provides round-robin load balancing mechanism over the
 * addresses from the {@link NameResolver}.  The sub-lists received from the name resolver
 * are considered to be an {@link EquivalentAddressGroup} and each of these sub-lists is
 * what is then balanced across.
 */
@ExperimentalApi("https://github.com/grpc/grpc-java/issues/1771")
public final class RoundRobinLoadBalancerFactory extends LoadBalancer.Factory {

  private static final RoundRobinLoadBalancerFactory instance = new RoundRobinLoadBalancerFactory();

  private RoundRobinLoadBalancerFactory() {
  }

  public static RoundRobinLoadBalancerFactory getInstance() {
    return instance;
  }

  @Override
  public <T> LoadBalancer<T> newLoadBalancer(String serviceName, TransportManager<T> tm) {
    return new RoundRobinLoadBalancer<T>(tm);
  }

  private static final class RoundRobinLoadBalancer<T> extends LoadBalancer<T> {
    private static final Status SHUTDOWN_STATUS =
        Status.UNAVAILABLE.augmentDescription("RoundRobinLoadBalancer has shut down");

    private final RoundRobinLoadBalancerLock lock = new RoundRobinLoadBalancerLock();

    @GuardedBy("lock")
    private final HashMap<EquivalentAddressGroup, SubchannelState<T>> subchannels =
        new HashMap<EquivalentAddressGroup, SubchannelState<T>>();

    private volatile RoundRobinSubchannelList<T> roundRobinList;

    @GuardedBy("lock")
    private InterimTransport<T> interimTransport;
    @GuardedBy("lock")
    private Status nameResolutionError;
    @GuardedBy("lock")
    private boolean closed;

    private final TransportManager<T> tm;

    private final NameResolver.Listener nameResolverListener = new NameResolver.Listener() {
      @Override
      public void onUpdate(List<ResolvedServerInfoGroup> updatedServers,
          Attributes attributes) {
        List<EquivalentAddressGroup> updatedAddressGroupList =
            new ArrayList<EquivalentAddressGroup>();
        HashSet<EquivalentAddressGroup> updatedAddressGroupSet =
            new HashSet<EquivalentAddressGroup>();
        HashSet<EquivalentAddressGroup> newAddressGroupSet =
            new HashSet<EquivalentAddressGroup>();
        HashSet<Subchannel<T>> subchannelsToBeRemoved = new HashSet<Subchannel<T>>();
        final RoundRobinSubchannelList<T> savedRoundRobinList;
        InterimTransport<T> savedInterimTransport;

        // Find out the added and removed subchannels
        synchronized (lock) {
          if (closed) {
            return;
          }
          for (ResolvedServerInfoGroup serverInfoGroup : updatedServers) {
            EquivalentAddressGroup addressGroup = serverInfoGroup.toEquivalentAddressGroup();
            if (!subchannels.containsKey(addressGroup)) {
              newAddressGroupSet.add(addressGroup);
            }
            updatedAddressGroupList.add(addressGroup);
            updatedAddressGroupSet.add(addressGroup);
          }
          for (SubchannelState<T> subchannel : subchannels.values()) {
            if (!updatedAddressGroupSet.contains(subchannel.subchannel.getAddresses())) {
              subchannelsToBeRemoved.add(subchannel.subchannel);
            }
          }
        }

        // From the fear for deadlocks, create and close subchannels outside of the lock
        ArrayList<SubchannelState<T>> newSubchannels = new ArrayList<SubchannelState<T>>();
        for (EquivalentAddressGroup addressGroup : newAddressGroupSet) {
          newSubchannels.add(new SubchannelState<T>(tm.createSubchannel(addressGroup)));
        }
        for (Subchannel<T> subchannel : subchannelsToBeRemoved) {
          subchannel.shutdown();
        }

        // Update states
        synchronized (lock) {
          if (closed) {
            // I lost a race to shutdown().  All subchannels will be shutdown by the top-level
            // channel.
            return;
          }
          for (SubchannelState<T> subchannel : newSubchannels) {
            checkState(subchannels.put(subchannel.subchannel.getAddresses(), subchannel) == null,
                "Subchannel for %s already exists", subchannel.subchannel.getAddresses());
          }
          for (Subchannel<T> subchannel : subchannelsToBeRemoved) {
            checkState(subchannels.remove(subchannel.getAddresses()) != null,
                "Subchannel to be removed for %s is missing", subchannel.getAddresses());
          }
          // Build the new round-robin list
          RoundRobinSubchannelList.Builder<T> listBuilder =
              new RoundRobinSubchannelList.Builder<T>(tm);
          for (EquivalentAddressGroup addressGroup : updatedAddressGroupList) {
            SubchannelState<T> subchannel = subchannels.get(addressGroup);
            checkState(subchannel != null, "Subchannel for %s is missing", addressGroup);
            listBuilder.add(subchannel.subchannel);
          }
          roundRobinList = listBuilder.build();
          savedRoundRobinList = roundRobinList;
          nameResolutionError = null;
          savedInterimTransport = interimTransport;
          interimTransport = null;
        }

        if (savedInterimTransport != null) {
          savedInterimTransport.closeWithRealTransports(new Supplier<T>() {
              @Override public T get() {
                return savedRoundRobinList.getNextTransport();
              }
            });
        }
      }

      @Override
      public void onError(Status error) {
        InterimTransport<T> savedInterimTransport;
        synchronized (lock) {
          if (closed) {
            return;
          }
          error = error.augmentDescription("Name resolution failed");
          savedInterimTransport = interimTransport;
          interimTransport = null;
          nameResolutionError = error;
        }
        if (savedInterimTransport != null) {
          savedInterimTransport.closeWithError(error);
        }
      }
    };

    private RoundRobinLoadBalancer(TransportManager<T> tm) {
      this.tm = tm;
    }

    @Override
    public T pickTransport(Attributes affinity) {
      RoundRobinSubchannelList<T> savedRoundRobinList = roundRobinList;
      if (savedRoundRobinList == null) {
        synchronized (lock) {
          if (closed) {
            return tm.createFailingTransport(SHUTDOWN_STATUS);
          }
          if (roundRobinList == null) {
            if (nameResolutionError != null) {
              return tm.createFailingTransport(nameResolutionError);
            }
            if (interimTransport == null) {
              interimTransport = tm.createInterimTransport();
            }
            return interimTransport.transport();
          } else {
            savedRoundRobinList = roundRobinList;
          }
        }
      }
      return savedRoundRobinList.getNextTransport();
    }

    @Override
    public NameResolver.Listener getNameResolverListener() {
      return nameResolverListener;
    }

    @Override
    public void shutdown() {
      InterimTransport<T> savedInterimTransport;
      ArrayList<SubchannelState<T>> savedSubchannels;
      synchronized (lock) {
        if (closed) {
          return;
        }
        closed = true;
        savedInterimTransport = interimTransport;
        savedSubchannels = new ArrayList<SubchannelState<T>>(subchannels.values());
        interimTransport = null;
        roundRobinList = null;
      }
      if (savedInterimTransport != null) {
        savedInterimTransport.closeWithError(SHUTDOWN_STATUS);
      }
      for (SubchannelState<T> subchannel : savedSubchannels) {
        subchannel.subchannel.shutdown();
      }
    }
  }

  private static class SubchannelState<T> {
    final Subchannel<T> subchannel;
    boolean healthy = true;

    SubchannelState(Subchannel<T> subchannel) {
      this.subchannel = subchannel;
    }
  }

  private static class RoundRobinLoadBalancerLock { }

}
