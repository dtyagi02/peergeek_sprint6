package com.peergeek.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableScheduling
public class ResourceProcessingConfig {
	@Bean(name = "resourceProcessingExecutor")
	ThreadPoolTaskExecutor resourceProcessingExecutor(ResourceSettings settings) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(settings.processingThreads());
		executor.setMaxPoolSize(settings.processingThreads());
		executor.setQueueCapacity(settings.processingQueueCapacity());
		executor.setThreadNamePrefix("resource-processing-");
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(30);
		return executor;
	}

	@Bean(name = "readinessExecutor")
	ThreadPoolTaskExecutor readinessExecutor(ReadinessSettings settings) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(settings.getRuns().getThreads());
		executor.setMaxPoolSize(settings.getRuns().getThreads());
		executor.setQueueCapacity(settings.getRuns().getQueueCapacity());
		executor.setThreadNamePrefix("readiness-");
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(30);
		return executor;
	}
}
