package com.peergeek.file;

import java.net.URI;
import com.peergeek.config.ResourceSettings;
import com.peergeek.exception.ConflictException;
import com.peergeek.exception.NotFoundException;
import org.springframework.stereotype.Service;

@Service
public class ResourceDownloadService {
	private final ResourceRepository resources;
	private final ObjectStorageService storage;
	private final ResourceSettings settings;

	public ResourceDownloadService(
			ResourceRepository resources, ObjectStorageService storage, ResourceSettings settings) {
		this.resources = resources;
		this.storage = storage;
		this.settings = settings;
	}

	public URI presignedGetUrl(long resourceId, ObjectStorageService.Disposition mode) {
		ResourceEntity resource = readyResource(resourceId);
		if (resource.getType() == ResourceType.LINK) {
			return URI.create(resource.getExternalUrl());
		}
		return storage.presignGet(resource.getObjectKey(), resource.getOriginalFilename(),
				settings.getUrlTtl(), mode).url();
	}

	public URI internalPresignedGetUrl(long resourceId, ObjectStorageService.Disposition mode) {
		ResourceEntity resource = readyResource(resourceId);
		if (resource.getType() == ResourceType.LINK) {
			throw new ConflictException("External links do not have stored object bytes.");
		}
		return storage.presignGetInternal(resource.getObjectKey(), resource.getOriginalFilename(),
				settings.getUrlTtl(), mode).url();
	}

	private ResourceEntity readyResource(long resourceId) {
		ResourceEntity resource = resources.findByIdAndDeletedAtIsNull(resourceId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));
		if (resource.getStatus() != ResourceStatus.READY) {
			throw new ConflictException("The resource is not ready.");
		}
		return resource;
	}
}
