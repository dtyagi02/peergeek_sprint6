package com.peergeek.readiness.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

class FfprobeRunnerTest {
	private static final FfprobeRunner RUNNER = new FfprobeRunner(new ObjectMapper());
	private static HttpServer server;

	@BeforeAll
	static void startFixtureServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		Map<String, String> fixtures = Map.of(
				"/valid-video.mkv", "video-fixtures/valid-video.mkv",
				"/truncated-video.mkv", "video-fixtures/truncated-video.mkv",
				"/zero-duration-video.mkv", "video-fixtures/zero-duration-video.mkv",
				"/audio-only.mkv", "video-fixtures/audio-only.mkv");
		server.createContext("/", exchange -> {
			String resource = fixtures.get(exchange.getRequestURI().getPath());
			if (resource == null) {
				exchange.sendResponseHeaders(404, -1);
				exchange.close();
				return;
			}
			try (var input = FfprobeRunnerTest.class.getResourceAsStream("/" + resource)) {
				if (input == null) {
					exchange.sendResponseHeaders(404, -1);
				} else {
					byte[] bytes = input.readAllBytes();
					exchange.getResponseHeaders().set("Content-Type", "video/x-matroska");
					exchange.sendResponseHeaders(200, bytes.length);
					exchange.getResponseBody().write(bytes);
				}
			} finally {
				exchange.close();
			}
		});
		server.start();
	}

	@AfterAll
	static void stopFixtureServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void probesValidVideoFixture() {
		requireFfprobe();
		VideoMetadata metadata = RUNNER.probe(fixture("valid-video.mkv"));

		assertThat(metadata.durationMilliseconds()).isEqualTo(1_000);
		assertThat(metadata.width()).isEqualTo(2);
		assertThat(metadata.height()).isEqualTo(2);
		assertThat(metadata.codec()).isNotBlank();
	}

	@Test
	void reportsTruncatedVideoAsInvalid() {
		requireFfprobe();

		assertInvalid("truncated-video.mkv");
	}

	@Test
	void reportsZeroDurationVideoAsInvalid() {
		requireFfprobe();

		assertInvalid("zero-duration-video.mkv");
	}

	@Test
	void reportsAudioOnlyResourceAsInvalidVideo() {
		requireFfprobe();

		assertInvalid("audio-only.mkv");
	}

	@Test
	void reportsUnavailableWhenTheExecutableCannotStart() {
		FfprobeRunner missingExecutable = new FfprobeRunner(new ObjectMapper(), "ffprobe-not-installed");

		assertThatThrownBy(() -> missingExecutable.probe(fixture("valid-video.mkv")))
				.isInstanceOf(VideoProbeException.class)
				.extracting(exception -> ((VideoProbeException) exception).outcome())
				.isEqualTo(VideoProbeException.Outcome.VIDEO_PROBE_UNAVAILABLE);
	}

	@Test
	void rejectsMalformedFfprobeJsonDefensively() {
		assertThatThrownBy(() -> RUNNER.parse("{".getBytes()))
				.isInstanceOf(VideoProbeException.class)
				.extracting(exception -> ((VideoProbeException) exception).outcome())
				.isEqualTo(VideoProbeException.Outcome.VIDEO_INVALID);
	}

	@Test
	void rejectsMissingDurationOrVideoStreamDefensively() {
		assertThatThrownBy(() -> RUNNER.parse("""
				{"format":{"duration":"1.0"},"streams":[{"codec_type":"audio"}]}
				""".getBytes()))
				.isInstanceOf(VideoProbeException.class)
				.extracting(exception -> ((VideoProbeException) exception).outcome())
				.isEqualTo(VideoProbeException.Outcome.VIDEO_INVALID);
	}

	@Test
	void distinguishesResourceRetrievalFailuresFromInvalidMedia() {
		assertThat(FfprobeRunner.isResourceUnavailable("Server returned 403 Forbidden")).isTrue();
		assertThat(FfprobeRunner.isResourceUnavailable("Connection refused")).isTrue();
		assertThat(FfprobeRunner.isResourceUnavailable("Invalid data found when processing input")).isFalse();
	}

	private static void assertInvalid(String fixture) {
		assertThatThrownBy(() -> RUNNER.probe(fixture(fixture)))
				.isInstanceOf(VideoProbeException.class)
				.extracting(exception -> ((VideoProbeException) exception).outcome())
				.isEqualTo(VideoProbeException.Outcome.VIDEO_INVALID);
	}

	private static void requireFfprobe() {
		Assumptions.assumeTrue(RUNNER.isAvailable(), "ffprobe is not installed");
	}

	private static URI fixture(String name) {
		return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/" + name);
	}
}
