package com.peergeek.lesson;

import java.net.URI;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.config.CourseSettings;
import com.peergeek.course.CourseInputValidationException;
import org.springframework.stereotype.Component;

@Component
public class LessonContentValidator {
	private static final Set<String> BLOCKS = Set.of(
			"paragraph", "heading", "bulletList", "orderedList", "blockquote", "codeBlock", "horizontalRule");
	private static final Set<String> INLINE = Set.of("text", "hardBreak");
	private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)*");
	private final ObjectMapper mapper;
	private final CourseSettings settings;

	public LessonContentValidator(ObjectMapper mapper, CourseSettings settings) {
		this.mapper = mapper;
		this.settings = settings;
	}

	public int validateAndCount(JsonNode document) {
		if (document == null || !document.isObject()) fail("document", "must be a ProseMirror document object");
		try {
			int size = mapper.writeValueAsBytes(document).length;
			if (size > settings.getMaximumContentJsonBytes()) {
				fail("document", "must not exceed " + settings.getMaximumContentJsonBytes() + " JSON bytes");
			}
		} catch (JsonProcessingException exception) {
			throw new CourseInputValidationException("document", "could not be serialized as JSON");
		}
		if (!"doc".equals(type(document))) fail("document.type", "must be doc");
		validateNode(document, 1, "document");
		return countWords(document);
	}

	private void validateNode(JsonNode node, int depth, String path) {
		if (depth > settings.getMaximumContentDepth()) {
			fail(path, "exceeds the maximum nesting depth of " + settings.getMaximumContentDepth());
		}
		if (!node.isObject()) fail(path, "must be an object");
		String type = type(node);
		Set<String> allowedKeys = Set.of("type", "attrs", "content", "marks", "text");
		rejectExtraKeys(node, allowedKeys, path);
		if (!Set.of("doc", "paragraph", "heading", "text", "bulletList", "orderedList",
				"listItem", "blockquote", "codeBlock", "hardBreak", "horizontalRule").contains(type)) {
			fail(path + ".type", "node type '" + type + "' is not allowed");
		}
		validateAttrs(node, type, path);
		if (node.has("marks")) {
			if (!"text".equals(type)) fail(path + ".marks", "marks are allowed only on text nodes");
			validateMarks(node.get("marks"), path + ".marks");
		}
		if ("text".equals(type)) {
			if (!node.path("text").isTextual()) fail(path + ".text", "must be a string");
			if (node.has("content")) fail(path + ".content", "text nodes cannot have content");
			return;
		}
		if (node.has("text")) fail(path + ".text", "is not allowed on this node");
		JsonNode content = node.get("content");
		if (requiresContent(type) && (content == null || !content.isArray())) {
			fail(path + ".content", "must be an array");
		}
		if (content != null) {
			if (!content.isArray()) fail(path + ".content", "must be an array");
			for (int index = 0; index < content.size(); index++) {
				JsonNode child = content.get(index);
				String childType = type(child);
				if (!isAllowedChild(type, childType)) {
					fail(path + ".content[" + index + "]", "node type '" + childType + "' is not valid inside " + type);
				}
				validateNode(child, depth + 1, path + ".content[" + index + "]");
			}
		}
	}

	private void validateAttrs(JsonNode node, String type, String path) {
		JsonNode attrs = node.get("attrs");
		if ("heading".equals(type)) {
			if (attrs == null || !attrs.isObject() || attrs.size() != 1
					|| !attrs.has("level") || !attrs.get("level").canConvertToInt()) {
				fail(path + ".attrs", "must contain only level (1, 2, or 3)");
			}
			int level = attrs.get("level").intValue();
			if (level < 1 || level > 3) fail(path + ".attrs.level", "must be 1, 2, or 3");
			return;
		}
		if ("codeBlock".equals(type)) {
			if (attrs == null) return;
			if (!attrs.isObject() || attrs.size() > 1 || (attrs.size() == 1 && !attrs.has("language"))) {
				fail(path + ".attrs", "may contain only language");
			}
			if (attrs.has("language") && (!attrs.get("language").isTextual()
					|| attrs.get("language").textValue().length() > 30)) {
				fail(path + ".attrs.language", "must be a string of at most 30 characters");
			}
			return;
		}
		if (attrs != null) fail(path + ".attrs", "attributes are not allowed on " + type);
	}

	private void validateMarks(JsonNode marks, String path) {
		if (!marks.isArray()) fail(path, "must be an array");
		for (int index = 0; index < marks.size(); index++) {
			JsonNode mark = marks.get(index);
			String markPath = path + "[" + index + "]";
			if (!mark.isObject() || !mark.path("type").isTextual()) fail(markPath, "must have a string type");
			String type = mark.get("type").textValue();
			if (!Set.of("bold", "italic", "code", "link").contains(type)) {
				fail(markPath + ".type", "mark type '" + type + "' is not allowed");
			}
			Set<String> keys = "link".equals(type) ? Set.of("type", "attrs") : Set.of("type");
			rejectExtraKeys(mark, keys, markPath);
			if ("link".equals(type)) validateLink(mark.get("attrs"), markPath + ".attrs");
		}
	}

	private void validateLink(JsonNode attrs, String path) {
		if (attrs == null || !attrs.isObject() || attrs.size() != 1
				|| !attrs.path("href").isTextual()) fail(path, "must contain only an href string");
		String href = attrs.get("href").textValue();
		try {
			String scheme = URI.create(href).getScheme();
			if (scheme == null || !Set.of("http", "https", "mailto").contains(scheme.toLowerCase())) {
				fail(path + ".href", "must use http, https, or mailto");
			}
		} catch (IllegalArgumentException exception) {
			fail(path + ".href", "must be a valid http, https, or mailto URL");
		}
	}

	private int countWords(JsonNode node) {
		int total = 0;
		if ("text".equals(type(node))) {
			Matcher matcher = WORD.matcher(node.get("text").textValue());
			while (matcher.find()) total++;
		}
		JsonNode content = node.get("content");
		if (content != null && content.isArray()) {
			for (JsonNode child : content) total += countWords(child);
		}
		return total;
	}

	private boolean requiresContent(String type) {
		return Set.of("doc", "bulletList", "orderedList", "listItem", "blockquote", "codeBlock").contains(type);
	}

	private boolean isAllowedChild(String parent, String child) {
		return switch (parent) {
			case "doc", "blockquote", "listItem" -> BLOCKS.contains(child) || "listItem".equals(parent) && "paragraph".equals(child);
			case "paragraph", "heading" -> INLINE.contains(child);
			case "bulletList", "orderedList" -> "listItem".equals(child);
			case "codeBlock" -> "text".equals(child);
			default -> false;
		};
	}

	private String type(JsonNode node) {
		return node != null && node.path("type").isTextual() ? node.get("type").textValue() : "";
	}

	private void rejectExtraKeys(JsonNode node, Set<String> allowed, String path) {
		Iterator<String> names = node.fieldNames();
		while (names.hasNext()) {
			String name = names.next();
			if (!allowed.contains(name)) fail(path + "." + name, "is not allowed");
		}
	}

	private void fail(String field, String message) {
		throw new CourseInputValidationException(field, message);
	}
}
