package com.peergeek.readiness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import com.peergeek.config.ReadinessSettings;
import org.junit.jupiter.api.Test;

class SafeLinkCheckerTest {
	@Test
	void blocksLoopbackTargetsBeforeOpeningASocket() {
		LinkCheckStore store = mock(LinkCheckStore.class);
		when(store.findFresh(org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
				.thenReturn(Optional.empty());

		SafeLinkChecker.Result result = new SafeLinkChecker(new ReadinessSettings(), store)
				.check(1, "http://127.0.0.1/private");

		assertThat(result.outcome()).isEqualTo(SafeLinkChecker.Outcome.UNSAFE);
		verify(store).save(org.mockito.ArgumentMatchers.eq(1L),
				org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("UNSAFE"),
				org.mockito.ArgumentMatchers.isNull());
	}

	@Test
	void rejectsNonHttpSchemes() {
		LinkCheckStore store = mock(LinkCheckStore.class);
		when(store.findFresh(org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
				.thenReturn(Optional.empty());

		SafeLinkChecker.Result result = new SafeLinkChecker(new ReadinessSettings(), store)
				.check(1, "file:///etc/passwd");

		assertThat(result.outcome()).isEqualTo(SafeLinkChecker.Outcome.UNSAFE);
	}
}
