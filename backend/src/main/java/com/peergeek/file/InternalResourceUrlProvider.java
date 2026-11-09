package com.peergeek.file;

import java.net.URI;

/**
 * Supplies a short-lived internal GET URL for a stored resource.
 * Implementations must only return URLs for resources already authorized by
 * the calling use case.
 */
public interface InternalResourceUrlProvider {
	URI createInternalPresignedGetUrl(long resourceId);
}
