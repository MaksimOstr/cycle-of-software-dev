package com.cycleofsoftwaredev.administration.domain;

import java.util.List;

public interface AuditLogRepository {

    AuditEntry save(AuditEntry entry);

    List<AuditEntry> findAll();
}
