package com.cubetech.eventpass.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.cubetech.eventpass.checkin.CheckInResponse;
import com.cubetech.eventpass.checkin.CheckInResult;
import com.cubetech.eventpass.checkin.CheckInService;
import com.cubetech.eventpass.common.ApiException;
import com.cubetech.eventpass.event.Event;
import com.cubetech.eventpass.registration.RegistrationDtos.RegistrationRequest;
import com.cubetech.eventpass.registration.RegistrationDtos.TicketResponse;
import com.cubetech.eventpass.registration.RegistrationService;
import com.cubetech.eventpass.registration.RegistrationStatus;
import com.cubetech.eventpass.registration.StaffRegistrationService;
import com.cubetech.eventpass.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Fires many simultaneous requests at the real PostgreSQL database to prove that row locking keeps
 * capacity and check-in state consistent. Each scenario releases all threads at the same instant.
 */
class ConcurrencyIntegrationTest extends IntegrationTest {

    private static final int THREADS = 16;

    @Autowired
    private RegistrationService registrationService;
    @Autowired
    private CheckInService checkInService;
    @Autowired
    private StaffRegistrationService staffRegistrationService;

    /** Runs all tasks at the same moment and returns each one's result or thrown exception. */
    private <T> List<Object> race(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        return task.call();
                    } catch (Exception ex) {
                        return ex;
                    }
                }));
            }
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private RegistrationRequest request(int i, boolean joinWaitlist) {
        return new RegistrationRequest("Racer " + i, "racer" + i + "@example.com", "09171234567", joinWaitlist);
    }

    @RepeatedTest(3)
    void onlyOneOfManySimultaneousRegistrationsGetsTheLastSeat() throws Exception {
        Event event = todayEvent("Last seat", 5);
        for (int i = 0; i < 4; i++) {
            register(event.getId(), "Early " + i, "early" + i + "@example.com");
        }

        List<Callable<TicketResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            int n = i;
            tasks.add(() -> registrationService.register(event.getId(), request(n, false)));
        }
        List<Object> results = race(tasks);

        long confirmed = results.stream().filter(TicketResponse.class::isInstance).count();
        long full = results.stream().filter(r -> r instanceof ApiException ex && ex.getCode().equals("EVENT_FULL")).count();
        assertThat(confirmed).isEqualTo(1);
        assertThat(full).isEqualTo(THREADS - 1);
        assertThat(registrations.countByEventIdAndStatusIn(event.getId(), RegistrationStatus.SEAT_HOLDING)).isEqualTo(5);
    }

    @Test
    void simultaneousRegistrationsBeyondCapacityAreWaitlisted() throws Exception {
        Event event = todayEvent("Small room", 3);
        List<Callable<TicketResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            int n = i;
            tasks.add(() -> registrationService.register(event.getId(), request(n, true)));
        }
        List<Object> results = race(tasks);

        assertThat(results).allMatch(TicketResponse.class::isInstance);
        long confirmed = results.stream().map(TicketResponse.class::cast)
                .filter(t -> t.status() == RegistrationStatus.CONFIRMED).count();
        List<Integer> positions = results.stream().map(TicketResponse.class::cast)
                .filter(t -> t.status() == RegistrationStatus.WAITLISTED).map(TicketResponse::waitlistPosition)
                .sorted().toList();
        assertThat(confirmed).isEqualTo(3);
        assertThat(positions).hasSize(THREADS - 3);
        assertThat(registrations.countByEventIdAndStatusIn(event.getId(), RegistrationStatus.SEAT_HOLDING)).isEqualTo(3);
    }

    @Test
    void sameEmailSubmittedTwiceAtOnceCreatesOneRegistration() throws Exception {
        Event event = todayEvent("Meetup", 30);
        List<Callable<TicketResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> registrationService.register(event.getId(),
                    new RegistrationRequest("Double Click", "double@example.com", "09171234567", false)));
        }
        List<Object> results = race(tasks);

        assertThat(results.stream().filter(TicketResponse.class::isInstance).count()).isEqualTo(1);
        assertThat(registrations.count()).isEqualTo(1);
    }

    @RepeatedTest(3)
    void twoDevicesCheckingInSameTicketOnlyOneSucceeds() throws Exception {
        Event event = todayEvent("Meetup", 30);
        String code = register(event.getId(), "Juan", "juan@example.com").get("ticketCode").asText();

        List<Callable<CheckInResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            var device = i % 2 == 0 ? doorStaff : organizer; // two staff members scanning
            tasks.add(() -> checkInService.checkIn(event.getId(), code, device));
        }
        List<Object> results = race(tasks);

        assertThat(results).allMatch(CheckInResponse.class::isInstance);
        List<CheckInResult> outcomes = results.stream().map(r -> ((CheckInResponse) r).result()).toList();
        assertThat(outcomes).filteredOn(r -> r == CheckInResult.CHECKED_IN).hasSize(1);
        assertThat(outcomes).filteredOn(r -> r == CheckInResult.ALREADY_CHECKED_IN).hasSize(THREADS - 1);
        // Everyone who saw "Already checked in" was shown the same, original time.
        assertThat(results.stream().map(r -> ((CheckInResponse) r).checkedInAt()).distinct()).hasSize(1);
    }

    @Test
    void concurrentPromotionsCannotHandOutTheSameSeatTwice() throws Exception {
        Event event = todayEvent("Promo", 2);
        TicketResponse first = registrationService.register(event.getId(), request(100, false));
        registrationService.register(event.getId(), request(101, false));
        for (int i = 0; i < 4; i++) {
            registrationService.register(event.getId(), request(200 + i, true));
        }
        registrationService.cancelByAttendee(first.accessToken()); // exactly one seat free

        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tasks.add(() -> staffRegistrationService.promoteNext(event.getId(), organizer));
        }
        List<Object> results = race(tasks);

        long promoted = results.stream().filter(r -> !(r instanceof Exception)).count();
        assertThat(promoted).isEqualTo(1);
        assertThat(registrations.countByEventIdAndStatusIn(event.getId(), RegistrationStatus.SEAT_HOLDING)).isEqualTo(2);
    }

    @Test
    void cancelAndCheckInRacingLeaveAConsistentState() throws Exception {
        Event event = todayEvent("Race", 10);
        TicketResponse ticket = registrationService.register(event.getId(), request(1, false));

        List<Callable<Object>> tasks = List.of(
                () -> checkInService.checkIn(event.getId(), ticket.ticketCode(), doorStaff),
                () -> registrationService.cancelByAttendee(ticket.accessToken()));
        List<Object> results = race(tasks);

        RegistrationStatus finalStatus = registrationByCode(ticket.ticketCode()).getStatus();
        CheckInResponse checkIn = (CheckInResponse) results.get(0);
        if (finalStatus == RegistrationStatus.CHECKED_IN) {
            assertThat(checkIn.result()).isEqualTo(CheckInResult.CHECKED_IN);
            assertThat(results.get(1)).isInstanceOf(ApiException.class); // cancel refused
        } else {
            assertThat(finalStatus).isEqualTo(RegistrationStatus.CANCELLED);
            assertThat(checkIn.result()).isEqualTo(CheckInResult.CANCELLED);
        }
    }
}
