package com.pics2text.api.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pics2text.api.infrastructure.persistence.AuditLogEntity;
import com.pics2text.api.infrastructure.persistence.AuditLogMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AuditQueryService {

    private final AuditLogMapper auditLogMapper;

    public AuditQueryService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public List<AuditLogEntity> latest() {
        return auditLogMapper.selectList(new LambdaQueryWrapper<AuditLogEntity>()
                .orderByDesc(AuditLogEntity::getCreatedAt)
                .last("LIMIT 200"));
    }
}
