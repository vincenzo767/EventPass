package com.cubetech.eventpass.event;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * Loads the event with {@code SELECT ... FOR UPDATE}. Every operation that changes how many seats
     * are taken (register, cancel, promote, capacity edit) takes this lock first, so those operations
     * are serialized per event and the seat count can never exceed capacity.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") Long id);

    @Query("select e from Event e where e.status = com.cubetech.eventpass.event.EventStatus.PUBLISHED "
            + "and e.endsAt > :now order by e.startsAt asc, e.id asc")
    List<Event> findPublicUpcoming(@Param("now") Instant now);

    List<Event> findAllByOrderByStartsAtDescIdDesc();

    List<Event> findByStatusNotOrderByStartsAtDescIdDesc(EventStatus status);

    List<Event> findByDemoKeyIsNotNull();
}
