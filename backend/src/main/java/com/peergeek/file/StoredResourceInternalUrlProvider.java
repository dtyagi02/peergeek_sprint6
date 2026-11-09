package com.peergeek.file;

import java.net.URI;
import org.springframework.stereotype.Component;

@Component
public class StoredResourceInternalUrlProvider implements InternalResourceUrlProvider {
	private final ResourceDownloadService downloads;

	public StoredResourceInternalUrlProvider(ResourceDownloadService downloads) {
		this.downloads = downloads;
	}

	@Override
	public URI createInternalPresignedGetUrl(long resourceId) {
		return downloads.internalPresignedGetUrl(resourceId, ObjectStorageService.Disposition.INLINE);
	}
}
