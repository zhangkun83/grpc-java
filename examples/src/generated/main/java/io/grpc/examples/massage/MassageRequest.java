// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: massage.proto

package io.grpc.examples.massage;

/**
 * Protobuf type {@code loadbalancer_gslb.client.grpc.MassageRequest}
 *
 * <pre>
 * Protocol type definitions
 * </pre>
 */
public  final class MassageRequest extends
    com.google.protobuf.GeneratedMessage implements
    // @@protoc_insertion_point(message_implements:loadbalancer_gslb.client.grpc.MassageRequest)
    MassageRequestOrBuilder {
  // Use MassageRequest.newBuilder() to construct.
  private MassageRequest(com.google.protobuf.GeneratedMessage.Builder<?> builder) {
    super(builder);
  }
  private MassageRequest() {
    request_ = "";
  }

  @java.lang.Override
  public final com.google.protobuf.UnknownFieldSet
  getUnknownFields() {
    return com.google.protobuf.UnknownFieldSet.getDefaultInstance();
  }
  private MassageRequest(
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

            request_ = s;
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
    return io.grpc.examples.massage.MasssageProto.internal_static_loadbalancer_gslb_client_grpc_MassageRequest_descriptor;
  }

  protected com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return io.grpc.examples.massage.MasssageProto.internal_static_loadbalancer_gslb_client_grpc_MassageRequest_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            io.grpc.examples.massage.MassageRequest.class, io.grpc.examples.massage.MassageRequest.Builder.class);
  }

  public static final int REQUEST_FIELD_NUMBER = 1;
  private volatile java.lang.Object request_;
  /**
   * <code>optional string request = 1;</code>
   */
  public java.lang.String getRequest() {
    java.lang.Object ref = request_;
    if (ref instanceof java.lang.String) {
      return (java.lang.String) ref;
    } else {
      com.google.protobuf.ByteString bs = 
          (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      request_ = s;
      return s;
    }
  }
  /**
   * <code>optional string request = 1;</code>
   */
  public com.google.protobuf.ByteString
      getRequestBytes() {
    java.lang.Object ref = request_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b = 
          com.google.protobuf.ByteString.copyFromUtf8(
              (java.lang.String) ref);
      request_ = b;
      return b;
    } else {
      return (com.google.protobuf.ByteString) ref;
    }
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
    if (!getRequestBytes().isEmpty()) {
      com.google.protobuf.GeneratedMessage.writeString(output, 1, request_);
    }
  }

  public int getSerializedSize() {
    int size = memoizedSize;
    if (size != -1) return size;

    size = 0;
    if (!getRequestBytes().isEmpty()) {
      size += com.google.protobuf.GeneratedMessage.computeStringSize(1, request_);
    }
    memoizedSize = size;
    return size;
  }

  private static final long serialVersionUID = 0L;
  public static io.grpc.examples.massage.MassageRequest parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static io.grpc.examples.massage.MassageRequest parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static io.grpc.examples.massage.MassageRequest parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static io.grpc.examples.massage.MassageRequest parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static io.grpc.examples.massage.MassageRequest parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return PARSER.parseFrom(input);
  }
  public static io.grpc.examples.massage.MassageRequest parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return PARSER.parseFrom(input, extensionRegistry);
  }
  public static io.grpc.examples.massage.MassageRequest parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return PARSER.parseDelimitedFrom(input);
  }
  public static io.grpc.examples.massage.MassageRequest parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return PARSER.parseDelimitedFrom(input, extensionRegistry);
  }
  public static io.grpc.examples.massage.MassageRequest parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return PARSER.parseFrom(input);
  }
  public static io.grpc.examples.massage.MassageRequest parseFrom(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return PARSER.parseFrom(input, extensionRegistry);
  }

  public Builder newBuilderForType() { return newBuilder(); }
  public static Builder newBuilder() {
    return DEFAULT_INSTANCE.toBuilder();
  }
  public static Builder newBuilder(io.grpc.examples.massage.MassageRequest prototype) {
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
   * Protobuf type {@code loadbalancer_gslb.client.grpc.MassageRequest}
   *
   * <pre>
   * Protocol type definitions
   * </pre>
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessage.Builder<Builder> implements
      // @@protoc_insertion_point(builder_implements:loadbalancer_gslb.client.grpc.MassageRequest)
      io.grpc.examples.massage.MassageRequestOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return io.grpc.examples.massage.MasssageProto.internal_static_loadbalancer_gslb_client_grpc_MassageRequest_descriptor;
    }

    protected com.google.protobuf.GeneratedMessage.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return io.grpc.examples.massage.MasssageProto.internal_static_loadbalancer_gslb_client_grpc_MassageRequest_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              io.grpc.examples.massage.MassageRequest.class, io.grpc.examples.massage.MassageRequest.Builder.class);
    }

    // Construct using io.grpc.examples.massage.MassageRequest.newBuilder()
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
      request_ = "";

      return this;
    }

    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return io.grpc.examples.massage.MasssageProto.internal_static_loadbalancer_gslb_client_grpc_MassageRequest_descriptor;
    }

    public io.grpc.examples.massage.MassageRequest getDefaultInstanceForType() {
      return io.grpc.examples.massage.MassageRequest.getDefaultInstance();
    }

    public io.grpc.examples.massage.MassageRequest build() {
      io.grpc.examples.massage.MassageRequest result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    public io.grpc.examples.massage.MassageRequest buildPartial() {
      io.grpc.examples.massage.MassageRequest result = new io.grpc.examples.massage.MassageRequest(this);
      result.request_ = request_;
      onBuilt();
      return result;
    }

    public Builder mergeFrom(com.google.protobuf.Message other) {
      if (other instanceof io.grpc.examples.massage.MassageRequest) {
        return mergeFrom((io.grpc.examples.massage.MassageRequest)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(io.grpc.examples.massage.MassageRequest other) {
      if (other == io.grpc.examples.massage.MassageRequest.getDefaultInstance()) return this;
      if (!other.getRequest().isEmpty()) {
        request_ = other.request_;
        onChanged();
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
      io.grpc.examples.massage.MassageRequest parsedMessage = null;
      try {
        parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        parsedMessage = (io.grpc.examples.massage.MassageRequest) e.getUnfinishedMessage();
        throw e;
      } finally {
        if (parsedMessage != null) {
          mergeFrom(parsedMessage);
        }
      }
      return this;
    }

    private java.lang.Object request_ = "";
    /**
     * <code>optional string request = 1;</code>
     */
    public java.lang.String getRequest() {
      java.lang.Object ref = request_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs =
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        request_ = s;
        return s;
      } else {
        return (java.lang.String) ref;
      }
    }
    /**
     * <code>optional string request = 1;</code>
     */
    public com.google.protobuf.ByteString
        getRequestBytes() {
      java.lang.Object ref = request_;
      if (ref instanceof String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        request_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }
    /**
     * <code>optional string request = 1;</code>
     */
    public Builder setRequest(
        java.lang.String value) {
      if (value == null) {
    throw new NullPointerException();
  }
  
      request_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>optional string request = 1;</code>
     */
    public Builder clearRequest() {
      
      request_ = getDefaultInstance().getRequest();
      onChanged();
      return this;
    }
    /**
     * <code>optional string request = 1;</code>
     */
    public Builder setRequestBytes(
        com.google.protobuf.ByteString value) {
      if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);
      
      request_ = value;
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


    // @@protoc_insertion_point(builder_scope:loadbalancer_gslb.client.grpc.MassageRequest)
  }

  // @@protoc_insertion_point(class_scope:loadbalancer_gslb.client.grpc.MassageRequest)
  private static final io.grpc.examples.massage.MassageRequest DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new io.grpc.examples.massage.MassageRequest();
  }

  public static io.grpc.examples.massage.MassageRequest getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private static final com.google.protobuf.Parser<MassageRequest>
      PARSER = new com.google.protobuf.AbstractParser<MassageRequest>() {
    public MassageRequest parsePartialFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      try {
        return new MassageRequest(input, extensionRegistry);
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

  public static com.google.protobuf.Parser<MassageRequest> parser() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.protobuf.Parser<MassageRequest> getParserForType() {
    return PARSER;
  }

  public io.grpc.examples.massage.MassageRequest getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }

}

