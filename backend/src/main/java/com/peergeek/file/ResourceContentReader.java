package com.peergeek.file;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import com.peergeek.exception.NotFoundException;
import org.springframework.stereotype.Service;

/**
 * Reads stored content without making an authorization decision.
 * No authorization, internal use only: callers must authorize before use and
 * must never expose object keys or internal presigned URLs to clients.
 */
@Service
public class ResourceContentReader {
	private final ResourceRepository resources;
	private final ObjectStorageService storage;

	public ResourceContentReader(ResourceRepository resources, ObjectStorageService storage) {
		this.resources = resources;
		this.storage = storage;
	}

	public ObjectStat statObject(long resourceId) {
		ResourceEntity resource = storedResource(resourceId);
		Optional<ObjectStorageService.ObjectHead> head = storage.head(resource.getObjectKey());
		return head.map(value -> new ObjectStat(true, value.sizeBytes()))
				.orElseGet(() -> new ObjectStat(false, null));
	}

	public InputStream openStream(long resourceId) {
		return storage.openStream(storedResource(resourceId).getObjectKey());
	}

	public URI internalPresignedGetUrl(long resourceId, Duration ttl) {
		ResourceEntity resource = storedResource(resourceId);
		return storage.presignGetInternal(resource.getObjectKey(), resource.getOriginalFilename(),
				ttl, ObjectStorageService.Disposition.INLINE).url();
	}

	private ResourceEntity storedResource(long resourceId) {
		ResourceEntity resource = resources.findByIdAndDeletedAtIsNull(resourceId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));
		if (resource.getType() == ResourceType.LINK || resource.getObjectKey() == null) {
			throw new IllegalArgumentException("The resource does not have stored object content.");
		}
		return resource;
	}

	public record ObjectStat(boolean exists, Long sizeBytes) {
	}
}
