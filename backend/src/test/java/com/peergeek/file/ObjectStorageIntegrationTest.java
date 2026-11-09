package com.peergeek.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import com.peergeek.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ObjectStorageIntegrationTest extends AbstractIntegrationTest {
	@Autowired
	private ObjectStorageService storage;

	@Test
	void uploadsAndDownloadsAnObjectThroughPublicPresignedUrls() throws Exception {
		String key = "phase3-integration/" + UUID.randomUUID();
		byte[] content = "resource object storage integration".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		var upload = storage.presignPut(key, "text/plain", content.length, Duration.ofMinutes(5));
		HttpClient http = HttpClient.newHttpClient();
		try {
			HttpRequest put = HttpRequest.newBuilder(upload.url())
					.header("Content-Type", "text/plain")
					.PUT(HttpRequest.BodyPublishers.ofByteArray(content))
					.build();
			HttpResponse<Void> uploaded = http.send(put, HttpResponse.BodyHandlers.discarding());
			assertThat(uploaded.statusCode()).isEqualTo(200);
			assertThat(storage.head(key))
					.contains(new ObjectStorageService.ObjectHead(content.length, "text/plain"));
			assertThat(storage.listObjects("phase3-integration/", null).objects())
					.extracting(ObjectStorageService.StoredObject::key)
					.contains(key);

			URI downloadUrl = storage.presignGet(
					key, "resource.txt", Duration.ofMinutes(5), ObjectStorageService.Disposition.ATTACHMENT).url();
			HttpResponse<byte[]> downloaded = http.send(
					HttpRequest.newBuilder(downloadUrl).GET().build(),
					HttpResponse.BodyHandlers.ofByteArray());
			assertThat(downloaded.statusCode()).isEqualTo(200);
			assertThat(downloaded.body()).containsExactly(content);
		} finally {
			storage.delete(key);
		}
	}
}
