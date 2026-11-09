package com.peergeek.readiness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import com.peergeek.config.ReadinessSettings;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SafeLinkCheckerIntegrationTest {
	private HttpServer server;
	private final Map<String, AtomicInteger> requests = new ConcurrentHashMap<>();
	private int port;

	@BeforeEach
	void startMockServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", this::respond);
		server.start();
		port = server.getAddress().getPort();
	}

	@AfterEach
	void stopMockServer() {
		server.stop(0);
	}

	@Test
	void rejectsRedirectToAForbiddenPrivateAddress() throws Exception {
		SafeLinkChecker checker = checker(host -> switch (host) {
			case "mock.test" -> addresses("127.0.0.1");
			case "forbidden.test" -> addresses("10.1.2.3");
			default -> throw new UnknownHostException(host);
		}, "mock.test");

		SafeLinkChecker.Result result = checker.check(101,
				"http://mock.test:" + port + "/redirect-private");

		assertThat(result.outcome()).isEqualTo(SafeLinkChecker.Outcome.UNSAFE);
		assertThat(requestCount("/redirect-private")).isEqualTo(1);
		assertThat(requestCount("/forbidden")).isZero();
	}

	@Test
	void stopsFollowingRedirectsAfterTheConfiguredLimit() throws Exception {
		ReadinessSettings settings = settings();
		settings.getLinks().setMaxRedirects(2);
		SafeLinkChecker checker = checker(settings, host -> {
			if (host.equals("mock.test")) return addresses("127.0.0.1");
			throw new UnknownHostException(host);
		});

		SafeLinkChecker.Result result = checker.check(102,
				"http://mock.test:" + port + "/chain-one");

		assertThat(result.outcome()).isEqualTo(SafeLinkChecker.Outcome.BROKEN);
		assertThat(result.httpStatus()).isEqualTo(302);
		assertThat(requestCount("/chain-one")).isEqualTo(1);
		assertThat(requestCount("/chain-two")).isEqualTo(1);
		assertThat(requestCount("/chain-three")).isEqualTo(1);
		assertThat(requestCount("/chain-four")).isZero();
	}

	@Test
	void connectsToOnlyTheAddressValidatedBeforeDnsChanges() throws Exception {
		AtomicInteger lookups = new AtomicInteger();
		SafeLinkChecker checker = checker(host -> {
			if (!host.equals("rebind.test")) throw new UnknownHostException(host);
			return lookups.getAndIncrement() == 0
					? addresses("127.0.0.1")
					: addresses("203.0.113.19");
		}, "rebind.test");

		SafeLinkChecker.Result result = checker.check(103,
				"http://rebind.test:" + port + "/document");

		assertThat(result.outcome()).isEqualTo(SafeLinkChecker.Outcome.AVAILABLE);
		assertThat(result.httpStatus()).isEqualTo(200);
		assertThat(lookups).hasValue(1);
		assertThat(requestCount("/document")).isEqualTo(1);
	}

	private SafeLinkChecker checker(AddressLookup lookup, String... localHosts) {
		return checker(settings(), lookup, localHosts);
	}

	private SafeLinkChecker checker(ReadinessSettings settings, AddressLookup lookup) {
		return checker(settings, lookup, "mock.test");
	}

	private SafeLinkChecker checker(
			ReadinessSettings settings, AddressLookup lookup, String... localHosts) {
		settings.getLinks().setAllowedPorts(List.of(port));
		settings.getLinks().setConnectTimeout(Duration.ofSeconds(2));
		settings.getLinks().setTotalTimeout(Duration.ofSeconds(5));
		LinkCheckStore store = store();
		when(store.findFresh(anyLong(), anyString(), any())).thenReturn(java.util.Optional.empty());
		return new SafeLinkChecker(settings, store) {
			@Override
			InetAddress[] resolve(String host) throws UnknownHostException {
				return lookup.resolve(host);
			}

			@Override
			boolean isAddressAllowed(String host, InetAddress address) throws UnknownHostException {
				if (List.of(localHosts).contains(host) && address.isLoopbackAddress()) return true;
				return super.isAddressAllowed(host, address);
			}
		};
	}

	private LinkCheckStore store() {
		return mock(LinkCheckStore.class);
	}

	private ReadinessSettings settings() {
		return new ReadinessSettings();
	}

	private InetAddress[] addresses(String value) throws UnknownHostException {
		return new InetAddress[] {InetAddress.getByName(value)};
	}

	private void respond(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().getPath();
		requests.computeIfAbsent(path, ignored -> new AtomicInteger()).incrementAndGet();
		String redirect = switch (path) {
			case "/redirect-private" -> "http://forbidden.test:" + port + "/forbidden";
			case "/chain-one" -> "/chain-two";
			case "/chain-two" -> "/chain-three";
			case "/chain-three" -> "/chain-four";
			default -> null;
		};
		if (redirect != null) {
			exchange.getResponseHeaders().add("Location", redirect);
			exchange.sendResponseHeaders(302, -1);
		} else {
			exchange.sendResponseHeaders(200, -1);
		}
		exchange.close();
	}

	private int requestCount(String path) {
		AtomicInteger count = requests.get(path);
		return count == null ? 0 : count.get();
	}

	@FunctionalInterface
	private interface AddressLookup {
		InetAddress[] resolve(String host) throws UnknownHostException;
	}
}
