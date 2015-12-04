// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: massage.proto

package io.grpc.examples.massage;

/**
 * Protobuf type {@code loadbalancer_gslb.client.grpc.MassageReply}
 */
public  final class MassageReply extends
    com.google.protobuf.GeneratedMessage implements
    // @@protoc_insertion_point(message_implements:loadbalancer_gslb.client.grpc.MassageReply)
    MassageReplyOrBuilder {
  // Use MassageReply.newBuilder() to construct.
  private MassageReply(com.google.protobuf.GeneratedMessage.Builder<?> builder) {
    super(builder);
  }
  private MassageReply() {
    reply_ = "";
  }

  @java.lang.Override
  public final com.google.protobuf.UnknownFieldSet
  getUnknownFields() {
    return com.google.protobuf.UnknownFieldSet.getDefaultInstance();
  }
  private MassageReply(
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

            reply_ = s;
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
    return io.grpc.examples.massage.MassageProto.internal_static_loadbalancer_gslb_client_grpc_MassageReply_descriptor;
  }

  protected com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return io.grpc.examples.massage.MassageProto.internal_static_loadbalancer_gslb_client_grpc_MassageReply_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            io.grpc.examples.massage.MassageReply.class, io.grpc.examples.massage.MassageReply.Builder.class);
  }

  public static final int REPLY_FIELD_NUMBER = 1;
  private volatile java.lang.Object reply_;
  /**
   * <code>optional string reply = 1;</code>
   */
  public java.lang.String getReply() {
    java.lang.Object ref = reply_;
    if (ref instanceof java.lang.String) {
      return (java.lang.String) ref;
    } else {
      com.google.protobuf.ByteString bs = 
          (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      reply_ = s;
      return s;
    }
  }
  /**
   * <code>optional string reply = 1;</code>
   */
  public com.google.protobuf.ByteString
      getReplyBytes() {
    java.lang.Object ref = reply_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b = 
          com.google.protobuf.ByteString.copyFromUtf8(
              (java.lang.String) ref);
      reply_ = b;
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
    if (!getReplyBytes().isEmpty()) {
      com.google.protobuf.GeneratedMessage.writeString(output, 1, reply_);
    }
  }

  public int getSerializedSize() {
    int size = memoizedSize;
    if (size != -1) return size;

    size = 0;
    if (!getReplyBytes().isEmpty()) {
      size += com.google.protobuf.GeneratedMessage.computeStringSize(1, reply_);
    }
    memoizedSize = size;
    return size;
  }

  private static final long serialVersionUID = 0L;
  public static io.grpc.examples.massage.MassageReply parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static io.grpc.examples.massage.MassageReply parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static io.grpc.examples.massage.MassageReply parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static io.grpc.examples.massage.MassageReply parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static io.grpc.examples.massage.MassageReply parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return PARSER.parseFrom(input);
  }
  public static io.grpc.examples.massage.MassageReply parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return PARSER.parseFrom(input, extensionRegistry);
  }
  public static io.grpc.examples.massage.MassageReply parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return PARSER.parseDelimitedFrom(input);
  }
  public static io.grpc.examples.massage.MassageReply parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return PARSER.parseDelimitedFrom(input, extensionRegistry);
  }
  public static io.grpc.examples.massage.MassageReply parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return PARSER.parseFrom(input);
  }
  public static io.grpc.examples.massage.MassageReply parseFrom(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return PARSER.parseFrom(input, extensionRegistry);
  }

  public Builder newBuilderForType() { return newBuilder(); }
  public static Builder newBuilder() {
    return DEFAULT_INSTANCE.toBuilder();
  }
  public static Builder newBuilder(io.grpc.examples.massage.MassageReply prototype) {
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
   * Protobuf type {@code loadbalancer_gslb.client.grpc.MassageReply}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessage.Builder<Builder> implements
      // @@protoc_insertion_point(builder_implements:loadbalancer_gslb.client.grpc.MassageReply)
      io.grpc.examples.massage.MassageReplyOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return io.grpc.examples.massage.MassageProto.internal_static_loadbalancer_gslb_client_grpc_MassageReply_descriptor;
    }

    protected com.google.protobuf.GeneratedMessage.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return io.grpc.examples.massage.MassageProto.internal_static_loadbalancer_gslb_client_grpc_MassageReply_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              io.grpc.examples.massage.MassageReply.class, io.grpc.examples.massage.MassageReply.Builder.class);
    }

    // Construct using io.grpc.examples.massage.MassageReply.newBuilder()
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
      reply_ = "";

      return this;
    }

    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return io.grpc.examples.massage.MassageProto.internal_static_loadbalancer_gslb_client_grpc_MassageReply_descriptor;
    }

    public io.grpc.examples.massage.MassageReply getDefaultInstanceForType() {
      return io.grpc.examples.massage.MassageReply.getDefaultInstance();
    }

    public io.grpc.examples.massage.MassageReply build() {
      io.grpc.examples.massage.MassageReply result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    public io.grpc.examples.massage.MassageReply buildPartial() {
      io.grpc.examples.massage.MassageReply result = new io.grpc.examples.massage.MassageReply(this);
      result.reply_ = reply_;
      onBuilt();
      return result;
    }

    public Builder mergeFrom(com.google.protobuf.Message other) {
      if (other instanceof io.grpc.examples.massage.MassageReply) {
        return mergeFrom((io.grpc.examples.massage.MassageReply)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(io.grpc.examples.massage.MassageReply other) {
      if (other == io.grpc.examples.massage.MassageReply.getDefaultInstance()) return this;
      if (!other.getReply().isEmpty()) {
        reply_ = other.reply_;
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
      io.grpc.examples.massage.MassageReply parsedMessage = null;
      try {
        parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        parsedMessage = (io.grpc.examples.massage.MassageReply) e.getUnfinishedMessage();
        throw e;
      } finally {
        if (parsedMessage != null) {
          mergeFrom(parsedMessage);
        }
      }
      return this;
    }

    private java.lang.Object reply_ = "";
    /**
     * <code>optional string reply = 1;</code>
     */
    public java.lang.String getReply() {
      java.lang.Object ref = reply_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs =
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        reply_ = s;
        return s;
      } else {
        return (java.lang.String) ref;
      }
    }
    /**
     * <code>optional string reply = 1;</code>
     */
    public com.google.protobuf.ByteString
        getReplyBytes() {
      java.lang.Object ref = reply_;
      if (ref instanceof String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        reply_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }
    /**
     * <code>optional string reply = 1;</code>
     */
    public Builder setReply(
        java.lang.String value) {
      if (value == null) {
    throw new NullPointerException();
  }
  
      reply_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>optional string reply = 1;</code>
     */
    public Builder clearReply() {
      
      reply_ = getDefaultInstance().getReply();
      onChanged();
      return this;
    }
    /**
     * <code>optional string reply = 1;</code>
     */
    public Builder setReplyBytes(
        com.google.protobuf.ByteString value) {
      if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);
      
      reply_ = value;
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


    // @@protoc_insertion_point(builder_scope:loadbalancer_gslb.client.grpc.MassageReply)
  }

  // @@protoc_insertion_point(class_scope:loadbalancer_gslb.client.grpc.MassageReply)
  private static final io.grpc.examples.massage.MassageReply DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new io.grpc.examples.massage.MassageReply();
  }

  public static io.grpc.examples.massage.MassageReply getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private static final com.google.protobuf.Parser<MassageReply>
      PARSER = new com.google.protobuf.AbstractParser<MassageReply>() {
    public MassageReply parsePartialFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      try {
        return new MassageReply(input, extensionRegistry);
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

  public static com.google.protobuf.Parser<MassageReply> parser() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.protobuf.Parser<MassageReply> getParserForType() {
    return PARSER;
  }

  public io.grpc.examples.massage.MassageReply getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }

}

