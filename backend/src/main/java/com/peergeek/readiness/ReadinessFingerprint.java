package com.peergeek.readiness;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.course.CourseStructureQueryService;
import org.springframework.stereotype.Component;

@Component
public class ReadinessFingerprint {
	private final ObjectMapper objectMapper;
	private final CourseStructureQueryService structures;

	public ReadinessFingerprint(ObjectMapper objectMapper, CourseStructureQueryService structures) {
		this.objectMapper = objectMapper;
		this.structures = structures;
	}

	public String calculate(ReadinessContext context) {
		try {
			List<LessonContentFingerprint> lessonContents = new ArrayList<>();
			for (var module : context.structure().modules()) {
				for (var lesson : module.lessons()) {
					var content = structures.getLessonContent(lesson.id());
					lessonContents.add(new LessonContentFingerprint(lesson.id(), content.version(),
							content.wordCount(), content.document()));
				}
			}
			byte[] payload = objectMapper.writeValueAsBytes(
					new FingerprintPayload(context, lessonContents));
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
		} catch (JsonProcessingException | NoSuchAlgorithmException exception) {
			throw new IllegalStateException("Unable to calculate the readiness fingerprint.", exception);
		}
	}

	private record FingerprintPayload(ReadinessContext context, List<LessonContentFingerprint> lessons) {
	}

	private record LessonContentFingerprint(long lessonId, long version, int wordCount,
			com.fasterxml.jackson.databind.JsonNode document) {
	}
}
