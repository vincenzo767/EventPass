package com.cubetech.eventpass.registration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TicketCodesTest {

    private final TicketCodes codes = new TicketCodes();

    @Test
    void generatedCodesHaveExpectedFormatAndAlphabet() {
        for (int i = 0; i < 1_000; i++) {
            String code = codes.newTicketCode("CDM");
            assertThat(code).matches("^CDM-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{5}-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{5}$");
            assertThat(TicketCodes.normalize(code)).isEqualTo(code);
        }
    }

    @Test
    void generatedCodesAreUniqueAndNotSequential() {
        Set<String> seen = new HashSet<>();
        String previous = null;
        int adjacentSuffixes = 0;
        for (int i = 0; i < 50_000; i++) {
            String code = codes.newTicketCode("CDM");
            assertThat(seen.add(code)).as("duplicate code %s", code).isTrue();
            if (previous != null && sharesPrefix(previous, code, 9)) {
                adjacentSuffixes++;
            }
            previous = code;
        }
        // Consecutive codes share no meaningful structure (a counter would share 9+ leading chars).
        assertThat(adjacentSuffixes).isLessThan(5);
    }

    @Test
    void everySymbolIsUsedRoughlyUniformly() {
        int[] counts = new int[TicketCodes.ALPHABET.length()];
        int samples = 20_000;
        for (int i = 0; i < samples; i++) {
            String random = codes.newTicketCode("AB").substring(3).replace("-", "");
            for (char c : random.toCharArray()) {
                counts[TicketCodes.ALPHABET.indexOf(c)]++;
            }
        }
        double expected = samples * 10.0 / TicketCodes.ALPHABET.length();
        for (int count : counts) {
            assertThat(count).isBetween((int) (expected * 0.9), (int) (expected * 1.1));
        }
    }

    @Test
    void accessTokensAre256BitUrlSafe() {
        String token = codes.newAccessToken();
        assertThat(token).hasSize(43).matches("^[A-Za-z0-9_-]+$");
        assertThat(codes.newAccessToken()).isNotEqualTo(token);
    }

    @ParameterizedTest
    @CsvSource({
        "cdm-7k3p9-q2xhm, CDM-7K3P9-Q2XHM",
        "CDM7K3P9Q2XHM, CDM-7K3P9-Q2XHM",
        "' CDM - 7K3P9 - Q2XHM ', CDM-7K3P9-Q2XHM",
        "MCS-ABCDE-FGHJK, MCS-ABCDE-FGHJK"
    })
    void normalizeAcceptsCommonInputVariations(String input, String expected) {
        assertThat(TicketCodes.normalize(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "CDM-000001", "CDM-00000-00001", "CDM-7K3P9-Q2XH", "CDM-7K3P9-Q2XHO", "C-7K3P9-Q2XHM",
        "' OR 1=1 --", "CDM-7K3P9-Q2XHM-EXTRA"})
    void normalizeRejectsMalformedOrImpossibleCodes(String input) {
        assertThat(TicketCodes.normalize(input)).isNull();
    }

    @ParameterizedTest
    @CsvSource({
        "Cebu Dev Meetup 2026, CDM",
        "Mactan Cloud & AI Summit, MCAS",
        "Cebu UX Design Night, CUDN",
        "Hackathon, HAC",
        "A Very Long Event Name With Many Words, AVLEN"
    })
    void prefixIsDerivedFromTitle(String title, String expected) {
        assertThat(TicketCodes.prefixFromTitle(title)).isEqualTo(expected);
    }

    private static boolean sharesPrefix(String a, String b, int length) {
        return a.regionMatches(0, b, 0, length);
    }
}
