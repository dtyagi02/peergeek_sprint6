package com.peergeek.walletpayment.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.walletpayment.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

@Service
public class RazorpayService {
    private static final Logger log = LoggerFactory.getLogger(RazorpayService.class);
    private static final String DEFAULT_BASE_URL = "https://api.razorpay.com/v1";

    private final RazorpaySettings settings;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private String baseUrl = DEFAULT_BASE_URL;

    @Autowired
    public RazorpayService(RazorpaySettings settings, ObjectMapper objectMapper) {
        this(settings, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    public RazorpayService(RazorpaySettings settings, ObjectMapper objectMapper, HttpClient httpClient) {
        this.settings = settings;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public RazorpayOrder createOrder(long amountPaise, String currency, String receipt, Map<String, String> notes) {
        ensureConfigured();

        try {
            Map<String, Object> requestBody = Map.of(
                    "amount", amountPaise,
                    "currency", currency,
                    "receipt", receipt,
                    "notes", notes != null ? notes : Map.of()
            );
            String json = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/orders"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", getBasicAuthHeader())
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Razorpay order creation failed with HTTP {}: {}", response.statusCode(), response.body());
                throw new BadRequestException("Failed to create Razorpay payment order: " + extractErrorMessage(response.body()));
            }

            JsonNode root = objectMapper.readTree(response.body());
            String orderId = root.path("id").asText();
            String status = root.path("status").asText();
            long amount = root.path("amount").asLong();

            return new RazorpayOrder(orderId, amount, currency, receipt, status);
        } catch (IOException | InterruptedException ex) {
            log.error("Failed to connect to Razorpay API for order creation: {}", ex.getMessage());
            throw new BadRequestException("Payment gateway communication error: " + ex.getMessage());
        }
    }

    public RazorpayPayment fetchPayment(String paymentId) {
        ensureConfigured();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/payments/" + paymentId))
                    .header("Authorization", getBasicAuthHeader())
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Failed to fetch Razorpay payment {}: HTTP {}: {}", paymentId, response.statusCode(), response.body());
                throw new BadRequestException("Failed to verify payment with gateway: " + extractErrorMessage(response.body()));
            }

            JsonNode root = objectMapper.readTree(response.body());
            String id = root.path("id").asText();
            String orderId = root.path("order_id").asText();
            String status = root.path("status").asText();
            long amount = root.path("amount").asLong();
            String currency = root.path("currency").asText();
            boolean captured = root.path("captured").asBoolean();

            return new RazorpayPayment(id, orderId, status, amount, currency, captured);
        } catch (IOException | InterruptedException ex) {
            log.error("Failed to connect to Razorpay API for payment verification: {}", ex.getMessage());
            throw new BadRequestException("Payment gateway communication error: " + ex.getMessage());
        }
    }

    private void ensureConfigured() {
        if (!settings.isConfigured()) {
            throw new BadRequestException("Razorpay payment gateway is not properly configured. Please check API credentials.");
        }
    }

    private String getBasicAuthHeader() {
        String auth = settings.getKeyId() + ":" + settings.getKeySecret();
        return "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    }

    private String extractErrorMessage(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String desc = root.path("error").path("description").asText();
            return (desc != null && !desc.isBlank()) ? desc : responseBody;
        } catch (Exception ex) {
            return responseBody;
        }
    }

    public record RazorpayOrder(
            String id,
            long amount,
            String currency,
            String receipt,
            String status
    ) {}

    public record RazorpayPayment(
            String id,
            String orderId,
            String status,
            long amount,
            String currency,
            boolean captured
    ) {}
}

