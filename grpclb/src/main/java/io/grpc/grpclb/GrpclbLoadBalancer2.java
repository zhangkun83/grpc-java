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

package io.grpc.grpclb;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

import io.grpc.Attributes;
import io.grpc.Channel;
import io.grpc.EquivalentAddressGroup;
import io.grpc.LoadBalancer;
import io.grpc.ResolvedServerInfo;
import io.grpc.ResolvedServerInfoGroup;
import io.grpc.Status;
import io.grpc.TransportManager;
import io.grpc.TransportManager.InterimTransport;
import io.grpc.internal.GrpcUtil;
import io.grpc.internal.RoundRobinServerList;
import io.grpc.internal.SharedResourceHolder;
import io.grpc.stub.StreamObserver;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

import javax.annotation.concurrent.GuardedBy;

/**
 * A {@link LoadBalancer} that uses the GRPCLB protocol.
 */
class GrpclbLoadBalancer2<T> extends LoadBalancer2<T> implements WithLogId {
  private static final Logger logger = Logger.getLogger(GrpclbLoadBalancer2.class.getName());
  private final LogId logId = LogId.allocate(getClass().getName());

  private final ObjectPool<? extends Executor> executorPool;
  private final String serviceName;
  private final Helper helper;

  private static final Attributes.Key<AtomicReference<ConnectivityStateInfo>> STATE_INFO =
        Attributes.Key.of("io.grpc.grpclb.GrpclbLoadBalancer.stateInfo");
  private static final PickResult THROTTLED_RESULT =
      PickResult.withError(Status.UNAVAILABLE.withDescription("Throttled by LB"));

  // All states in this class are mutated ONLY from Channel Executor

  private Executor lbCommExecutor;
  private Status lastError;
  private LoadBalancer2 delegate;
  private LbPolicy lbPolicy;

  ///////////////////////////////////////////////////////////////////////////////
  // GRPCLB states, valid only if lbPolicy == GRPCLB
  ///////////////////////////////////////////////////////////////////////////////

  // null if there isn't any available LB addresses.
  // If non-null, never empty.
  private List<LbAddressGroup> lbAddressGroups;
  private ManagedChannel lbCommChannel;
  // Points to the position of the LB address that lbCommChannel is bound to, if
  // lbCommChannel != null.
  private int currentLbIndex;
  private StreamObserver<LoadBalanceResponse> lbResponseObserver;
  private StreamObserver<LoadBalanceRequest> lbRequestWriter;
  private HashMap<EquivalentAddressGroup, Subchannel> subchannels;
  // A null element indicate a simulated error for throttling purpose
  private List<EquivalentAddressGroup> roundRobinList;

  GrpclbLoadBalancer(Helper helper, ObjectPool<? extends Executor> executorPool) {
    this.serviceName = helper.getAuthority();
    this.helper = helper;
    this.executorPool = executorPool;
    this.lbCommExecutor = executorPool.getObject();
  }

  @Override
  public LogId getLogId() {
    return logId;
  }

  @Override
  public void handleSubchannelState(Subchannel subchannel, ConnectivityStateInfo newState) {
    if (newState.getState() == SHUTDOWN || !(subchannels.values().contains(subchannel))) {
      return;
    }
    helper.updatePicker(new RoundRobinPicker(createPickList()));
  }

  @Override
  public void handleResolvedAddresses(List<ResolvedServerInfoGroup> updatedServers,
      Attributes attributes) {
    LbPolicy newLbPolicy = attributes.get(ATTR_LB_POLICY);
    if (newLbPolicy == null) {
      newLbPolicy = LbPolicy.PICK_FIRST;
    }

    // LB addresses and backend addresses are treated separately
    List<LbAddressGroup> newLbAddressGroups = new ArrayList<LbAddressGroup>();
    List<ResolvedServerInfoGroup> newBackendServerInfoGroups =
        new ArrayList<ResolvedServerInfoGroup>();
    List<EquivalentAddressGroup> newBackendAddressGroups =
        new ArrayList<EquivalentAddressGroup>();
    for (ResolvedServerInfoGroup serverInfoGroup : updatedServers) {
      String lbAddrAuthority = serverInfoGroup.getAttributes().get(ATTR_ADDR_AUTHORITY);
      EquivalentAddressGroup eag = serverInfoGroup.toEquivalentAddressGroup();
      if (lbAddrName != null) {
        newLbAddressGroups.add(new LbAddressGroup(eag, lbAddrAuthority));
      } else {
        newBackendAddressGroups.add(eag);
        newBackendServerInfoGroups.add(serverInfoGroup);
      }
    }

    if (newBackendAddressGroups.isEmpty()) {
      if (newLbPolicy != LbPolicy.GRPCLB) {
        newLbPolicy = LbPolicy.GRPCLB;
        logger.log(Level.FINE, "[{0}] Switching to GRPCLB because no backend addresses provided",
            logId);
      }
    }
    if (newLbPolicy == null) {
      logger.log(Level.FINE, "[{0}] New config missing policy. Using PICK_FIRST", logId);
      newLbPolicy = LbPolicy.PICK_FIRST;
    }

    // Switch LB policy if requested
    if (newLbPolicy != lbPolicy) {
      shutdownDelegate();
      shutdownLbComm();
      lbAddressGroups = null;
      currentLbIndex = 0;
      switch (newLbPolicy) {
        case PICK_FIRST:
          // TODO(zhangkun83): create a PickFirstBalancer and assign to delegate
          throw new UnsupportedOperationException();
          break;
        case ROUND_ROBIN:
          // TODO(zhangkun83): create a RoundRobinBalancer and assign to delegate
          throw new UnsupportedOperationException();
          break;
      }
    }
    lbPolicy = newLbPolicy;

    // Consume the new addresses
    switch (lbPolicy) {
      case PICK_FIRST:
      case ROUND_ROBIN:
        checkNotNull(delegate, "delegate should not be null. newLbPolicy=" + newLbPolicy);
        delegate.handleResolvedAddresses(newBackendServerInfoGroup, Attributes.EMPTY);
        break;
      case GRPCLB:
        if (newLbAddressGroups.isEmpty()) {
          shutdownLbComm();
          lbAddressGroups = null;
          handleError(Status.UNAVAILABLE.withDescription(
                  "NameResolver returned no LB address while asking for GRPCLB"));
        } else {
          // See if the currently used LB server is in the new list.
          int newIndexOfCurrentLb = -1;
          if (lbAddressGroups != null) {
            LbAddressGroup currentLb = lbAddressGroups.get(currentLbIndex);          
            newIndexOfCurrentLb = newLbAddressGroups.indexOf(currentLb);
          }
          lbAddressGroups = newLbAddressGroups;
          if (newIndexOfCurrentLb == -1) {
            // Current LB is delisted
            shutdownLbComm();
            currentLbIndex = 0;
            startLbComm();
          } else {
            currentLbIndex = newIndexOfCurrentLb;
          }
        }
        break;
      defaut:
        handleError(new UnsupportedOperationException("Not implemented: " + newLbPolicy));
        return;
    }
  }

  private void shutdownLbComm() {
    if (lbCommChannel != null) {
      lbCommChannel.shutdown();
      lbCommChannel = null;
    }
    if (lbRequestWriter != null) {
      lbRequestWriter.onCompleted();
      lbRequestWriter = null;
    }
    if (lbResponseObserver != null) {
      lbResponseObserver.dismissed = true;
      lbResponseObserver = null;
    }
  }

  private void startLbComm() {
    checkState(lbCommChannel == null, "previous lbCommChannel has not been closed yet");
    checkState(lbRequestWriter == null, "previous lbRequestWriter has not been cleared yet");
    checkState(lbResponseObserver == null, "previous lbResponseObserver has not been cleared yet");
    LbAddressGroup currentLb = lbAddressGroups.get(currentLbIndex); 
    lbCommChannel = helper.createOobChannel(currentLb.getAddresses(), currentLb.getAuthority(),
        oobExecutor);
    LoadBalancerGrpc.LoadBalancerStub stub = LoadBalancerGrpc.newStub(channel);
    lbResponseObserver = new LbResponseObserver();
    lbRequestWriter = stub.balanceLoad(lbResponseObserver);

    LoadBalanceRequest initRequest = LoadBalanceRequest.newBuilder()
        .setInitialRequest(InitialLoadBalanceRequest.newBuilder()
            .setName(helper.getAuthority()).build())
        .build();
    lbRequestWriter.onNext(initRequest);
  }

  private void shutdownDelegate() {
    if (delegate != null) {
      delegate.shutdown();
      delegate = null;
    }
  }

  @Override
  public void shutdown() {
    shutdownDelegate();
    shutdownLbComm();
    if (lbCommExecutor != null) {
      lbCommExecutor = executorPool.returnObject(lbCommExecutor);
    }
  }

  private void handleError(Status status) {
    lastError = status;
    // Fail the fail-fast RPCs first
    helper.updatePicker(new ErrorPicker(status));
    if (roundRobinList != null) {
      helper.updatePicker(new RoundRobinPicker(createPickList()));
    }
    refreshPicker(status);
  }

  private List<PickResult> createPickList() {
    List<PickResult> resultList = new ArrayList<PickResult>();
    for (EquivalentAddressGroup eag : roundRobinList) {
      if (eag == null) {
        resultList.add(THROTTLED_RESULT);
      } else {
        Subchannel subchannel = subchannels.get(eag);
        checkNotNull(subchannel, "Subchannel for %s not found", eag);
        Attributes attrs = subchannel.getAttributes();
        if (attrs.get(STATE_INFO).get() == READY) {
          resultList.add(PickResult.withSubchannel(subchannel));
        }
      }
    }
    return resultList;
    // TODO(zhangkun83): should we reduce the number of throttled result in proportion to the number
    // of non-READY subchannels?  In the extreme case, do we still keep the throttled results if
    // there is no READY subchannel?
  }

  @Override
  public void handleNameResolutionError(Status error) {
    handleError(error.augmentDescription("Name resolution failed"));
  }

  private class LbResponseObserver implements StreamObserver<LoadBalanceResponse> {
    boolean dismissed;

    @Override public void onNext(final LoadBalanceResponse response) {
      helper.runSerialized(new Runnable() {
          @Override
          public void run() {
            handleResponse(response);
          }
        });
    }

    private void handleResponse(LoadBalanceResponse response) {
      if (dismissed) {
        return;
      }
      logger.log(Level.FINE, "[{0}] Got an LB response: {1}", new Object[] {logId, response});
      // TODO(zhangkun83): make use of initialResponse
      // InitialLoadBalanceResponse initialResponse = response.getInitialResponse();
      ServerList serverList = response.getServerList();
      HashMap<EquivalentAddressGroup, Subchannel> newSubchannelMap =
          new HashMap<EquivalentAddressGroup, Subchannel>();
      List<EquivalentAddressGroup> newRoundRobinList = new ArrayList<EquivalentAddressGroup>();
      // TODO(zhangkun83): honor expiration_interval
      // Construct the new collections. Create new Subchannels when necessary.
      for (Server server : serverList.getServersList()) {
        if (server.getDropRequest()) {
          newRoundRobinList.add(null);
        } else {
          InetSocketAddress address;
          try {
            address = new InetSocketAddress(
                InetAddress.getByAddress(server.getIpAddress().toByteArray()), server.getPort());
          } catch (UnknownHostException e) {
            handleError(e);
            continue;
          }
          EquivalentAddressGroup eag = new EquivalentAddressGroup(address);
          // TODO(zhangkun83): save the LB token and insert it to the application RPCs' headers.
          if (!newSubchannelMap.containsKey(eag)) {
            Attributes subchannelAttrs = Attributes.newBuilder()
                .set(STATE_INFO, new AtomicReference<ConnectivityStateInfo>(
                        ConnectivityStateInfo.forNonError(IDLE)))
                .build();
            newSubchannelMap.put(eag, helper.createSubchannel(eag, subchannelAttrs));
          }
          newRoundRobinList.add(eag);
        }
      }
      if (newRoundRobinList.isEmpty()) {
        // initialResponse and serverList are under a oneof group. If initialResponse is set,
        // serverList will be empty.
        return;
      }
      // Close Subchannels whose addresses have been delisted
      for (Entry<EquivalentAddressGroup, Subchannel> entry : subchannels) {
        EquivalentAddressGroup eag = entry.getKey();
        if (!newSubchannelMap.containsKey(eag)) {
          entry.getValue().shutdown();
        }
      }

      subchannels = newSubchannelMap;
      roundRobinList = newRoundRobinList;
      refreshPicker();
    }

    @Override public void onError(final Throwable error) {
      helper.runSerialized(new Runnable() {
          @Override
          public void run() {
            onStreamClosed(Status.fromThrowable(error)
                .augmentDescription("Stream to GRPCLB LoadBalancer had an error"));
          }
        });
    }

    @Override public void onCompleted() {
      helper.runSerialized(new Runnable() {
          @Override
          public void run() {
            onStreamClosed(Status.UNAVAILABLE.augmentDescription(
                    "Stream to GRPCLB LoadBalancer was closed"));
          }
        });
    }

    private void handleStreamClosed(Status status) {
      if (dismissed) {
        return;
      }
      handleError(status);
      shutdownLbComm();
      currentLbIndex = (currentLbIndex + 1) % lbAddressGroups.size();
      startLbComm();
    }
  }

  private static class ErrorPicker extends SubchannelPicker {
    final PickResult result;

    ErrorPicker(Status status) {
      result = PickResult.withError(status);
    }

    @Override
    public PickResult pickSubchannel(Attributes affinity, Metadata headers) {
      return result;
    }
  }

  private static class RoundRobinPicker extends SubchannelPicker {
    final Iterator<PickResult> resultIterator

    RoundRobinPicker(List<PickResult> resultList) {
      resultIterator = Iterables.cycle(resultList).iterator();
    }

    @Override
    public PickResult pickSubchannel(Attributes affinity, Metadata headers) {
      synchronized (resultIterator) {
        return resultIterator.next();
      }
    }
  }
}
