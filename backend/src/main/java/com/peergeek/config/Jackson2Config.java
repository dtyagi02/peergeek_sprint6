package com.peergeek.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class Jackson2Config implements WebMvcConfigurer {

	@Bean
	ObjectMapper objectMapper() {
		return new ObjectMapper()
				.findAndRegisterModules()
				.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	}

	@Override
	public void extendMessageConverters(java.util.List<HttpMessageConverter<?>> converters) {
		int insertionIndex = 0;
		for (int index = 0; index < converters.size(); index++) {
			if (converters.get(index) instanceof StringHttpMessageConverter) {
				insertionIndex = index + 1;
				break;
			}
		}
		converters.add(insertionIndex, new MappingJackson2HttpMessageConverter(objectMapper()));
	}
}
