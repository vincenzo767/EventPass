package com.cubetech.eventpass.registration;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    /** Per-status totals for one or more events. */
    interface StatusCount {
        Long getEventId();

        RegistrationStatus getStatus();

        long getTotal();
    }

    @Query("select r.event.id as eventId, r.status as status, count(r) as total from Registration r "
            + "where r.event.id in :eventIds group by r.event.id, r.status")
    List<StatusCount> countByStatusForEvents(@Param("eventIds") Collection<Long> eventIds);

    long countByEventIdAndStatusIn(Long eventId, Collection<RegistrationStatus> statuses);

    @Query("select count(r) > 0 from Registration r where r.event.id = :eventId and r.email = :email "
            + "and r.status <> com.cubetech.eventpass.registration.RegistrationStatus.CANCELLED")
    boolean existsActiveByEventAndEmail(@Param("eventId") Long eventId, @Param("email") String email);

    boolean existsByTicketCode(String ticketCode);

    @Query("select r from Registration r join fetch r.event where r.ticketCode = :code")
    Optional<Registration> findByTicketCode(@Param("code") String ticketCode);

    @Query("select r from Registration r join fetch r.event where r.accessToken = :token")
    Optional<Registration> findByAccessToken(@Param("token") String accessToken);

    /**
     * Locks one registration row ({@code SELECT ... FOR UPDATE}). Check-in and undo take this lock, so
     * two devices scanning the same ticket at once are serialized and only the first sees CONFIRMED.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Registration r where r.ticketCode = :code")
    Optional<Registration> findByTicketCodeForUpdate(@Param("code") String ticketCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Registration r where r.id = :id")
    Optional<Registration> findByIdForUpdate(@Param("id") Long id);

    /*
     * Id-only lookups. Used before taking row locks so the entity is first loaded by the locking query
     * itself (an entity already in the persistence context would not be refreshed by it).
     */
    @Query("select r.event.id from Registration r where r.id = :id")
    Optional<Long> findEventIdById(@Param("id") Long id);

    @Query("select r.id from Registration r where r.accessToken = :token")
    Optional<Long> findIdByAccessToken(@Param("token") String accessToken);

    /** Earliest waitlisted registration (FIFO by registration time, then id), locked for promotion. */
    @Query(value = "select * from registrations where event_id = :eventId and status = 'WAITLISTED' "
            + "order by registered_at, id limit 1 for update", nativeQuery = true)
    Optional<Registration> findFirstWaitlistedForUpdate(@Param("eventId") Long eventId);

    /** Number of waitlisted registrations ahead of the given one, used for "You are #N on the waitlist". */
    @Query("select count(r) from Registration r where r.event.id = :eventId "
            + "and r.status = com.cubetech.eventpass.registration.RegistrationStatus.WAITLISTED "
            + "and (r.registeredAt < :registeredAt or (r.registeredAt = :registeredAt and r.id < :id))")
    long countWaitlistedAhead(@Param("eventId") Long eventId, @Param("registeredAt") Instant registeredAt,
                              @Param("id") Long id);

    @Query("select r from Registration r where r.event.id = :eventId order by r.registeredAt asc, r.id asc")
    List<Registration> findAllForEvent(@Param("eventId") Long eventId);

    /** Name or ticket-code search. {@code pattern} must already be lower-cased, escaped and wrapped in %. */
    @Query("select r from Registration r where r.event.id = :eventId and "
            + "(lower(r.fullName) like :pattern escape '\\' or lower(r.ticketCode) like :pattern escape '\\') "
            + "order by r.registeredAt asc, r.id asc")
    List<Registration> searchForEvent(@Param("eventId") Long eventId, @Param("pattern") String pattern);
}
