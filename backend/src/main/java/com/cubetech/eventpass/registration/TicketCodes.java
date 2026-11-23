package com.cubetech.eventpass.registration;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Generates and normalizes ticket codes and ticket-page access tokens.
 *
 * <p>Ticket code format: {@code PREFIX-XXXXX-XXXXX}, e.g. {@code CDM-7K3P9-Q2XHM}. The ten random
 * characters come from {@link SecureRandom} over a 31-symbol alphabet that omits look-alike characters
 * (0/O, 1/I/L), giving ~49.5 bits of entropy (31^10 ≈ 8.2 × 10^14 combinations). Codes are never
 * sequential or derived from database ids, so guessing a valid code is impractical, especially with
 * the rate limits on the lookup and check-in endpoints.
 *
 * <p>The access token is 256 bits of randomness (base64url) and is used only in ticket-page links.
 */
@Component
public class TicketCodes {

    static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    static final int RANDOM_LENGTH = 10;

    private static final Pattern NORMALIZED = Pattern.compile("^[A-Z]{2,5}-[" + ALPHABET + "]{5}-[" + ALPHABET + "]{5}$");
    private static final Pattern COMPACT = Pattern.compile("^([A-Z]{2,5})-?([A-Z0-9]{5})-?([A-Z0-9]{5})$");

    private final SecureRandom random = new SecureRandom();

    public String newTicketCode(String prefix) {
        StringBuilder code = new StringBuilder(prefix.length() + RANDOM_LENGTH + 2).append(prefix);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            if (i % 5 == 0) {
                code.append('-');
            }
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    public String newAccessToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Canonicalizes user input (manual entry, USB scanners, camera scans): trims, upper-cases, strips
     * spaces and re-inserts dashes. Returns {@code null} when the input cannot be a valid ticket code,
     * so callers can reject it without touching the database.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String compact = raw.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        var matcher = COMPACT.matcher(compact);
        if (!matcher.matches()) {
            return null;
        }
        String candidate = matcher.group(1) + "-" + matcher.group(2) + "-" + matcher.group(3);
        return NORMALIZED.matcher(candidate).matches() ? candidate : null;
    }

    /** Builds a 2–5 letter ticket prefix from an event title, e.g. "Cebu Dev Meetup 2026" becomes "CDM". */
    public static String prefixFromTitle(String title) {
        StringBuilder prefix = new StringBuilder();
        for (String word : title.split("[^A-Za-z]+")) {
            if (!word.isEmpty() && prefix.length() < 5) {
                prefix.append(Character.toUpperCase(word.charAt(0)));
            }
        }
        if (prefix.length() < 2) {
            String letters = title.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
            prefix = new StringBuilder(letters.length() >= 2 ? letters.substring(0, Math.min(3, letters.length())) : "EVT");
        }
        return prefix.toString();
    }
}
