package com.peergeek.lesson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.peergeek.config.CourseSettings;
import com.peergeek.course.CourseInputValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LessonContentValidatorTest {
	private final ObjectMapper mapper = new ObjectMapper();
	private final CourseSettings settings = new CourseSettings();
	private LessonContentValidator validator;

	@BeforeEach
	void setUp() {
		validator = new LessonContentValidator(mapper, settings);
	}

	@Test
	void countsUnicodeWordsInAllowedProseMirrorNodes() throws Exception {
		var document = mapper.readTree("""
				{"type":"doc","content":[{"type":"paragraph","content":[
				  {"type":"text","text":"Grüße 世界 42 don't re-enter"},
				  {"type":"text","text":" link","marks":[{"type":"link","attrs":{"href":"mailto:hello@example.org"}}]}
				]}]}
				""");

		assertThat(validator.validateAndCount(document)).isEqualTo(7);
	}

	@Test
	void rejectsUnknownNodeTypesAndMarksWithPreciseFieldErrors() throws Exception {
		assertInvalid("""
				{"type":"doc","content":[{"type":"image"}]}
				""", "document.content[0]");
		assertInvalid("""
				{"type":"doc","content":[{"type":"paragraph","content":[
				  {"type":"text","text":"bad","marks":[{"type":"strike"}]}
				]}]}
				""", "document.content[0].content[0].marks[0].type");
	}

	@Test
	void rejectsUnsafeLinkProtocols() throws Exception {
		assertInvalid("""
				{"type":"doc","content":[{"type":"paragraph","content":[
				  {"type":"text","text":"unsafe","marks":[{"type":"link","attrs":{"href":"javascript:alert(1)"}}]}
				]}]}
				""", "document.content[0].content[0].marks[0].attrs.href");
		assertInvalid("""
				{"type":"doc","content":[{"type":"paragraph","content":[
				  {"type":"text","text":"unsafe","marks":[{"type":"link","attrs":{"href":"data:text/html,bad"}}]}
				]}]}
				""", "document.content[0].content[0].marks[0].attrs.href");
	}

	@Test
	void allowsEmptyDocumentAndRejectsDepthAndOversize() throws Exception {
		assertThat(validator.validateAndCount(mapper.readTree(
				"{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\"}]}"))).isZero();
		assertThatThrownBy(() -> validator.validateAndCount(nestedBlockquotes(21)))
				.isInstanceOf(CourseInputValidationException.class)
				.satisfies(exception -> assertThat(((CourseInputValidationException) exception)
						.getFieldErrors().getFirst().field()).startsWith("document.content"));
		ObjectNode oversized = mapper.createObjectNode().put("type", "doc");
		ArrayNode content = oversized.putArray("content");
		content.add(mapper.createObjectNode().put("type", "paragraph")
				.set("content", mapper.createArrayNode().add(mapper.createObjectNode()
						.put("type", "text").put("text", "x".repeat(settings.getMaximumContentJsonBytes())))));
		assertInvalid(mapper.writeValueAsString(oversized), "document");
	}

	@Test
	void acceptsContentWithMoreThanTenThousandWords() throws Exception {
		String text = "word ".repeat(10_001).trim();
		var document = mapper.createObjectNode().put("type", "doc");
		document.putArray("content").add(mapper.createObjectNode().put("type", "paragraph")
				.set("content", mapper.createArrayNode().add(mapper.createObjectNode()
						.put("type", "text").put("text", text))));

		assertThat(validator.validateAndCount(document)).isEqualTo(10_001);
	}

	private ObjectNode nestedBlockquotes(int count) {
		ObjectNode current = mapper.createObjectNode().put("type", "paragraph");
		for (int index = 0; index < count; index++) {
			ObjectNode wrapper = mapper.createObjectNode().put("type", "blockquote");
			wrapper.putArray("content").add(current);
			current = wrapper;
		}
		ObjectNode root = mapper.createObjectNode().put("type", "doc");
		root.putArray("content").add(current);
		return root;
	}

	private void assertInvalid(String json, String expectedField) throws Exception {
		var document = mapper.readTree(json);
		assertThatThrownBy(() -> validator.validateAndCount(document))
				.isInstanceOf(CourseInputValidationException.class)
				.satisfies(exception -> assertThat(((CourseInputValidationException) exception)
						.getFieldErrors().getFirst().field()).isEqualTo(expectedField));
	}
}
