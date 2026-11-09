package com.peergeek;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@Tag("integration")
@ExtendWith(DockerAvailabilityCondition.class)
public abstract class AbstractIntegrationTest {
	protected static final MySQLContainer<?> MYSQL =
			new MySQLContainer<>(DockerImageName.parse("mysql:8.4.5"))
					.withDatabaseName("peergeek")
					.withUsername("peergeek")
					.withPassword("peergeek-test");

	protected static final GenericContainer<?> MINIO =
			new GenericContainer<>(DockerImageName.parse(
					"bitnamilegacy/minio:2025.2.28-debian-12-r0"))
					.withEnv("MINIO_ROOT_USER", "peergeek-test")
					.withEnv("MINIO_ROOT_PASSWORD", "peergeek-test-secret")
					.withCommand("/opt/bitnami/scripts/minio/run.sh")
					.withExposedPorts(9000)
					.waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

	protected static final GenericContainer<?> REDIS =
			new GenericContainer<>(DockerImageName.parse("redis:7.4.2-alpine"))
					.withExposedPorts(6379);

	@BeforeAll
	protected static synchronized void startSharedContainers() {
		if (!MYSQL.isRunning()) MYSQL.start();
		if (!MINIO.isRunning()) MINIO.start();
		if (!REDIS.isRunning()) REDIS.start();
	}

	@DynamicPropertySource
	static void infrastructureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
		registry.add("spring.datasource.username", MYSQL::getUsername);
		registry.add("spring.datasource.password", MYSQL::getPassword);
		registry.add("peergeek.redis.host", REDIS::getHost);
		registry.add("peergeek.redis.port", () -> REDIS.getMappedPort(6379));
		registry.add("peergeek.storage.endpoint",
				() -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
		registry.add("peergeek.storage.public-endpoint",
				() -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
		registry.add("peergeek.storage.region", () -> "us-east-1");
		registry.add("peergeek.storage.access-key", () -> "peergeek-test");
		registry.add("peergeek.storage.secret-key", () -> "peergeek-test-secret");
		registry.add("peergeek.storage.bucket", () -> "peergeek-test");
		registry.add("peergeek.storage.path-style", () -> true);
		registry.add("peergeek.storage.create-bucket-if-missing", () -> true);
	}
}
