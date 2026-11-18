package com.stacc.backend.report;

import java.util.List;

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
