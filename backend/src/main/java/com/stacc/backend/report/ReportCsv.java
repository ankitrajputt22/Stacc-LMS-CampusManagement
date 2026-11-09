package com.stacc.backend.report;

import java.util.List;

/**
 * Writes CSV for spreadsheets, safely. Every cell is quoted and inner quotes are doubled, so commas, quotes, and line
 * breaks stay inside their cell. A text cell that a spreadsheet would read as a formula (it starts with =, +, -, @, a
 * tab, or a carriage return) is prefixed with an apostrophe, so it is shown as text and never run. Rows end with CRLF.
 */
public final class ReportCsv {

    private ReportCsv() {
    }

    public static String write(List<String> header, List<List<String>> rows) {
        StringBuilder out = new StringBuilder();
        appendRow(out, header);
        for (List<String> row : rows) {
            appendRow(out, row);
        }
        return out.toString();
    }

    static String cell(String value) {
        String text = value == null ? "" : value;
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static void appendRow(StringBuilder out, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(cell(cells.get(i)));
        }
        out.append("\r\n");
    }
}
