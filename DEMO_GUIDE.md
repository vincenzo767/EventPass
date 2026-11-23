# EventPass — Demonstration Guide (3–5 minutes)

A script for a live walkthrough or the demo video. It uses the seeded demo data, so nothing needs to be set up by hand.

## Before you start (1 minute, off camera)

| | |
|---|---|
| App URL | Deployed URL, or `http://localhost:5173` locally (see README §13) |
| Organizer | `organizer` / `EventPass#2026` |
| Door staff | `door` / `DoorStaff#2026` |

- **Phone** (or a browser in phone mode, F12 → device toolbar → 390 px): the attendee screens.
- **Second window or device:** the staff screens.
- **Event-day check:** Cebu Dev Meetup 2026 must be dated **today**. Rolling demo dates keep it there automatically; otherwise use *Events → Edit*.
- **Fresh data (optional):** see README §16 "Reset the demo data".
- **Have a second ticket code from another event ready.** Sign in as organizer → *Events → Mactan Cloud & AI Summit → Attendees*, and copy any ticket code.

---

## Level 1 — core flow (≈ 2½ minutes)

1. **Open the app** (phone). "These are the upcoming events, all in Cebu time with AM/PM." Point out **"12 of 30 spots left"** on Cebu Dev Meetup 2026 and the red **Full** badge on Mactan Cloud & AI Summit.
2. **Open Cebu Dev Meetup 2026.** Show the date, time, venue and description, then tap **Register**.
3. **Show validation.** Submit with email `juan@invalid` and mobile `0917123456`. Both fields show errors next to them. Fix them: `juan.demo@example.com`, `09171234567`.
4. **Register** to get the **digital ticket**: event, date, time, venue, attendee name, ticket code and QR code.
   - *Optional:* scan the QR with the phone's camera app; it shows the same ticket code.
   - Note: "The QR contains only the ticket code, no personal data."
5. **Retrieve the ticket again.** Refresh the page (the ticket reloads). Then open **Find my ticket**, enter the code and the email, and the ticket appears.
   - Change one letter of the email: **"Ticket not found."**, with no details shown.
6. **Duplicate registration.** Register again with the same email: "You are already registered."
7. **Staff sign-in.** In a new window, open `/staff/check-in` while signed out. You are sent to the sign-in page. Sign in as **door**.
8. **Open the check-in screen** for Cebu Dev Meetup 2026. Point out the **live counter**, e.g. "4 / 19 checked in".
9. **Check in the ticket** (type the code, or **Start camera** and scan the phone): green **Checked in** with the attendee's name. The counter goes up.
10. **Scan or enter it again:** yellow **Already checked in**, with the original time.
11. **Invalid tickets.** Enter `CDM-000001` → red **Invalid ticket**. Enter the Mactan ticket code you copied earlier → also **Invalid ticket**, because it belongs to a different event.
12. **Attendee list** (*Events → Cebu Dev Meetup 2026*): **Registered / capacity** and **Checked in / registered** totals, with search by name or ticket code.

## Level 2 (≈ 1 minute) — sign in as **organizer**

- **Full event and waitlist.** On the phone, open Mactan Cloud & AI Summit. **Register** is disabled, so tap **Join the waitlist** and register. The page shows "You are #4 on the waitlist" and no QR code. At the door, entering that code shows **On waitlist — not confirmed**.
- **Cancellation.** In the summit's attendee list, **Cancel** one confirmed attendee (confirm the dialog). Spots left goes from 0 to 1, and scanning that ticket now shows **Cancelled**.
- **Promotion.** Click **Promote next in line**. The first waitlisted person gets the seat and a confirmed ticket.
- **Dashboard.** Show capacity, confirmed, checked in, no-shows (shown only after the event starts), waitlisted and cancelled. Mention the CSV export (opens in Excel) and **Undo check-in** in the attendee list, which is recorded in the audit log.

## Level 3 (≈ 1 minute)

- **Camera scanning** (on the deployed HTTPS site, on a phone). Tap **Start camera** and scan a ticket from another screen. The result appears over the camera for about 2 seconds, then the scanner is ready again. Holding the same QR in view is not checked twice.
- **Secure ticket validation.** Codes are about 50 bits of secure randomness, so `CDM-000001` or an edited code is never valid. Lookups and check-ins are rate-limited.
- **Concurrency.** Show `ConcurrencyIntegrationTest` (`cd backend && mvn test -Dtest=ConcurrencyIntegrationTest`). Sixteen simultaneous requests for the last seat produce exactly one confirmation, and sixteen simultaneous scans of one ticket produce exactly one "Checked in". This works through PostgreSQL row locks (README §10).
- **Roles.** Signed in as **door**, there is no *New event* button, and opening `/staff/events/new` shows "Organizer access required". The API returns 403 as well. As organizer, show the **Audit log**: check-ins, undo, cancellation and promotion, each with who and when.
- **Email** (if the `MAIL_*` sandbox is configured). Show the confirmation and promotion emails, with their QR and ticket link, in the Mailtrap or Ethereal inbox.
