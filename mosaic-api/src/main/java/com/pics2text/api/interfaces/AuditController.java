package com.pics2text.api.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.pics2text.api.application.AuditQueryService;
import com.pics2text.api.infrastructure.persistence.AuditLogEntity;
import com.pics2text.identity.domain.Permission;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AuditController {

    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping
    public List<AuditResponse> latest() {
        StpUtil.checkPermission(Permission.AUDIT_READ.code());
        return auditQueryService.latest().stream().map(AuditResponse::from).toList();
    }

    public record AuditResponse(
            long id,
            UUID actorUserId,
            String action,
            String targetType,
            String targetId,
            boolean success,
            String requestId,
            Instant createdAt) {

        static AuditResponse from(AuditLogEntity entity) {
            return new AuditResponse(
                    entity.getId(),
                    entity.getActorUserId(),
                    entity.getAction(),
                    entity.getTargetType(),
                    entity.getTargetId(),
                    entity.getSuccess(),
                    entity.getRequestId(),
                    entity.getCreatedAt());
        }
    }
}
