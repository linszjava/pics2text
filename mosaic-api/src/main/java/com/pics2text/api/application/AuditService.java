package com.pics2text.api.application;

import com.pics2text.api.common.RequestIdFilter;
import com.pics2text.api.infrastructure.persistence.AuditLogEntity;
import com.pics2text.api.infrastructure.persistence.AuditLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {

    private final AuditLogMapper auditLogMapper;

    public AuditService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public void record(UUID actorUserId, String action, String targetType, String targetId, boolean success) {
        HttpServletRequest request = currentRequest();
        Object requestId = request == null ? null : request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        auditLogMapper.insert(new AuditLogEntity(
                actorUserId,
                action,
                targetType,
                targetId,
                success,
                requestId == null ? null : requestId.toString(),
                clientIp(request),
                "{}",
                Instant.now()));
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) return null;
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String first = forwardedFor.split(",", 2)[0].trim();
            return first.length() <= 64 ? first : first.substring(0, 64);
        }
        return request.getRemoteAddr();
    }
}
