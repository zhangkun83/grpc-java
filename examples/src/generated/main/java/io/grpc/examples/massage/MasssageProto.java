// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: massage.proto

package io.grpc.examples.massage;

public final class MasssageProto {
  private MasssageProto() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
  }
  static com.google.protobuf.Descriptors.Descriptor
    internal_static_loadbalancer_gslb_client_grpc_MassageRequest_descriptor;
  static
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_loadbalancer_gslb_client_grpc_MassageRequest_fieldAccessorTable;
  static com.google.protobuf.Descriptors.Descriptor
    internal_static_loadbalancer_gslb_client_grpc_MassageReply_descriptor;
  static
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_loadbalancer_gslb_client_grpc_MassageReply_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n\rmassage.proto\022\035loadbalancer_gslb.clien" +
      "t.grpc\"!\n\016MassageRequest\022\017\n\007request\030\001 \001(" +
      "\t\"\035\n\014MassageReply\022\r\n\005reply\030\001 \001(\t2u\n\007Mass" +
      "age\022j\n\nGetMassage\022-.loadbalancer_gslb.cl" +
      "ient.grpc.MassageRequest\032+.loadbalancer_" +
      "gslb.client.grpc.MassageReply\"\000B+\n\030io.gr" +
      "pc.examples.massageB\rMasssageProtoP\001b\006pr" +
      "oto3"
    };
    com.google.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner assigner =
        new com.google.protobuf.Descriptors.FileDescriptor.    InternalDescriptorAssigner() {
          public com.google.protobuf.ExtensionRegistry assignDescriptors(
              com.google.protobuf.Descriptors.FileDescriptor root) {
            descriptor = root;
            return null;
          }
        };
    com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        }, assigner);
    internal_static_loadbalancer_gslb_client_grpc_MassageRequest_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_loadbalancer_gslb_client_grpc_MassageRequest_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_loadbalancer_gslb_client_grpc_MassageRequest_descriptor,
        new java.lang.String[] { "Request", });
    internal_static_loadbalancer_gslb_client_grpc_MassageReply_descriptor =
      getDescriptor().getMessageTypes().get(1);
    internal_static_loadbalancer_gslb_client_grpc_MassageReply_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessage.FieldAccessorTable(
        internal_static_loadbalancer_gslb_client_grpc_MassageReply_descriptor,
        new java.lang.String[] { "Reply", });
  }

  // @@protoc_insertion_point(outer_class_scope)
}
