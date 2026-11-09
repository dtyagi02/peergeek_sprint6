package com.peergeek.readiness.video;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.peergeek.config.ReadinessSettings;

@Component
public class FfprobeRunner {
	private static final Duration DEFAULT_PROCESS_TIMEOUT = Duration.ofSeconds(30);
	private static final int MAX_OUTPUT_BYTES = 1_048_576;
	private static final int MAX_ERROR_BYTES = 65_536;
	private static final int READ_JOIN_MILLIS = 2_000;

	private final ObjectMapper objectMapper;
	private final String executable;
	private final Duration processTimeout;

	@Autowired
	public FfprobeRunner(ObjectMapper objectMapper, ReadinessSettings settings) {
		this(objectMapper, "ffprobe", settings.getVideo().getTimeout());
	}

	FfprobeRunner(ObjectMapper objectMapper) {
		this(objectMapper, "ffprobe", DEFAULT_PROCESS_TIMEOUT);
	}

	FfprobeRunner(ObjectMapper objectMapper, String executable) {
		this(objectMapper, executable, DEFAULT_PROCESS_TIMEOUT);
	}

	private FfprobeRunner(ObjectMapper objectMapper, String executable, Duration processTimeout) {
		this.objectMapper = objectMapper;
		this.executable = executable;
		this.processTimeout = processTimeout;
	}

	public VideoMetadata probe(URI internalPresignedUrl) {
		validateUrl(internalPresignedUrl);
		Process process;
		try {
			process = new ProcessBuilder(List.of(
					executable,
					"-v", "error",
					"-protocol_whitelist", "http,https,tcp,tls,crypto",
					"-show_format",
					"-show_streams",
					"-print_format", "json",
					internalPresignedUrl.toASCIIString()))
					.start();
		} catch (IOException exception) {
			throw unavailable("ffprobe could not be started", exception);
		}

		CapturedStream stdout = new CapturedStream(process.getInputStream(), MAX_OUTPUT_BYTES);
		CapturedStream stderr = new CapturedStream(process.getErrorStream(), MAX_ERROR_BYTES);
		Thread stdoutReader = Thread.ofVirtual().start(stdout::read);
		Thread stderrReader = Thread.ofVirtual().start(stderr::read);
		try {
			if (!process.waitFor(processTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
				process.destroyForcibly();
				process.waitFor();
				joinReaders(stdoutReader, stderrReader);
				throw unavailable("ffprobe exceeded its execution timeout", null);
			}
			joinReaders(stdoutReader, stderrReader);
		} catch (InterruptedException exception) {
			process.destroyForcibly();
			Thread.currentThread().interrupt();
			throw unavailable("ffprobe execution was interrupted", exception);
		}

		if (stdout.failure() != null || stderr.failure() != null) {
			throw unavailable("Could not read ffprobe output", stdout.failure() != null
					? stdout.failure() : stderr.failure());
		}
		if (stdout.exceededLimit() || stderr.exceededLimit()) {
			throw invalid("ffprobe output exceeded the configured safety limit");
		}
		if (process.exitValue() != 0) {
			if (isResourceUnavailable(stderr.text())) {
				throw unavailable("ffprobe could not retrieve the internal resource", null);
			}
			throw invalid("ffprobe rejected the video resource");
		}
		return parse(stdout.bytes());
	}

	static boolean isResourceUnavailable(String diagnostics) {
		String lowerCase = diagnostics.toLowerCase(java.util.Locale.ROOT);
		return lowerCase.contains("connection refused")
				|| lowerCase.contains("connection timed out")
				|| lowerCase.contains("operation timed out")
				|| lowerCase.contains("network is unreachable")
				|| lowerCase.contains("no route to host")
				|| lowerCase.contains("temporary failure in name resolution")
				|| lowerCase.contains("name or service not known")
				|| lowerCase.matches("(?s).*(server returned|http error)\\s+[45][0-9]{2}.*");
	}

	public boolean isAvailable() {
		try {
			Process process = new ProcessBuilder(executable, "-version").start();
			if (!process.waitFor(5, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return false;
			}
			return process.exitValue() == 0;
		} catch (IOException exception) {
			return false;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	VideoMetadata parse(byte[] output) {
		JsonNode root;
		try {
			root = objectMapper.readTree(output);
		} catch (IOException exception) {
			throw invalid("ffprobe returned malformed JSON", exception);
		}
		if (root == null || !root.isObject()) {
			throw invalid("ffprobe returned an invalid response");
		}

		JsonNode durationNode = root.path("format").path("duration");
		BigDecimal durationSeconds;
		try {
			durationSeconds = new BigDecimal(durationNode.asText());
		} catch (NumberFormatException exception) {
			throw invalid("The video has no valid duration", exception);
		}
		if (durationSeconds.signum() <= 0) {
			throw invalid("The video duration must be greater than zero");
		}
		long durationMilliseconds;
		try {
			durationMilliseconds = durationSeconds
					.multiply(BigDecimal.valueOf(1_000))
					.setScale(0, java.math.RoundingMode.CEILING)
					.longValueExact();
		} catch (ArithmeticException exception) {
			throw invalid("The video duration is outside the supported range", exception);
		}

		JsonNode streams = root.path("streams");
		if (!streams.isArray()) {
			throw invalid("ffprobe returned no streams");
		}
		for (JsonNode stream : streams) {
			if (!"video".equals(stream.path("codec_type").asText())) {
				continue;
			}
			int width = stream.path("width").asInt(0);
			int height = stream.path("height").asInt(0);
			if (width <= 0 || height <= 0) {
				throw invalid("The video dimensions are invalid");
			}
			String codec = stream.path("codec_name").asText("");
			if (codec.isBlank()) {
				throw invalid("The video codec is missing");
			}
			return new VideoMetadata(durationMilliseconds, width, height, codec);
		}
		throw invalid("The resource contains no video stream");
	}

	private static void validateUrl(URI url) {
		if (url == null || url.getHost() == null || url.getUserInfo() != null
				|| !("http".equalsIgnoreCase(url.getScheme()) || "https".equalsIgnoreCase(url.getScheme()))) {
			throw unavailable("The internal presigned URL is missing or invalid", null);
		}
	}

	private static void joinReaders(Thread... readers) throws InterruptedException {
		for (Thread reader : readers) {
			reader.join(READ_JOIN_MILLIS);
			if (reader.isAlive()) {
				reader.interrupt();
				throw unavailable("ffprobe output reader did not terminate", null);
			}
		}
	}

	private static VideoProbeException unavailable(String message, Throwable cause) {
		return cause == null
				? new VideoProbeException(VideoProbeException.Outcome.VIDEO_PROBE_UNAVAILABLE, message)
				: new VideoProbeException(VideoProbeException.Outcome.VIDEO_PROBE_UNAVAILABLE, message, cause);
	}

	private static VideoProbeException invalid(String message) {
		return new VideoProbeException(VideoProbeException.Outcome.VIDEO_INVALID, message);
	}

	private static VideoProbeException invalid(String message, Throwable cause) {
		return new VideoProbeException(VideoProbeException.Outcome.VIDEO_INVALID, message, cause);
	}

	private static final class CapturedStream {
		private final InputStream input;
		private final int limit;
		private final ByteArrayOutputStream output = new ByteArrayOutputStream();
		private volatile IOException failure;
		private volatile boolean exceededLimit;

		private CapturedStream(InputStream input, int limit) {
			this.input = input;
			this.limit = limit;
		}

		private void read() {
			byte[] buffer = new byte[8_192];
			try (input) {
				int count;
				while ((count = input.read(buffer)) != -1) {
					int remaining = limit - output.size();
					if (remaining > 0) {
						output.write(buffer, 0, Math.min(count, remaining));
					}
					if (count > remaining) {
						exceededLimit = true;
					}
				}
			} catch (IOException exception) {
				failure = exception;
			}
		}

		private byte[] bytes() {
			return output.toByteArray();
		}

		private String text() {
			return output.toString(StandardCharsets.UTF_8);
		}

		private IOException failure() {
			return failure;
		}

		private boolean exceededLimit() {
			return exceededLimit;
		}
	}
}
