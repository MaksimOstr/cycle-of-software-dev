package com.cycleofsoftwaredev.web;

import com.cycleofsoftwaredev.administration.api.DeliverySettings;
import com.cycleofsoftwaredev.administration.application.AuditLogService;
import com.cycleofsoftwaredev.administration.application.SalesReport;
import com.cycleofsoftwaredev.administration.application.SalesReportService;
import com.cycleofsoftwaredev.administration.application.SettingsService;
import com.cycleofsoftwaredev.administration.domain.AuditEntry;
import com.cycleofsoftwaredev.shared.domain.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Administrator endpoints: store settings, audit log and sales dashboard. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdministrationController {

    private final SettingsService settings;
    private final AuditLogService auditLog;
    private final SalesReportService salesReports;
    private final CurrentUserResolver currentUser;

    public AdministrationController(SettingsService settings, AuditLogService auditLog,
                                    SalesReportService salesReports, CurrentUserResolver currentUser) {
        this.settings = settings;
        this.auditLog = auditLog;
        this.salesReports = salesReports;
        this.currentUser = currentUser;
    }

    public record DeliverySettingsRequest(@NotNull @DecimalMin("0") BigDecimal courierPrice,
                                          @NotNull @DecimalMin("0") BigDecimal novaPoshtaPrice,
                                          @NotNull @DecimalMin("0") BigDecimal freeShippingThreshold) {
    }

    @GetMapping("/settings/delivery")
    public DeliverySettings deliverySettings(@RequestHeader(value = CurrentUserResolver.HEADER, required = false) UUID userId) {
        currentUser.requireAdministrator(userId);
        return settings.deliverySettings();
    }

    @PutMapping("/settings/delivery")
    public DeliverySettings updateDeliverySettings(@RequestHeader(value = CurrentUserResolver.HEADER, required = false) UUID userId,
                                                   @Valid @RequestBody DeliverySettingsRequest request) {
        currentUser.requireAdministrator(userId);
        settings.updateDeliverySettings(new DeliverySettings(Money.of(request.courierPrice()),
                Money.of(request.novaPoshtaPrice()), Money.of(request.freeShippingThreshold())));
        return settings.deliverySettings();
    }

    @GetMapping("/audit-log")
    public List<AuditEntry> auditLog(@RequestHeader(value = CurrentUserResolver.HEADER, required = false) UUID userId,
                                     @RequestParam(defaultValue = "50") int limit) {
        currentUser.requireAdministrator(userId);
        return auditLog.latestEntries(limit);
    }

    @GetMapping("/sales")
    public SalesReport sales(@RequestHeader(value = CurrentUserResolver.HEADER, required = false) UUID userId,
                             @RequestParam Instant from, @RequestParam Instant to) {
        currentUser.requireAdministrator(userId);
        return salesReports.report(from, to);
    }
}
