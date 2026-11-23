package com.cubetech.eventpass.demo;

import com.cubetech.eventpass.event.EventStatus;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/** The seeded demonstration events. Day offsets are relative to "today" in Cebu time. */
final class DemoEvents {

    static final String CEBU_DEV_MEETUP = "cebu-dev-meetup-2026";

    /**
     * @param dayOffset   days from today; {@code rolling} events are moved forward to keep this offset
     * @param confirmed   seeded seat-holding registrations (including {@code checkedIn})
     * @param checkedIn   how many of the confirmed registrations are already checked in
     * @param waitlisted  seeded waitlisted registrations
     */
    record Definition(String key, String title, String prefix, String venue, String description, int dayOffset,
                      LocalTime start, LocalTime end, int capacity, EventStatus status, int confirmed,
                      int checkedIn, int waitlisted, boolean rolling) {
    }

    static final List<Definition> ALL = List.of(
            new Definition(CEBU_DEV_MEETUP, "Cebu Dev Meetup 2026", "CDM",
                    "Ayala Center Cebu, Function Room 2",
                    "A full day for Cebu's developer community: morning talks on modern web and cloud "
                            + "engineering, hands-on afternoon workshops, and evening lightning talks and networking. "
                            + "Free admission; seats are limited to 30 attendees.",
                    0, LocalTime.of(9, 0), LocalTime.of(21, 0), 30, EventStatus.PUBLISHED, 18, 4, 0, true),
            new Definition("mactan-cloud-ai-summit", "Mactan Cloud & AI Summit", "MCS",
                    "Mövenpick Hotel Mactan Island Cebu, Ballroom B",
                    "An afternoon of practical sessions on deploying AI features on the cloud, from cost "
                            + "control to observability. This event is fully booked; join the waitlist to be "
                            + "notified if a seat opens.",
                    7, LocalTime.of(13, 0), LocalTime.of(18, 0), 12, EventStatus.PUBLISHED, 12, 0, 3, true),
            new Definition("cebu-ux-design-night", "Cebu UX Design Night", "CUX",
                    "The Company Cebu, Cebu IT Park",
                    "Designers and developers share case studies on accessible, mobile-first product design, "
                            + "followed by portfolio reviews.",
                    14, LocalTime.of(18, 0), LocalTime.of(21, 0), 20, EventStatus.PUBLISHED, 19, 0, 0, true),
            new Definition("cebu-startup-pitch-day", "Cebu Startup Pitch Day", "CSP",
                    "Cebu Business Park, Innovation Hub",
                    "Early-stage founders pitch to local investors and mentors. (Draft: not yet visible to attendees.)",
                    30, LocalTime.of(9, 0), LocalTime.of(17, 0), 100, EventStatus.DRAFT, 0, 0, 0, true),
            new Definition("cebu-dev-meetup-2025", "Cebu Dev Meetup 2025", "CDX",
                    "Ayala Center Cebu, Function Room 2",
                    "Last year's edition of the Cebu Dev Meetup.",
                    -365, LocalTime.of(13, 0), LocalTime.of(18, 0), 40, EventStatus.CLOSED, 35, 29, 0, false));

    static Optional<Definition> byKey(String key) {
        return ALL.stream().filter(definition -> definition.key().equals(key)).findFirst();
    }

    static final List<String> FIRST_NAMES = List.of(
            "Andrea", "Miguel", "Patricia", "Joshua", "Kristine", "Carlo", "Bea", "Rafael", "Angelica", "Paolo",
            "Camille", "Jerome", "Danica", "Mark", "Hazel", "Vincent", "Nicole", "Adrian", "Joy", "Gabriel",
            "Trisha", "Kenneth", "Lea", "Jericho", "Mae", "Christian", "Shiela", "Ramon", "Lovely", "Dennis",
            "Ella", "Francis", "Grace", "Iñigo", "Jasmine", "Luis");

    static final List<String> LAST_NAMES = List.of(
            "Villanueva", "Dela Cruz", "Reyes", "Santos", "Bautista", "Gonzales", "Fernandez", "Ramos", "Mendoza",
            "Aquino", "Navarro", "Castillo", "Garcia", "Tan", "Lim", "Uy", "Sy", "Abellana", "Cabahug", "Ouano",
            "Peña", "Alcantara", "Rosales", "Lacson", "Tumulak", "Alburo", "Gullas", "Osmeña");

    private DemoEvents() {
    }
}
