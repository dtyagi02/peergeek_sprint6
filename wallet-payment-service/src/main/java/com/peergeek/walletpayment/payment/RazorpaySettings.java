package com.peergeek.walletpayment.payment;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "razorpay")
public class RazorpaySettings {
    private static final Logger log = LoggerFactory.getLogger(RazorpaySettings.class);

    private String keyId;
    private String keySecret;
    private String webhookSecret;
    private boolean enabled = true;

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getKeySecret() {
        return keySecret;
    }

    public void setKeySecret(String keySecret) {
        this.keySecret = keySecret;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isConfigured() {
        return isNotBlank(keyId) && isNotBlank(keySecret);
    }

    public boolean isWebhookConfigured() {
        return isNotBlank(webhookSecret);
    }

    @PostConstruct
    public void init() {
        // 1. Check system environment variables if properties are empty
        if (isBlank(keyId)) {
            keyId = System.getenv("RAZORPAY_KEY_ID");
        }
        if (isBlank(keySecret)) {
            keySecret = System.getenv("RAZORPAY_KEY_SECRET");
        }
        if (isBlank(webhookSecret)) {
            webhookSecret = System.getenv("RAZORPAY_WEBHOOK_SECRET");
        }

        // 2. Fallback: check root / parent .env file
        if (isBlank(keyId) || isBlank(keySecret)) {
            Map<String, String> dotEnv = loadDotEnv();
            if (isBlank(keyId)) keyId = dotEnv.get("RAZORPAY_KEY_ID");
            if (isBlank(keySecret)) keySecret = dotEnv.get("RAZORPAY_KEY_SECRET");
            if (isBlank(webhookSecret)) webhookSecret = dotEnv.get("RAZORPAY_WEBHOOK_SECRET");
        }

        String maskedKey = (isNotBlank(keyId) && keyId.length() > 8)
                ? keyId.substring(0, 8) + "..."
                : (isNotBlank(keyId) ? "configured" : "NOT_SET");

        log.info("Razorpay Payment Gateway startup status: enabled={}, keyId={}, secret=[PROTECTED], webhookSecret=[{}]",
                enabled, maskedKey, isWebhookConfigured() ? "CONFIGURED" : "NOT_CONFIGURED");
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private boolean isNotBlank(String s) {
        return !isBlank(s);
    }

    private Map<String, String> loadDotEnv() {
        Map<String, String> map = new HashMap<>();
        File[] candidateFiles = new File[] {
                new File(".env"),
                new File("../.env"),
                new File("../../.env")
        };
        for (File f : candidateFiles) {
            if (f.exists() && f.isFile()) {
                try {
                    List<String> lines = Files.readAllLines(f.toPath());
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                            int idx = trimmed.indexOf('=');
                            String key = trimmed.substring(0, idx).trim();
                            String val = trimmed.substring(idx + 1).trim();
                            if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                                val = val.substring(1, val.length() - 1);
                            }
                            map.put(key, val);
                        }
                    }
                    log.info("Loaded environment fallback from {}", f.getAbsolutePath());
                    break;
                } catch (Exception ex) {
                    log.warn("Failed to read {}: {}", f.getAbsolutePath(), ex.getMessage());
                }
            }
        }
        return map;
    }
}

