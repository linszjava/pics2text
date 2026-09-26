package com.pics2text.api.application;

import com.pics2text.api.common.RequestIdFilter;
import com.pics2text.api.infrastructure.persistence.AuditLogEntity;
import com.pics2text.api.infrastructure.persistence.AuditLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditLogMapper auditLogMapper;
    private final HttpServletRequest request;

    public AuditService(AuditLogMapper auditLogMapper, HttpServletRequest request) {
        this.auditLogMapper = auditLogMapper;
        this.request = request;
    }

    public void record(UUID actorUserId, String action, String targetType, String targetId, boolean success) {
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        auditLogMapper.insert(new AuditLogEntity(
                actorUserId,
                action,
                targetType,
                targetId,
                success,
                requestId == null ? null : requestId.toString(),
                clientIp(),
                "{}",
                Instant.now()));
    }

    private String clientIp() {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String first = forwardedFor.split(",", 2)[0].trim();
            return first.length() <= 64 ? first : first.substring(0, 64);
        }
        return request.getRemoteAddr();
    }
}
