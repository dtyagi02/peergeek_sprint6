package com.peergeek.submission;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class SelectedOptionIdsConverter implements AttributeConverter<List<String>, String> {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Override
	public String convertToDatabaseColumn(List<String> attribute) {
		if (attribute == null || attribute.isEmpty()) {
			return null;
		}
		try {
			return MAPPER.writeValueAsString(attribute);
		} catch (Exception e) {
			throw new IllegalArgumentException("Failed to serialize selected option ids", e);
		}
	}

	@Override
	public List<String> convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return new ArrayList<>();
		}
		try {
			return MAPPER.readValue(dbData, new TypeReference<List<String>>() {});
		} catch (Exception e) {
			throw new IllegalArgumentException("Failed to deserialize selected option ids", e);
		}
	}
}
