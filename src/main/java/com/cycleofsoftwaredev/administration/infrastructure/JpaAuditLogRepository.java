package com.cycleofsoftwaredev.administration.infrastructure;

import com.cycleofsoftwaredev.administration.domain.AuditEntry;
import com.cycleofsoftwaredev.administration.domain.AuditLogRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** PostgreSQL storage of the audit log. */
public interface JpaAuditLogRepository extends JpaRepository<AuditEntry, UUID>, AuditLogRepository {
}
