package com.turkcell.mayacore.ticketing.service;

import com.turkcell.mayacore.ticketing.domain.AuditLog;
import com.turkcell.mayacore.ticketing.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void log(Long actorId, String action, String resourceType, Long resourceId,
                    String ip, String userAgent) {
        AuditLog entry = new AuditLog();
        entry.setActorId(actorId);
        entry.setAction(action);
        entry.setResourceType(resourceType);
        entry.setResourceId(resourceId);
        entry.setIp(ip);
        entry.setUserAgent(userAgent);
        auditLogRepository.save(entry);
    }
}
