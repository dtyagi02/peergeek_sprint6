package com.peergeek.gateway.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Component
@Order(-2)
public class GatewayExceptionHandler implements WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status;
        String errorMessage;

        if (isTimeoutException(ex)) {
            status = HttpStatus.GATEWAY_TIMEOUT;
            errorMessage = "Gateway timeout waiting for downstream response";
        } else if (isConnectionException(ex)) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            errorMessage = "Downstream service is currently unavailable";
        } else if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.valueOf(rse.getStatusCode().value());
            errorMessage = rse.getReason() != null ? rse.getReason() : ex.getMessage();
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            errorMessage = "Gateway routing error: " + (ex.getMessage() != null ? ex.getMessage() : "Unknown error");
        }

        log.error("Gateway error for URI {}: [{}] {}", exchange.getRequest().getURI(), status, errorMessage, ex);

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> errorAttributes = new LinkedHashMap<>();
        errorAttributes.put("timestamp", Instant.now().toString());
        errorAttributes.put("status", status.value());
        errorAttributes.put("error", status.getReasonPhrase());
        errorAttributes.put("message", errorMessage);
        errorAttributes.put("path", exchange.getRequest().getPath().value());

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorAttributes);
        } catch (JsonProcessingException e) {
            bytes = ("{\"status\":" + status.value() + ",\"error\":\"" + status.getReasonPhrase() + "\"}").getBytes(StandardCharsets.UTF_8);
        }

        DataBufferFactory bufferFactory = response.bufferFactory();
        DataBuffer buffer = bufferFactory.wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private boolean isConnectionException(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof ConnectException || current.getClass().getName().contains("ConnectException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isTimeoutException(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof TimeoutException || current.getClass().getName().contains("TimeoutException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
