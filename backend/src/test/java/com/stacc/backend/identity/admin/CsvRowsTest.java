package com.stacc.backend.identity.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class CsvRowsTest {

    @Test
    void quotedFieldsKeepCommasQuotesAndLineBreaks() {
        List<List<String>> rows = CsvRows.parse("﻿a,\"b, c\",\"say \"\"hi\"\"\"\r\n1,\"two\nlines\",3\n");

        assertEquals(List.of(List.of("a", "b, c", "say \"hi\""), List.of("1", "two\nlines", "3")), rows);
    }

    @Test
    void blankLinesAreSkippedAndEmptyFieldsKept() {
        assertEquals(List.of(List.of("a", "", "c")), CsvRows.parse("\n\na,,c\n\n,,\n"));
    }

    @Test
    void anUnclosedQuoteIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> CsvRows.parse("a,\"b\nc"));
    }
}
