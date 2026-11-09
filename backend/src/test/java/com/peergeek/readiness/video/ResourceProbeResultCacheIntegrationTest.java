package com.peergeek.readiness.video;

import static org.assertj.core.api.Assertions.assertThat;

import com.peergeek.AbstractIntegrationTest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ResourceProbeResultCacheIntegrationTest extends AbstractIntegrationTest {
	private static final long RESOURCE_ID = 9_876_543_210L;
	private static final String CHECKSUM = "b".repeat(64);

	@Autowired
	private ResourceProbeResultCache cache;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void storesAndReadsVideoProbeResultByChecksum() {
		jdbcTemplate.update("delete from resource_probe_results where resource_id = ?", RESOURCE_ID);
		VideoProbeOutcome expected = new VideoProbeOutcome(true, null, 2_500L, 1920, 1080, "h264");

		cache.saveVideo(RESOURCE_ID, CHECKSUM, expected);
		Optional<ResourceProbeResult> actual = cache.findVideo(RESOURCE_ID, CHECKSUM);

		assertThat(actual).isPresent();
		assertThat(actual.orElseThrow().outcome()).isEqualTo(expected);
		assertThat(cache.findVideo(RESOURCE_ID, "c".repeat(64))).isEmpty();
	}
}
