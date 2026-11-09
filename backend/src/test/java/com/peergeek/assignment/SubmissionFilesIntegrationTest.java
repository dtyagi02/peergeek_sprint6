package com.peergeek.assignment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.peergeek.submission.AbandonedFileCleanupJob;
import com.peergeek.submission.SubmissionEntity;
import com.peergeek.submission.SubmissionFileEntity;
import com.peergeek.user.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubmissionFilesIntegrationTest extends AssignmentTestSupport {

	@Autowired private AbandonedFileCleanupJob cleanupJob;

	@Test
	@DisplayName("Real upload to MinIO, confirmation, tampering rejection, download URL, and deletion")
	void fullFileLifecycle() throws Exception {
		UserEntity instructor = createInstructor("prof.file@example.test", "Prof File");
		String instructorToken = tokenFor(instructor);
		long courseId = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, courseId);

		long assignmentId = createHistoricalProjectAssignment(courseId, "File Project", instructor.getId());

		UserEntity learner = createUser("learner.file@example.test", "Learner File");
		String learnerToken = tokenFor(learner);
		enrollLearner(learnerToken, courseId);

		// Start attempt
		mockMvc.perform(post("/api/v1/learning/assignments/{assignmentId}/start", assignmentId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk());

		// 1. Request presigned upload for a valid PDF file
		byte[] validPdfBytes = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes(StandardCharsets.UTF_8);

		ObjectNode uploadReq = objectMapper.createObjectNode();
		uploadReq.put("filename", "report.pdf");
		uploadReq.put("sizeBytes", validPdfBytes.length);
		uploadReq.put("mimeType", "application/pdf");

		var uploadRes = mockMvc.perform(post("/api/v1/learning/assignments/{assignmentId}/files/uploads", assignmentId)
						.header("Authorization", bearer(learnerToken))
						.contentType("application/json")
						.content(uploadReq.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fileId").isNumber())
				.andExpect(jsonPath("$.upload.url").isNotEmpty())
				.andReturn();

		JsonNode uploadJson = objectMapper.readTree(uploadRes.getResponse().getContentAsString());
		long fileId = uploadJson.get("fileId").asLong();
		String uploadUrl = uploadJson.get("upload").get("url").asText();

		// 2. Perform HTTP PUT to upload bytes directly into MinIO
		HttpClient httpClient = HttpClient.newHttpClient();
		HttpRequest putRequest = HttpRequest.newBuilder()
				.uri(URI.create(uploadUrl))
				.header("Content-Type", "application/pdf")
				.PUT(HttpRequest.BodyPublishers.ofByteArray(validPdfBytes))
				.build();
		HttpResponse<Void> putResponse = httpClient.send(putRequest, HttpResponse.BodyHandlers.discarding());
		assertThat(putResponse.statusCode()).isIn(200, 204);

		// 3. Confirm upload
		mockMvc.perform(post("/api/v1/learning/assignments/{assignmentId}/files/{fileId}/confirm", assignmentId, fileId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fileId").value(fileId))
				.andExpect(jsonPath("$.status").value("READY"));

		SubmissionFileEntity savedFile = fileRepository.findById(fileId).orElseThrow();
		assertThat(savedFile.getStatus()).isEqualTo(com.peergeek.submission.SubmissionFileStatus.READY);
		assertThat(savedFile.getSizeBytes()).isEqualTo((long) validPdfBytes.length);
		assertThat(savedFile.getChecksumSha256()).isNotNull();

		// 4. Download URL authorized for learner
		mockMvc.perform(get("/api/v1/learning/assignments/{assignmentId}/files/{fileId}/download-url", assignmentId, fileId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.downloadUrl").isNotEmpty());

		// 5. Download URL authorized for course instructor
		SubmissionEntity submission = submissionRepository.findByAssignmentIdAndUserId(assignmentId, learner.getId()).orElseThrow();
		mockMvc.perform(get("/api/v1/instructor/submissions/{submissionId}/files/{fileId}/download-url", submission.getId(), fileId)
						.header("Authorization", bearer(instructorToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.downloadUrl").isNotEmpty());

		// 6. Delete file while IN_PROGRESS
		mockMvc.perform(delete("/api/v1/learning/assignments/{assignmentId}/files/{fileId}", assignmentId, fileId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isNoContent());

		assertThat(fileRepository.findById(fileId)).isEmpty();
	}

	@Test
	@DisplayName("Content sniffing rejects spoofed or tampered file uploads")
	void contentSniffingTamperingRejection() throws Exception {
		UserEntity instructor = createInstructor("prof.sniff@example.test", "Prof Sniff");
		String instructorToken = tokenFor(instructor);
		long courseId = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, courseId);

		long assignmentId = createHistoricalProjectAssignment(courseId, "Tamper Test", instructor.getId());

		UserEntity learner = createUser("learner.tamper@example.test", "Learner Tamper");
		String learnerToken = tokenFor(learner);
		enrollLearner(learnerToken, courseId);

		mockMvc.perform(post("/api/v1/learning/assignments/{assignmentId}/start", assignmentId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk());

		// PNG magic bytes disguised as PDF
		byte[] fakePdfBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};

		ObjectNode uploadReq = objectMapper.createObjectNode();
		uploadReq.put("filename", "trojan.pdf");
		uploadReq.put("sizeBytes", fakePdfBytes.length);
		uploadReq.put("mimeType", "application/pdf");

		var uploadRes = mockMvc.perform(post("/api/v1/learning/assignments/{assignmentId}/files/uploads", assignmentId)
						.header("Authorization", bearer(learnerToken))
						.contentType("application/json")
						.content(uploadReq.toString()))
				.andExpect(status().isOk())
				.andReturn();

		JsonNode uploadJson = objectMapper.readTree(uploadRes.getResponse().getContentAsString());
		long fileId = uploadJson.get("fileId").asLong();
		String uploadUrl = uploadJson.get("upload").get("url").asText();

		HttpClient httpClient = HttpClient.newHttpClient();
		HttpRequest putRequest = HttpRequest.newBuilder()
				.uri(URI.create(uploadUrl))
				.header("Content-Type", "application/pdf")
				.PUT(HttpRequest.BodyPublishers.ofByteArray(fakePdfBytes))
				.build();
		httpClient.send(putRequest, HttpResponse.BodyHandlers.discarding());

		// Confirm should detect PNG sniffing mismatch and set status to FAILED
		mockMvc.perform(post("/api/v1/learning/assignments/{assignmentId}/files/{fileId}/confirm", assignmentId, fileId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.failureReason").isNotEmpty());
	}
}
