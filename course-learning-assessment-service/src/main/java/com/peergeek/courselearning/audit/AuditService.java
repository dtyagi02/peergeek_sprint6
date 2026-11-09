package com.peergeek.courselearning.audit;

import java.time.Instant;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final AuditRecordWriter writer;

    public AuditService(AuditRecordWriter writer) {
        this.writer = writer;
    }

    public void record(AuditEvent event) {
        HttpServletRequest request = currentRequest();
        writer.write(event, request == null ? null : request.getRemoteAddr(),
                request == null ? null : truncate(request.getHeader("User-Agent"), 512), Instant.now());
    }

    public void recordBestEffort(AuditEvent event) {
        try {
            record(event);
        } catch (RuntimeException exception) {
            log.error("Best-effort audit write failed exceptionType={}", exception.getClass().getName());
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
