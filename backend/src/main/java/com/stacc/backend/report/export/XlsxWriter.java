package com.stacc.backend.report.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

/** Writes simple sheets of text cells as XLSX. Cells are always text, so nothing in them is ever run as a formula. */
final class XlsxWriter {

    record Sheet(String name, List<String> header, List<List<String>> rows) {
    }

    private XlsxWriter() {
    }

    static byte[] write(List<Sheet> sheets) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Workbook workbook = new Workbook(out, "Stacc", "1.0");
            for (Sheet sheet : sheets) {
                Worksheet ws = workbook.newWorksheet(sheet.name());
                for (int c = 0; c < sheet.header().size(); c++) {
                    ws.value(0, c, sheet.header().get(c));
                    ws.style(0, c).bold().set();
                }
                for (int r = 0; r < sheet.rows().size(); r++) {
                    List<String> row = sheet.rows().get(r);
                    for (int c = 0; c < row.size(); c++) {
                        ws.value(r + 1, c, row.get(c) == null ? "" : row.get(c));
                    }
                }
            }
            workbook.finish();
            return out.toByteArray();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
