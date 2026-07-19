package com.turkcell.mayacore.ticketing.repository;

import com.turkcell.mayacore.ticketing.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
