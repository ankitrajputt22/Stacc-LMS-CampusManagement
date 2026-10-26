package com.stacc.backend.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ReportCsvTest {

    @Test
    void everyCellIsQuotedAndQuotesAreDoubled() {
        String csv = ReportCsv.write(List.of("Course Code", "Course Name"),
                List.of(List.of("CS301", "Data, Structures"), List.of("CS302", "The \"best\" course"),
                        List.of("CS303", "Line one\nLine two")));

        assertThat(csv).isEqualTo("\"Course Code\",\"Course Name\"\r\n"
                + "\"CS301\",\"Data, Structures\"\r\n"
                + "\"CS302\",\"The \"\"best\"\" course\"\r\n"
                + "\"CS303\",\"Line one\nLine two\"\r\n");
    }

    @Test
    void cellsThatASpreadsheetWouldRunAsFormulasAreWrittenAsText() {
        assertThat(ReportCsv.cell("=HYPERLINK(\"http://x\")")).isEqualTo("\"'=HYPERLINK(\"\"http://x\"\")\"");
        assertThat(ReportCsv.cell("+1")).isEqualTo("\"'+1\"");
        assertThat(ReportCsv.cell("-2+3")).isEqualTo("\"'-2+3\"");
        assertThat(ReportCsv.cell("@SUM(A1)")).isEqualTo("\"'@SUM(A1)\"");
        assertThat(ReportCsv.cell("\tx")).isEqualTo("\"'\tx\"");
        assertThat(ReportCsv.cell("\rx")).isEqualTo("\"'\rx\"");
        assertThat(ReportCsv.cell("Data=Structures")).isEqualTo("\"Data=Structures\"");
        assertThat(ReportCsv.cell("12")).isEqualTo("\"12\"");
        assertThat(ReportCsv.cell(null)).isEqualTo("\"\"");
        assertThat(ReportCsv.cell("")).isEqualTo("\"\"");
    }
}
