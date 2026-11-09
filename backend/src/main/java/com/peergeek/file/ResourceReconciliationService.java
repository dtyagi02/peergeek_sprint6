package com.peergeek.file;

import java.time.Instant;
import java.util.List;
import com.peergeek.config.ResourceSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Component
public class ResourceReconciliationService {
	private static final Logger log = LoggerFactory.getLogger(ResourceReconciliationService.class);
	private final ResourceRepository resources;
	private final ObjectStorageService storage;
	private final ResourceSettings settings;
	private final ResourceProcessingDispatcher processing;
	private final ResourceEventPublisher events;
	private final TransactionTemplate transactions;

	public ResourceReconciliationService(
			ResourceRepository resources,
			ObjectStorageService storage,
			ResourceSettings settings,
			ResourceProcessingDispatcher processing,
			ResourceEventPublisher events,
			PlatformTransactionManager transactionManager) {
		this.resources = resources;
		this.storage = storage;
		this.settings = settings;
		this.processing = processing;
		this.events = events;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	@Scheduled(
			fixedDelayString = "${peergeek.resources.cleanup-interval:PT15M}",
			initialDelayString = "${peergeek.resources.cleanup-interval:PT15M}")
	public void reconcileResources() {
		transactions.executeWithoutResult(status -> expireAbandonedUploads());
		transactions.executeWithoutResult(status -> retryStuckProcessing());
		purgeDeletedObjects();
		purgeOrphanObjects();
	}

	private void expireAbandonedUploads() {
		Instant cutoff = Instant.now().minus(settings.abandonedUploadAge());
		for (ResourceEntity resource : resources.findByStatusAndDeletedAtIsNullAndUpdatedAtBefore(
				ResourceStatus.PENDING, cutoff)) {
			resource.markFailed("UPLOAD_EXPIRED");
			resource.softDelete(Instant.now());
			events.publishAfterCommit(resource.getCourseId(), resource.getLessonId(),
					resource.getId(), ResourceChangeType.DELETED);
			log.info("Expired abandoned resource upload resourceId={}", resource.getId());
		}
	}

	private void retryStuckProcessing() {
		Instant cutoff = Instant.now().minus(settings.stuckProcessingAge());
		for (ResourceStatus status : new ResourceStatus[] {ResourceStatus.PROCESSING, ResourceStatus.UPLOADED}) {
			for (ResourceEntity resource : resources.findByStatusAndDeletedAtIsNullAndUpdatedAtBefore(status, cutoff)) {
				if (resource.getProcessingAttempts() >= settings.maxProcessingAttempts()) {
					resource.markFailed("PROCESSING_RETRY_EXHAUSTED");
					events.publishAfterCommit(resource.getCourseId(), resource.getLessonId(),
							resource.getId(), ResourceChangeType.FAILED);
					log.error("Resource processing retries exhausted resourceId={} attempts={}",
							resource.getId(), resource.getProcessingAttempts());
				} else {
					resource.markProcessing();
					processing.dispatchAfterCommit(resource.getId());
					log.warn("Retrying stuck resource processing resourceId={} attempts={}",
							resource.getId(), resource.getProcessingAttempts());
				}
			}
		}
	}

	private void purgeDeletedObjects() {
		Instant cutoff = Instant.now().minus(settings.deletedResourceGrace());
		for (ResourceEntity resource : resources.findByDeletedAtIsNotNullAndDeletedAtBefore(cutoff)) {
			if (resource.getObjectKey() != null) {
				storage.delete(resource.getObjectKey());
				log.info("Purged deleted resource object resourceId={}", resource.getId());
			}
		}
	}

	private void purgeOrphanObjects() {
		Instant cutoff = Instant.now().minus(settings.deletedResourceGrace());
		String token = null;
		do {
			ObjectStorageService.ObjectPage page = storage.listObjects("courses/", token);
			List<String> keys = page.objects().stream()
					.map(ObjectStorageService.StoredObject::key)
					.toList();
			java.util.Set<String> registered = keys.isEmpty()
					? java.util.Set.of()
					: resources.findByObjectKeyIn(keys).stream()
							.map(ResourceEntity::getObjectKey)
							.collect(java.util.stream.Collectors.toSet());
			for (ObjectStorageService.StoredObject object : page.objects()) {
				if (!registered.contains(object.key())
						&& object.lastModified() != null
						&& object.lastModified().isBefore(cutoff)) {
					storage.delete(object.key());
					log.warn("Purged orphaned resource object key={}", object.key());
				}
			}
			token = page.continuationToken();
		} while (token != null);
	}
}
