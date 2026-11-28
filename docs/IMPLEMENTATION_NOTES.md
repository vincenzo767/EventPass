# Implementation notes

The key design decisions and the reasons for them, for developers maintaining EventPass.

## Architecture

- **One deployable, one origin.** Vite builds the SPA, which is packaged into the Spring Boot jar under `static/`. `SpaController` forwards client-side routes to `index.html`. Serving from the same origin allows HttpOnly session cookies and a simple CSRF scheme, with no CORS configuration and no tokens in `localStorage`.
- **Feature packages** (`event`, `registration`, `checkin`, `staff`, `audit`, `notification`, `demo`) rather than technical layers. Each feature has a controller, a service, a repository and DTOs. Registration is split into `RegistrationService` (attendee-facing) and `StaffRegistrationService` (staff-facing) to avoid one giant service.
- **The entity state machine lives in `Registration`.** `checkIn`, `undoCheckIn`, `promote` and `cancel` refuse invalid transitions. Services translate expected failures into user-facing `ApiException`s *before* calling them, and `RegistrationRules` holds those checks.
- **`Clock` is injected everywhere.** The event-day rule, no-shows and the ended state are tested by moving a `MutableClock`.

## Data integrity

- Seat counting happens inside `SELECT … FOR UPDATE` on the event row. The alternatives were:
  - a `seats_taken` counter column with a CHECK constraint, which still needs care when cancelling and promoting;
  - SERIALIZABLE isolation, which needs retry logic.

  The pessimistic event lock is simple, has a short critical section, and contention per event is low.
- The partial unique index `(event_id, email) WHERE status <> 'CANCELLED'` implements "one active registration per email". It also lets people re-register after cancelling.
- **Locks are taken before entities are loaded.** Hibernate does not refresh an already-managed entity when a locking query returns it. Cancellation therefore resolves ids first (`findEventIdById`, `findIdByAccessToken`), then locks the event, then locks the registration.
- Audit entries use `Propagation.MANDATORY`, so they always commit or roll back together with the action they describe.

## Tickets

- Ticket code (`PREFIX-XXXXX-XXXXX`) is for humans and the QR. Access token (256-bit) is for links. Keeping them separate means a ticket link never exposes the code-plus-email lookup, and the code alone is never enough to view personal data.
- QR images are rendered on the server with ZXing, so the web page and the email use the same image. The integration test decodes the served PNG to prove its content.

## Demo data

- `DemoDataSeeder` runs only on an empty database.
- `DemoDateRoller` moves only events created by the seeder (identified by `demo_key`), and only after they have ended. That keeps "today's" Cebu Dev Meetup demonstrable without touching staff-created events.

## Testing approach

- Integration tests run against a **real PostgreSQL 17** server through zonky embedded-postgres. Locking, partial indexes and `timestamptz` behave exactly as in production, and no Docker is needed (Docker wasn't available on the development machine). CI on GitHub Actions runs the same suite.
- `mvn spring-boot:test-run` starts the same embedded database for local development (`DevEventPassApplication`, test scope only).
- Playwright tests create their own events through the organizer API, so they can be run repeatedly. The test that registers for the seeded Cebu Dev Meetup skips itself, with a message, if a reused database has filled the event.

## Known issues / follow-ups

- Open an exported CSV in Microsoft Excel itself (the format follows Excel's conventions and was checked programmatically).
- Move rate-limit counters and sessions to a shared store if the app is ever scaled to more than one instance.
