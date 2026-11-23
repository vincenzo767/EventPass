package com.cubetech.eventpass.audit;

import com.cubetech.eventpass.audit.AuditLog.TargetType;
import com.cubetech.eventpass.staff.StaffPrincipal;
import com.cubetech.eventpass.staff.StaffUser;
import com.cubetech.eventpass.staff.StaffUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final StaffUserRepository staffUsers;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, StaffUserRepository staffUsers, Clock clock) {
        this.repository = repository;
        this.staffUsers = staffUsers;
        this.clock = clock;
    }

    /**
     * Records an action inside the caller's transaction, so the audit entry is committed (or rolled
     * back) together with the change it describes.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(StaffPrincipal staff, AuditAction action, TargetType targetType, Long targetId, String details) {
        String safeDetails = details != null && details.length() > 500 ? details.substring(0, 500) : details;
        repository.save(new AuditLog(staff.id(), action, targetType, targetId, safeDetails, Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> list(int page, int size) {
        Page<AuditLog> logs = repository.findAllByOrderByCreatedAtDescIdDesc(
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        Map<Long, StaffUser> staff = staffUsers.findAllById(
                        logs.getContent().stream().map(AuditLog::getStaffUserId).distinct().toList())
                .stream().collect(Collectors.toMap(StaffUser::getId, Function.identity()));
        return logs.map(log -> {
            StaffUser user = staff.get(log.getStaffUserId());
            return new AuditLogResponse(log.getId(), log.getAction(), log.getTargetType(), log.getTargetId(),
                    log.getDetails(), log.getCreatedAt(), log.getStaffUserId(),
                    user == null ? "Unknown" : user.getName(), user == null ? null : user.getUsername());
        });
    }

    public record AuditLogResponse(Long id, AuditAction action, TargetType targetType, Long targetId, String details,
                                   Instant createdAt, Long staffUserId, String staffName, String staffUsername) {
    }
}
