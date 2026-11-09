package com.peergeek.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OpenApiCustomizer;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;

@Configuration
public class OpenApiConfig {

	@Bean
	OpenApiCustomizer standardErrorSchema() {
		return openApi -> {
			if (openApi.getComponents() == null) {
				openApi.setComponents(new Components());
			}
			openApi.getComponents().addSchemas("ApiError", new ObjectSchema()
				.addProperty("timestamp", new StringSchema().format("date-time"))
				.addProperty("status", new IntegerSchema())
				.addProperty("error", new StringSchema())
				.addProperty("code", new StringSchema())
				.addProperty("message", new StringSchema())
				.addProperty("path", new StringSchema())
				.addProperty("fieldErrors", new ArraySchema().items(new ObjectSchema()
						.addProperty("field", new StringSchema())
						.addProperty("message", new StringSchema()))));
		};
	}
}
