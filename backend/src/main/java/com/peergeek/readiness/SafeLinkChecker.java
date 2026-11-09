package com.peergeek.readiness;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import com.peergeek.config.ReadinessSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SafeLinkChecker {
	private static final Logger LOGGER = LoggerFactory.getLogger(SafeLinkChecker.class);
	private static final int MAX_HEADER_BYTES = 16 * 1024;
	private final ReadinessSettings settings;
	private final LinkCheckStore store;
	private final Semaphore globalSlots;
	private final Map<String, Semaphore> hostSlots = new ConcurrentHashMap<>();

	public SafeLinkChecker(ReadinessSettings settings, LinkCheckStore store) {
		this.settings = settings;
		this.store = store;
		this.globalSlots = new Semaphore(settings.getLinks().getMaxConcurrent());
	}

	public Result check(long resourceId, String value) {
		if (value == null || value.isBlank()) return new Result(Outcome.INVALID, null);
		String hash = sha256(value);
		var fresh = store.findFresh(resourceId, hash,
				Instant.now().minus(settings.getLinks().getCacheTtl()));
		if (fresh.isPresent()) {
			var cached = fresh.get();
			return new Result(Outcome.valueOf(cached.outcome()), cached.httpStatus());
		}
		Result result;
		long deadline = System.nanoTime() + settings.getLinks().getTotalTimeout().toNanos();
		boolean acquired = false;
		try {
			acquired = globalSlots.tryAcquire(remainingMillis(deadline), TimeUnit.MILLISECONDS);
			result = acquired ? fetchWithRedirects(URI.create(value), deadline)
					: new Result(Outcome.UNAVAILABLE, null);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			result = new Result(Outcome.UNAVAILABLE, null);
		} catch (IllegalArgumentException exception) {
			result = new Result(Outcome.INVALID, null);
		} catch (Exception exception) {
			LOGGER.info("External link check did not complete resourceId={} exceptionType={}",
					resourceId, exception.getClass().getSimpleName());
			result = new Result(exception instanceof UnsafeLinkException ? Outcome.UNSAFE : Outcome.UNAVAILABLE, null);
		} finally {
			if (acquired) globalSlots.release();
		}

		store.save(resourceId, hash, result.outcome().name(), result.httpStatus());
		return result;
	}

	private Result fetchWithRedirects(URI initial, long deadline) throws IOException, InterruptedException {
		URI current = initial;
		for (int redirects = 0; ; redirects++) {
			InetAddress[] pinnedAddresses = validateUri(current);
			String host = current.getHost().toLowerCase(Locale.ROOT);
			Semaphore hostSlot = hostSlots.computeIfAbsent(host,
					ignored -> new Semaphore(settings.getLinks().getMaxConcurrentPerHost()));
			if (!hostSlot.tryAcquire(remainingMillis(deadline), TimeUnit.MILLISECONDS)) {
				throw new java.net.SocketTimeoutException("Link-check host limit timed out.");
			}
			Response response;
			try {
				response = fetchPinned(current, pinnedAddresses, "HEAD", deadline);
				if (response.status() == 405 || response.status() == 501) {
					response = fetchPinned(current, pinnedAddresses, "GET", deadline);
				}
			} finally {
				hostSlot.release();
			}
			if (response.status() >= 300 && response.status() <= 399) {
				String location = response.headers().get("location");
				if (location == null || redirects >= settings.getLinks().getMaxRedirects()) {
					return new Result(Outcome.BROKEN, response.status());
				}
				current = current.resolve(location);
				continue;
			}
			return new Result(response.status() >= 200 && response.status() < 400
					? Outcome.AVAILABLE : Outcome.BROKEN, response.status());
		}
	}

	private InetAddress[] validateUri(URI uri) throws IOException {
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		if ((!scheme.equals("http") && !scheme.equals("https")) || uri.getHost() == null
				|| uri.getUserInfo() != null || uri.getFragment() != null || uri.getHost().length() > 253) {
			throw new UnsafeLinkException();
		}
		int port = uri.getPort() == -1 ? (scheme.equals("https") ? 443 : 80) : uri.getPort();
		if (!settings.getLinks().getAllowedPorts().contains(port)) throw new UnsafeLinkException();
		InetAddress[] addresses = resolve(uri.getHost());
		for (InetAddress address : addresses) {
			if (!isAddressAllowed(uri.getHost(), address)) throw new UnsafeLinkException();
		}
		return addresses;
	}

	private Response fetchPinned(URI uri, InetAddress[] addresses, String method, long deadline) throws IOException {
		String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
		String host = uri.getHost();
		int port = uri.getPort() == -1 ? (scheme.equals("https") ? 443 : 80) : uri.getPort();
		IOException lastFailure = null;
		for (InetAddress address : addresses) {
			try (Socket socket = new Socket()) {
				int timeout = Math.min(Math.toIntExact(settings.getLinks().getConnectTimeout().toMillis()),
						remainingMillis(deadline));
				socket.connect(new InetSocketAddress(address, port), timeout);
				socket.setSoTimeout(remainingMillis(deadline));
				Socket connected = scheme.equals("https") ? tlsSocket(socket, host, port) : socket;
				try (connected) {
					OutputStream output = connected.getOutputStream();
					String path = uri.getRawPath();
					if (path == null || path.isEmpty()) path = "/";
					if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();
					String hostHeader = hostHeader(host, port, scheme);
					output.write((method + " " + path + " HTTP/1.1\r\nHost: " + hostHeader
							+ "\r\nUser-Agent: PeerGeek-Link-Check/1.0\r\n"
							+ (method.equals("GET") ? "Range: bytes=0-0\r\n" : "")
							+ "Connection: close\r\n\r\n")
							.getBytes(StandardCharsets.US_ASCII));
					output.flush();
					return readResponse(connected);
				}
			} catch (IOException exception) {
				lastFailure = exception;
			}
		}
		throw lastFailure == null ? new IOException("Unable to connect to URL host.") : lastFailure;
	}

	private int remainingMillis(long deadline) throws java.net.SocketTimeoutException {
		long remaining = deadline - System.nanoTime();
		if (remaining <= 0) throw new java.net.SocketTimeoutException("Link check exceeded its total timeout.");
		return Math.toIntExact(Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
	}

	private Socket tlsSocket(Socket socket, String host, int port) throws IOException {
		SSLSocket secure = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
				.createSocket(socket, host, port, true);
		var parameters = secure.getSSLParameters();
		parameters.setEndpointIdentificationAlgorithm("HTTPS");
		if (!isIpLiteral(host)) parameters.setServerNames(java.util.List.of(new SNIHostName(host)));
		secure.setSSLParameters(parameters);
		secure.startHandshake();
		return secure;
	}

	private Response readResponse(Socket socket) throws IOException {
		BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
		int consumed = 0;
		String statusLine = readLine(input);
		consumed += statusLine.length() + 2;
		String[] statusParts = statusLine.split(" ", 3);
		if (statusParts.length < 2 || !statusParts[0].startsWith("HTTP/")) {
			throw new IOException("Invalid HTTP response.");
		}
		int status;
		try {
			status = Integer.parseInt(statusParts[1]);
		} catch (NumberFormatException exception) {
			throw new IOException("Invalid HTTP status.", exception);
		}
		Map<String, String> headers = new java.util.HashMap<>();
		while (true) {
			String line = readLine(input);
			consumed += line.length() + 2;
			if (consumed > MAX_HEADER_BYTES) throw new IOException("HTTP response headers are too large.");
			if (line.isEmpty()) break;
			int separator = line.indexOf(':');
			if (separator > 0) {
				headers.put(line.substring(0, separator).trim().toLowerCase(Locale.ROOT),
						line.substring(separator + 1).trim());
			}
		}
		return new Response(status, Map.copyOf(headers));
	}

	private String readLine(BufferedInputStream input) throws IOException {
		ByteArrayOutputStream line = new ByteArrayOutputStream();
		for (int next; (next = input.read()) != -1; ) {
			if (next == '\n') {
				byte[] bytes = line.toByteArray();
				int length = bytes.length > 0 && bytes[bytes.length - 1] == '\r' ? bytes.length - 1 : bytes.length;
				return new String(bytes, 0, length, StandardCharsets.ISO_8859_1);
			}
			line.write(next);
			if (line.size() > MAX_HEADER_BYTES) throw new IOException("HTTP response line is too large.");
		}
		throw new IOException("Incomplete HTTP response.");
	}

	InetAddress[] resolve(String host) throws UnknownHostException {
		return InetAddress.getAllByName(host);
	}

	boolean isAddressAllowed(String host, InetAddress address) throws UnknownHostException {
		return settings.getLinks().isAllowPrivateHosts() || isPublic(address);
	}

	private boolean isPublic(InetAddress address) throws UnknownHostException {
		if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
				|| address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
		byte[] bytes = address.getAddress();
		if (bytes.length == 4) {
			int a = Byte.toUnsignedInt(bytes[0]);
			int b = Byte.toUnsignedInt(bytes[1]);
			int c = Byte.toUnsignedInt(bytes[2]);
			return !(a == 0 || a == 10 || a == 127 || a >= 224
					|| (a == 100 && b >= 64 && b <= 127)
					|| (a == 169 && b == 254)
					|| (a == 172 && b >= 16 && b <= 31)
					|| (a == 192 && (b == 0 || b == 168))
					|| (a == 192 && b == 0 && c == 2)
					|| (a == 198 && (b == 18 || b == 19 || b == 51))
					|| (a == 203 && b == 0 && c == 113));
		}
		if (bytes.length == 16) {
			int first = Byte.toUnsignedInt(bytes[0]);
			int second = Byte.toUnsignedInt(bytes[1]);
			boolean mappedIpv4 = true;
			for (int index = 0; index < 10; index++) mappedIpv4 &= bytes[index] == 0;
			mappedIpv4 &= bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
			if (mappedIpv4) {
				return isPublic(InetAddress.getByAddress(java.util.Arrays.copyOfRange(bytes, 12, 16)));
			}
			boolean globalUnicast = (first & 0xe0) == 0x20;
			return globalUnicast && !(first == 0xfc || first == 0xfd
					|| (first == 0xfe && (second & 0xc0) == 0x80)
					|| (first == 0x20 && second == 0x01 && Byte.toUnsignedInt(bytes[2]) == 0x0d
							&& Byte.toUnsignedInt(bytes[3]) == 0xb8)
					|| (first == 0x20 && second == 0x01 && Byte.toUnsignedInt(bytes[2]) == 0
							&& Byte.toUnsignedInt(bytes[3]) == 0)
					|| (first == 0x20 && second == 0x02));
		}
		return false;
	}

	private boolean isIpLiteral(String host) {
		return host.indexOf(':') >= 0 || host.matches("[0-9.]+");
	}

	private String hostHeader(String host, int port, String scheme) {
		String formattedHost = host.indexOf(':') >= 0 ? "[" + host + "]" : host;
		int defaultPort = scheme.equals("https") ? 443 : 80;
		return port == defaultPort ? formattedHost : formattedHost + ":" + port;
	}

	private String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	public record Result(Outcome outcome, Integer httpStatus) {
	}

	public enum Outcome {
		AVAILABLE,
		BROKEN,
		UNSAFE,
		UNAVAILABLE,
		INVALID
	}

	private record Response(int status, Map<String, String> headers) {
	}

	private static final class UnsafeLinkException extends IOException {
	}
}
