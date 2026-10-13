package dev.coreviewer.storage;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Converts compatible workbook sheets once, on the storage worker. */
public final class XlsxImporter {
    public record SheetData(String name, String csv) {}

    public static List<SheetData> read(Path file) throws IOException {
        if (!file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xlsx"))
            throw new IOException("Select an .xlsx workbook.");
        if (!Files.isRegularFile(file) || Files.size(file) > 32L * 1024 * 1024)
            throw new IOException("Workbook must exist and be at most 32 MB.");
        var result = new ArrayList<SheetData>();
        try (var workbook = WorkbookFactory.create(file.toFile())) {
            if (workbook.getNumberOfSheets() > 32) throw new IOException("Maximum 32 sheets.");
            var formatter = new DataFormatter(Locale.ROOT);
            formatter.setUseCachedValuesForFormulaCells(true);
            int totalRows = 0;
            for (var sheet : workbook) {
                Row header = sheet.getRow(sheet.getFirstRowNum());
                if (header == null) continue;
                int columns = header.getLastCellNum();
                if (columns < 1 || columns > 40) continue;
                var headers = new ArrayList<String>();
                for (int col = 0; col < columns; col++)
                    headers.add(formatter.formatCellValue(header.getCell(col)).strip());
                if (!headers.contains("action_id") && !headers.contains("type")) continue;
                var csv = new StringBuilder();
                for (int rowNumber = sheet.getFirstRowNum();
                        rowNumber <= sheet.getLastRowNum();
                        rowNumber++) {
                    if (++totalRows > 200_000)
                        throw new IOException("Maximum 200,000 workbook rows.");
                    Row row = sheet.getRow(rowNumber);
                    for (int col = 0; col < columns; col++) {
                        if (col > 0) csv.append(',');
                        String value =
                                row == null ? "" : formatter.formatCellValue(row.getCell(col));
                        csv.append(EventCodec.escape(value));
                    }
                    csv.append('\n');
                    if (csv.length() > 64_000_000)
                        throw new IOException("A converted sheet exceeds 64 MB.");
                }
                result.add(new SheetData(sheet.getSheetName(), csv.toString()));
            }
        } catch (IOException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new IOException("Cannot read .xlsx workbook: " + ex.getMessage(), ex);
        }
        return List.copyOf(result);
    }
}
