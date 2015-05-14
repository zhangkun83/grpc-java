package io.grpc.stub;

import com.google.common.collect.ImmutableList;

import io.grpc.Channel;
import io.grpc.MethodDescriptor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

/** Unit tests for {@link AbstractStub}. */
@RunWith(JUnit4.class)
public class AbstractStubTest {
  @Mock private Channel channel;
  private Stub stub;
  private final ServiceDescriptor serviceDescriptor = new ServiceDescriptor();

  private static class ServiceDescriptor extends AbstractServiceDescriptor<ServiceDescriptor> {
    @Override
    protected ServiceDescriptor build(Map<String, MethodDescriptor<?, ?>> map) {
      return new ServiceDescriptor();
    }
    @Override
    public ImmutableList<MethodDescriptor<?, ?>> methods() {
      return null;
    }
  }

  private static class Stub extends AbstractStub<Stub, ServiceDescriptor> {
    private Stub(Channel channel, ServiceDescriptor config) {
      super(channel, config);
    }

    @Override
    protected Stub build(Channel channel, ServiceDescriptor config) {
      return new Stub(channel, config);
    }

    public void unaryCall() {
    }
  }

  @Before public void setUp() {
    MockitoAnnotations.initMocks(this);
    stub = new Stub(channel, serviceDescriptor);
  }

  @Test public void foo() {
    Stub newStub = stub.reconfigure().deadline(1000).done();
    org.junit.Assert.fail();
  }
}
