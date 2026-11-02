package com.stacc.backend.identity.admin;

import java.util.ArrayList;
import java.util.List;

/**
 * A small reader for comma-separated text (RFC 4180): fields may be quoted with double quotes, a quote inside a quoted
 * field is written twice, and lines end with LF or CRLF. A leading byte-order mark is ignored. It reads text only and
 * never evaluates anything.
 */
final class CsvRows {

    private CsvRows() {
    }

    /** Every non-empty line, each as its fields. Throws IllegalArgumentException for an unclosed quote. */
    static List<List<String>> parse(String text) {
        String input = text.startsWith("﻿") ? text.substring(1) : text;
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean fieldStarted = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < input.length() && input.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"' && field.length() == 0 && !fieldStarted) {
                quoted = true;
                fieldStarted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < input.length() && input.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
                addIfNotBlank(rows, row);
                row = new ArrayList<>();
            } else {
                field.append(c);
                fieldStarted = true;
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("A quoted field is not closed.");
        }
        row.add(field.toString());
        addIfNotBlank(rows, row);
        return rows;
    }

    private static void addIfNotBlank(List<List<String>> rows, List<String> row) {
        if (row.stream().anyMatch(value -> !value.isBlank())) {
            rows.add(row);
        }
    }
}
