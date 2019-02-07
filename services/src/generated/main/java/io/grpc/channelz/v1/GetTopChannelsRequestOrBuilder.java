// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: grpc/channelz/v1/channelz.proto

package io.grpc.channelz.v1;

public interface GetTopChannelsRequestOrBuilder extends
    // @@protoc_insertion_point(interface_extends:grpc.channelz.v1.GetTopChannelsRequest)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * start_channel_id indicates that only channels at or above this id should be
   * included in the results.
   * To request the first page, this should be set to 0. To request
   * subsequent pages, the client generates this value by adding 1 to
   * the highest seen result ID.
   * </pre>
   *
   * <code>int64 start_channel_id = 1;</code>
   */
  long getStartChannelId();

  /**
   * <pre>
   * If non-zero, the server will return a page of results containing
   * at most this many items. If zero, the server will choose a
   * reasonable page size.  Must never be negative.
   * </pre>
   *
   * <code>int64 max_results = 2;</code>
   */
  long getMaxResults();
}
