// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: load_balancer.proto

package io.grpc.grpclb;

/**
 * Protobuf type {@code loadbalancer_gslb.client.grpc.Server}
 */
public  final class Server extends
    com.google.protobuf.GeneratedMessage implements
    // @@protoc_insertion_point(message_implements:loadbalancer_gslb.client.grpc.Server)
    ServerOrBuilder {
  // Use Server.newBuilder() to construct.
  private Server(com.google.protobuf.GeneratedMessage.Builder<?> builder) {
    super(builder);
  }
  private Server() {
    ipAddress_ = "";
    port_ = 0;
    loadBalanceToken_ = com.google.protobuf.ByteString.EMPTY;
    dropRequest_ = false;
  }

  @java.lang.Override
  public final com.google.protobuf.UnknownFieldSet
  getUnknownFields() {
    return com.google.protobuf.UnknownFieldSet.getDefaultInstance();
  }
  private Server(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry) {
    this();
    int mutable_bitField0_ = 0;
    try {
      boolean done = false;
      while (!done) {
        int tag = input.readTag();
        switch (tag) {
          case 0:
            done = true;
            break;
          default: {
            if (!input.skipField(tag)) {
              done = true;
            }
            break;
          }
          case 10: {
            String s = input.readStringRequireUtf8();

            ipAddress_ = s;
            break;
          }
          case 16: {

            port_ = input.readInt32();
            break;
          }
          case 26: {

            loadBalanceToken_ = input.readBytes();
            break;
          }
          case 32: {

            dropRequest_ = input.readBool();
            break;
          }
        }
      }
    } catch (com.google.protobuf.InvalidProtocolBufferException e) {
      throw new RuntimeException(e.setUnfinishedMessage(this));
    } catch (java.io.IOException e) {
      throw new RuntimeException(
          new com.google.protobuf.InvalidProtocolBufferException(
              e.getMessage()).setUnfinishedMessage(this));
    } finally {
      makeExtensionsImmutable();
    }
  }
  public static final com.google.protobuf.Descriptors.Descriptor
      getDescriptor() {
    return io.grpc.grpclb.LoadBalancerProto.internal_static_loadbalancer_gslb_client_grpc_Server_descriptor;
  }

  protected com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return io.grpc.grpclb.LoadBalancerProto.internal_static_loadbalancer_gslb_client_grpc_Server_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            io.grpc.grpclb.Server.class, io.grpc.grpclb.Server.Builder.class);
  }

  public static final int IP_ADDRESS_FIELD_NUMBER = 1;
  private volatile java.lang.Object ipAddress_;
  /**
   * <code>optional string ip_address = 1;</code>
   *
   * <pre>
   * A resolved address and port for the server. The IP address string may
   * either be an IPv4 or IPv6 address.
   * </pre>
   */
  public java.lang.String getIpAddress() {
    java.lang.Object ref = ipAddress_;
    if (ref instanceof java.lang.String) {
      return (java.lang.String) ref;
    } else {
      com.google.protobuf.ByteString bs = 
          (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      ipAddress_ = s;
      return s;
    }
  }
  /**
   * <code>optional string ip_address = 1;</code>
   *
   * <pre>
   * A resolved address and port for the server. The IP address string may
   * either be an IPv4 or IPv6 address.
   * </pre>
   */
  public com.google.protobuf.ByteString
      getIpAddressBytes() {
    java.lang.Object ref = ipAddress_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b = 
          com.google.protobuf.ByteString.copyFromUtf8(
              (java.lang.String) ref);
      ipAddress_ = b;
      return b;
    } else {
      return (com.google.protobuf.ByteString) ref;
    }
  }

  public static final int PORT_FIELD_NUMBER = 2;
  private int port_;
  /**
   * <code>optional int32 port = 2;</code>
   */
  public int getPort() {
    return port_;
  }

  public static final int LOAD_BALANCE_TOKEN_FIELD_NUMBER = 3;
  private com.google.protobuf.ByteString loadBalanceToken_;
  /**
   * <code>optional bytes load_balance_token = 3;</code>
   *
   * <pre>
   * An opaque token that is passed from the client to the server in metadata.
   * The server may expect this token to indicate that the request from the
   * client was load balanced.
   * TODO(yetianx): Not used right now, and will be used after implementing
   * load report.
   * </pre>
   */
  public com.google.protobuf.ByteString getLoadBalanceToken() {
    return loadBalanceToken_;
  }

  public static final int DROP_REQUEST_FIELD_NUMBER = 4;
  private boolean dropRequest_;
  /**
   * <code>optional bool drop_request = 4;</code>
   *
   * <pre>
   * Indicates whether this particular request should be dropped by the client
   * when this server is chosen from the list.
   * </pre>
   */
  public boolean getDropRequest() {
    return dropRequest_;
  }

  private byte memoizedIsInitialized = -1;
  public final boolean isInitialized() {
    byte isInitialized = memoizedIsInitialized;
    if (isInitialized == 1) return true;
    if (isInitialized == 0) return false;

    memoizedIsInitialized = 1;
    return true;
  }

  public void writeTo(com.google.protobuf.CodedOutputStream output)
                      throws java.io.IOException {
    if (!getIpAddressBytes().isEmpty()) {
      com.google.protobuf.GeneratedMessage.writeString(output, 1, ipAddress_);
    }
    if (port_ != 0) {
      output.writeInt32(2, port_);
    }
    if (!loadBalanceToken_.isEmpty()) {
      output.writeBytes(3, loadBalanceToken_);
    }
    if (dropRequest_ != false) {
      output.writeBool(4, dropRequest_);
    }
  }

  public int getSerializedSize() {
    int size = memoizedSize;
    if (size != -1) return size;

    size = 0;
    if (!getIpAddressBytes().isEmpty()) {
      size += com.google.protobuf.GeneratedMessage.computeStringSize(1, ipAddress_);
    }
    if (port_ != 0) {
      size += com.google.protobuf.CodedOutputStream
        .computeInt32Size(2, port_);
    }
    if (!loadBalanceToken_.isEmpty()) {
      size += com.google.protobuf.CodedOutputStream
        .computeBytesSize(3, loadBalanceToken_);
    }
    if (dropRequest_ != false) {
      size += com.google.protobuf.CodedOutputStream
        .computeBoolSize(4, dropRequest_);
    }
    memoizedSize = size;
    return size;
  }

  private static final long serialVersionUID = 0L;
  public static io.grpc.grpclb.Server parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static io.grpc.grpclb.Server parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static io.grpc.grpclb.Server parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static io.grpc.grpclb.Server parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static io.grpc.grpclb.Server parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return PARSER.parseFrom(input);
  }
  public static io.grpc.grpclb.Server parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return PARSER.parseFrom(input, extensionRegistry);
  }
  public static io.grpc.grpclb.Server parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return PARSER.parseDelimitedFrom(input);
  }
  public static io.grpc.grpclb.Server parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return PARSER.parseDelimitedFrom(input, extensionRegistry);
  }
  public static io.grpc.grpclb.Server parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return PARSER.parseFrom(input);
  }
  public static io.grpc.grpclb.Server parseFrom(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return PARSER.parseFrom(input, extensionRegistry);
  }

  public Builder newBuilderForType() { return newBuilder(); }
  public static Builder newBuilder() {
    return DEFAULT_INSTANCE.toBuilder();
  }
  public static Builder newBuilder(io.grpc.grpclb.Server prototype) {
    return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
  }
  public Builder toBuilder() {
    return this == DEFAULT_INSTANCE
        ? new Builder() : new Builder().mergeFrom(this);
  }

  @java.lang.Override
  protected Builder newBuilderForType(
      com.google.protobuf.GeneratedMessage.BuilderParent parent) {
    Builder builder = new Builder(parent);
    return builder;
  }
  /**
   * Protobuf type {@code loadbalancer_gslb.client.grpc.Server}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessage.Builder<Builder> implements
      // @@protoc_insertion_point(builder_implements:loadbalancer_gslb.client.grpc.Server)
      io.grpc.grpclb.ServerOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return io.grpc.grpclb.LoadBalancerProto.internal_static_loadbalancer_gslb_client_grpc_Server_descriptor;
    }

    protected com.google.protobuf.GeneratedMessage.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return io.grpc.grpclb.LoadBalancerProto.internal_static_loadbalancer_gslb_client_grpc_Server_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              io.grpc.grpclb.Server.class, io.grpc.grpclb.Server.Builder.class);
    }

    // Construct using io.grpc.grpclb.Server.newBuilder()
    private Builder() {
      maybeForceBuilderInitialization();
    }

    private Builder(
        com.google.protobuf.GeneratedMessage.BuilderParent parent) {
      super(parent);
      maybeForceBuilderInitialization();
    }
    private void maybeForceBuilderInitialization() {
      if (com.google.protobuf.GeneratedMessage.alwaysUseFieldBuilders) {
      }
    }
    public Builder clear() {
      super.clear();
      ipAddress_ = "";

      port_ = 0;

      loadBalanceToken_ = com.google.protobuf.ByteString.EMPTY;

      dropRequest_ = false;

      return this;
    }

    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return io.grpc.grpclb.LoadBalancerProto.internal_static_loadbalancer_gslb_client_grpc_Server_descriptor;
    }

    public io.grpc.grpclb.Server getDefaultInstanceForType() {
      return io.grpc.grpclb.Server.getDefaultInstance();
    }

    public io.grpc.grpclb.Server build() {
      io.grpc.grpclb.Server result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    public io.grpc.grpclb.Server buildPartial() {
      io.grpc.grpclb.Server result = new io.grpc.grpclb.Server(this);
      result.ipAddress_ = ipAddress_;
      result.port_ = port_;
      result.loadBalanceToken_ = loadBalanceToken_;
      result.dropRequest_ = dropRequest_;
      onBuilt();
      return result;
    }

    public Builder mergeFrom(com.google.protobuf.Message other) {
      if (other instanceof io.grpc.grpclb.Server) {
        return mergeFrom((io.grpc.grpclb.Server)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(io.grpc.grpclb.Server other) {
      if (other == io.grpc.grpclb.Server.getDefaultInstance()) return this;
      if (!other.getIpAddress().isEmpty()) {
        ipAddress_ = other.ipAddress_;
        onChanged();
      }
      if (other.getPort() != 0) {
        setPort(other.getPort());
      }
      if (other.getLoadBalanceToken() != com.google.protobuf.ByteString.EMPTY) {
        setLoadBalanceToken(other.getLoadBalanceToken());
      }
      if (other.getDropRequest() != false) {
        setDropRequest(other.getDropRequest());
      }
      onChanged();
      return this;
    }

    public final boolean isInitialized() {
      return true;
    }

    public Builder mergeFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      io.grpc.grpclb.Server parsedMessage = null;
      try {
        parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        parsedMessage = (io.grpc.grpclb.Server) e.getUnfinishedMessage();
        throw e;
      } finally {
        if (parsedMessage != null) {
          mergeFrom(parsedMessage);
        }
      }
      return this;
    }

    private java.lang.Object ipAddress_ = "";
    /**
     * <code>optional string ip_address = 1;</code>
     *
     * <pre>
     * A resolved address and port for the server. The IP address string may
     * either be an IPv4 or IPv6 address.
     * </pre>
     */
    public java.lang.String getIpAddress() {
      java.lang.Object ref = ipAddress_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs =
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        ipAddress_ = s;
        return s;
      } else {
        return (java.lang.String) ref;
      }
    }
    /**
     * <code>optional string ip_address = 1;</code>
     *
     * <pre>
     * A resolved address and port for the server. The IP address string may
     * either be an IPv4 or IPv6 address.
     * </pre>
     */
    public com.google.protobuf.ByteString
        getIpAddressBytes() {
      java.lang.Object ref = ipAddress_;
      if (ref instanceof String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        ipAddress_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }
    /**
     * <code>optional string ip_address = 1;</code>
     *
     * <pre>
     * A resolved address and port for the server. The IP address string may
     * either be an IPv4 or IPv6 address.
     * </pre>
     */
    public Builder setIpAddress(
        java.lang.String value) {
      if (value == null) {
    throw new NullPointerException();
  }
  
      ipAddress_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>optional string ip_address = 1;</code>
     *
     * <pre>
     * A resolved address and port for the server. The IP address string may
     * either be an IPv4 or IPv6 address.
     * </pre>
     */
    public Builder clearIpAddress() {
      
      ipAddress_ = getDefaultInstance().getIpAddress();
      onChanged();
      return this;
    }
    /**
     * <code>optional string ip_address = 1;</code>
     *
     * <pre>
     * A resolved address and port for the server. The IP address string may
     * either be an IPv4 or IPv6 address.
     * </pre>
     */
    public Builder setIpAddressBytes(
        com.google.protobuf.ByteString value) {
      if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);
      
      ipAddress_ = value;
      onChanged();
      return this;
    }

    private int port_ ;
    /**
     * <code>optional int32 port = 2;</code>
     */
    public int getPort() {
      return port_;
    }
    /**
     * <code>optional int32 port = 2;</code>
     */
    public Builder setPort(int value) {
      
      port_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>optional int32 port = 2;</code>
     */
    public Builder clearPort() {
      
      port_ = 0;
      onChanged();
      return this;
    }

    private com.google.protobuf.ByteString loadBalanceToken_ = com.google.protobuf.ByteString.EMPTY;
    /**
     * <code>optional bytes load_balance_token = 3;</code>
     *
     * <pre>
     * An opaque token that is passed from the client to the server in metadata.
     * The server may expect this token to indicate that the request from the
     * client was load balanced.
     * TODO(yetianx): Not used right now, and will be used after implementing
     * load report.
     * </pre>
     */
    public com.google.protobuf.ByteString getLoadBalanceToken() {
      return loadBalanceToken_;
    }
    /**
     * <code>optional bytes load_balance_token = 3;</code>
     *
     * <pre>
     * An opaque token that is passed from the client to the server in metadata.
     * The server may expect this token to indicate that the request from the
     * client was load balanced.
     * TODO(yetianx): Not used right now, and will be used after implementing
     * load report.
     * </pre>
     */
    public Builder setLoadBalanceToken(com.google.protobuf.ByteString value) {
      if (value == null) {
    throw new NullPointerException();
  }
  
      loadBalanceToken_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>optional bytes load_balance_token = 3;</code>
     *
     * <pre>
     * An opaque token that is passed from the client to the server in metadata.
     * The server may expect this token to indicate that the request from the
     * client was load balanced.
     * TODO(yetianx): Not used right now, and will be used after implementing
     * load report.
     * </pre>
     */
    public Builder clearLoadBalanceToken() {
      
      loadBalanceToken_ = getDefaultInstance().getLoadBalanceToken();
      onChanged();
      return this;
    }

    private boolean dropRequest_ ;
    /**
     * <code>optional bool drop_request = 4;</code>
     *
     * <pre>
     * Indicates whether this particular request should be dropped by the client
     * when this server is chosen from the list.
     * </pre>
     */
    public boolean getDropRequest() {
      return dropRequest_;
    }
    /**
     * <code>optional bool drop_request = 4;</code>
     *
     * <pre>
     * Indicates whether this particular request should be dropped by the client
     * when this server is chosen from the list.
     * </pre>
     */
    public Builder setDropRequest(boolean value) {
      
      dropRequest_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>optional bool drop_request = 4;</code>
     *
     * <pre>
     * Indicates whether this particular request should be dropped by the client
     * when this server is chosen from the list.
     * </pre>
     */
    public Builder clearDropRequest() {
      
      dropRequest_ = false;
      onChanged();
      return this;
    }
    public final Builder setUnknownFields(
        final com.google.protobuf.UnknownFieldSet unknownFields) {
      return this;
    }

    public final Builder mergeUnknownFields(
        final com.google.protobuf.UnknownFieldSet unknownFields) {
      return this;
    }


    // @@protoc_insertion_point(builder_scope:loadbalancer_gslb.client.grpc.Server)
  }

  // @@protoc_insertion_point(class_scope:loadbalancer_gslb.client.grpc.Server)
  private static final io.grpc.grpclb.Server DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new io.grpc.grpclb.Server();
  }

  public static io.grpc.grpclb.Server getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private static final com.google.protobuf.Parser<Server>
      PARSER = new com.google.protobuf.AbstractParser<Server>() {
    public Server parsePartialFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      try {
        return new Server(input, extensionRegistry);
      } catch (RuntimeException e) {
        if (e.getCause() instanceof
            com.google.protobuf.InvalidProtocolBufferException) {
          throw (com.google.protobuf.InvalidProtocolBufferException)
              e.getCause();
        }
        throw e;
      }
    }
  };

  public static com.google.protobuf.Parser<Server> parser() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.protobuf.Parser<Server> getParserForType() {
    return PARSER;
  }

  public io.grpc.grpclb.Server getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }

}

