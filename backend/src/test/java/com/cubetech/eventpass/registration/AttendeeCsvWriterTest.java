package com.cubetech.eventpass.registration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AttendeeCsvWriterTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
        "=SUM(A1:A2)|'=SUM(A1:A2)",
        "+639171234567|'+639171234567",
        "-2+3|'-2+3",
        "@cmd|'@cmd",
        "Juan dela Cruz|Juan dela Cruz",
        "Peña, Jr.|\"Peña, Jr.\"",
        "say \"hi\"|\"say \"\"hi\"\"\"",
        "=1,2|\"'=1,2\""
    })
    void escapesFormulasAndQuotesSpecialCharacters(String input, String expected) {
        assertThat(AttendeeCsvWriter.escape(input)).isEqualTo(expected);
    }
}
