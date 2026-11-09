package com.peergeek.assignment;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class QuestionOptionsConverter implements AttributeConverter<List<QuestionOption>, String> {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Override
	public String convertToDatabaseColumn(List<QuestionOption> attribute) {
		if (attribute == null || attribute.isEmpty()) {
			return null;
		}
		try {
			return MAPPER.writeValueAsString(attribute);
		} catch (Exception e) {
			throw new IllegalArgumentException("Failed to serialize question options", e);
		}
	}

	@Override
	public List<QuestionOption> convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return new ArrayList<>();
		}
		try {
			return MAPPER.readValue(dbData, new TypeReference<List<QuestionOption>>() {});
		} catch (Exception e) {
			throw new IllegalArgumentException("Failed to deserialize question options", e);
		}
	}
}
