package com.peergeek.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import com.peergeek.audit.AuditService;
import com.peergeek.config.ResourceSettings;
import com.peergeek.course.CourseThumbnailAccessor;
import com.peergeek.user.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class ResourceProcessingWorkerTest {
	@Test
	void streamsLargeUtf8TextAndComputesItsSha256() throws Exception {
		byte[] prefix = "x".repeat(65_535).getBytes(StandardCharsets.UTF_8);
		byte[] tail = "€".getBytes(StandardCharsets.UTF_8);
		byte[] content = new byte[prefix.length + tail.length];
		System.arraycopy(prefix, 0, content, 0, prefix.length);
		System.arraycopy(tail, 0, content, prefix.length, tail.length);
		ResourceEntity resource = processingResource(content.length);
		ResourceRepository resources = mock(ResourceRepository.class);
		ObjectStorageService storage = mock(ObjectStorageService.class);
		PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
		when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		when(resources.findByIdAndDeletedAtIsNull(22L)).thenReturn(Optional.of(resource));
		when(storage.openStream("key")).thenReturn(new ByteArrayInputStream(content));

		worker(resources, storage, transactions).process(22L);

		assertThat(resource.getStatus()).isEqualTo(ResourceStatus.READY);
		assertThat(resource.getChecksumSha256())
				.isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)));
		assertThat(resource.getSizeBytes()).isEqualTo((long) content.length);
	}

	@Test
	void rejectsMalformedUtf8BeyondTheBoundedContentSample() {
		byte[] prefix = "x".repeat(65_536).getBytes(StandardCharsets.UTF_8);
		byte[] content = java.util.Arrays.copyOf(prefix, prefix.length + 1);
		content[content.length - 1] = (byte) 0xff;
		ResourceEntity resource = processingResource(content.length);
		ResourceRepository resources = mock(ResourceRepository.class);
		ObjectStorageService storage = mock(ObjectStorageService.class);
		PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
		when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		when(resources.findByIdAndDeletedAtIsNull(22L)).thenReturn(Optional.of(resource));
		when(storage.openStream("key")).thenReturn(new ByteArrayInputStream(content));

		worker(resources, storage, transactions).process(22L);

		assertThat(resource.getStatus()).isEqualTo(ResourceStatus.FAILED);
		assertThat(resource.getFailureReason()).isEqualTo("CONTENT_TYPE_MISMATCH");
	}

	private ResourceProcessingWorker worker(
			ResourceRepository resources, ObjectStorageService storage,
			PlatformTransactionManager transactions) {
		return new ResourceProcessingWorker(resources, storage, new ResourceSettings(),
				mock(AuditService.class), mock(CourseThumbnailAccessor.class),
				mock(ResourceEventPublisher.class), transactions);
	}

	private ResourceEntity processingResource(long size) {
		UserEntity owner = mock(UserEntity.class);
		when(owner.getId()).thenReturn(7L);
		ResourceEntity resource = new ResourceEntity(
				3, 4L, owner, ResourcePurpose.LESSON_RESOURCE, ResourceType.DOCUMENT,
				"Text", "notes.txt", "key", "text/plain", size, null, 0, ResourceStatus.PROCESSING);
		ReflectionTestUtils.setField(resource, "id", 22L);
		return resource;
	}
}
