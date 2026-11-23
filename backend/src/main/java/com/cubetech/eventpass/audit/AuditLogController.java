package com.cubetech.eventpass.audit;

import com.cubetech.eventpass.audit.AuditService.AuditLogResponse;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/audit-logs")
@PreAuthorize("hasRole('ORGANIZER')")
public class AuditLogController {

    private final AuditService auditService;

    public AuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public AuditLogPage list(@RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "50") int size) {
        Page<AuditLogResponse> result = auditService.list(page, size);
        return new AuditLogPage(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    public record AuditLogPage(List<AuditLogResponse> items, int page, int size, long totalItems, int totalPages) {
    }
}
