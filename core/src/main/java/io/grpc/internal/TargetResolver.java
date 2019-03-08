/*
 * Copyright 2019 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.grpc.internal;

/**
 * Uses {@link NameResolver} to resolve a target string, with retries on failures.
 *
 * <p>Not thread-safe: must be used from the SynchronizationContext of the helper passed to the
 * constructor.
 */
final class TargetResolver {
  private static final NameResolver resolver;

  private boolean nameResolverStarted;

  TargetResolver(String target, NameResolver.Factory nameResolverFactory,
      NameResolver.Helper nameResolverHelper) {
    this.resolver = getNameResolver(target, nameResolverFactory, nameResolverHelper);
  }

  void start(NameResolver.Listener listener, boolean allowEmptyAddressList) {
  }

  @VisibleForTesting
  static NameResolver getNameResolver(String target, NameResolver.Factory nameResolverFactory,
      NameResolver.Helper nameResolverHelper) {
    // Finding a NameResolver. Try using the target string as the URI. If that fails, try prepending
    // "dns:///".
    URI targetUri = null;
    StringBuilder uriSyntaxErrors = new StringBuilder();
    try {
      targetUri = new URI(target);
      // For "localhost:8080" this would likely cause newNameResolver to return null, because
      // "localhost" is parsed as the scheme. Will fall into the next branch and try
      // "dns:///localhost:8080".
    } catch (URISyntaxException e) {
      // Can happen with ip addresses like "[::1]:1234" or 127.0.0.1:1234.
      uriSyntaxErrors.append(e.getMessage());
    }
    if (targetUri != null) {
      NameResolver resolver = nameResolverFactory.newNameResolver(targetUri, nameResolverHelper);
      if (resolver != null) {
        return resolver;
      }
      // "foo.googleapis.com:8080" cause resolver to be null, because "foo.googleapis.com" is an
      // unmapped scheme. Just fall through and will try "dns:///foo.googleapis.com:8080"
    }

    // If we reached here, the targetUri couldn't be used.
    if (!URI_PATTERN.matcher(target).matches()) {
      // It doesn't look like a URI target. Maybe it's an authority string. Try with the default
      // scheme from the factory.
      try {
        targetUri = new URI(nameResolverFactory.getDefaultScheme(), "", "/" + target, null);
      } catch (URISyntaxException e) {
        // Should not be possible.
        throw new IllegalArgumentException(e);
      }
      NameResolver resolver = nameResolverFactory.newNameResolver(targetUri, nameResolverHelper);
      if (resolver != null) {
        return resolver;
      }
    }
    throw new IllegalArgumentException(String.format(
        "cannot find a NameResolver for %s%s",
        target, uriSyntaxErrors.length() > 0 ? " (" + uriSyntaxErrors + ")" : ""));
  }

  private static final class ListenerImpl extends NameResolver.Listener {
    final NameResolver.Listener delegate;
    final boolean allowEmptyAddressList;

    ListenerImpl(NameResolver.Listener delegate, boolean allowEmptyAddressList) {
      this.delegate = checkNotNull(delegate, "delegate");
      this.allowEmptyAddressList = allowEmptyAddressList;
    }

    @Override
    public void onAddresses(List<EquivalentAddressGroup> servers, Attributes attributes) {
      if (servers.isEmpty() && !allowEmptyAddressList) {
        delegate.onError(Status.UNAVAILABLE.withDescription(
                "Name resolver " + resolver + " returned an empty list"));
      } else {
        delegate.onAddresses(servers, attributes);
      }
    }

    @Override
    public void onError(Status error) {
      delegate.onError(error);
    }
  }
}
