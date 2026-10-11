package com.cycleofsoftwaredev.administration.infrastructure;

import com.cycleofsoftwaredev.administration.domain.AuditEntry;
import com.cycleofsoftwaredev.administration.domain.AuditLogRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** PostgreSQL storage of the audit log. */
public interface JpaAuditLogRepository extends JpaRepository<AuditEntry, UUID>, AuditLogRepository {

    /** The log only grows, so the page is cut by the database using the index on occurred_at. */
    @Override
    @Query(value = "SELECT * FROM administration.audit_log ORDER BY occurred_at DESC, id LIMIT :limit", nativeQuery = true)
    List<AuditEntry> findLatest(@Param("limit") int limit);
}
