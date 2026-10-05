package com.cycleofsoftwaredev.administration.domain;

import java.util.List;

public interface AuditLogRepository {

    void append(AuditEntry entry);

    List<AuditEntry> findAll();
}
