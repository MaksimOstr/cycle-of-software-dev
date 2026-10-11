package com.cycleofsoftwaredev.administration.domain;

import java.util.List;

public interface AuditLogRepository {

    AuditEntry save(AuditEntry entry);

    /** The newest records first, at most {@code limit} of them. */
    List<AuditEntry> findLatest(int limit);
}
