package com.mugime.kotodo.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A small RFC 4180 reader/writer.
 *
 * <p>Handles quoted fields, doubled quotes, embedded newlines, CRLF line endings
 * and a UTF-8 BOM, which together cover what Excel and Google Sheets produce.</p>
 */
public final class Csv {

    /** UTF-8 byte order mark, which Excel writes at the start of exported files. */
    private static final char BOM = (char) 0xFEFF;

    private Csv() {
    }

    /** Splits CSV text into rows of raw (unescaped) field values. */
    @NonNull
    public static List<List<String>> parse(@Nullable String text) {
        List<List<String>> rows = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return rows;
        }
        if (text.charAt(0) == BOM) {
            text = text.substring(1);
        }

        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean rowStarted = false;
        int length = text.length();

        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < length && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
                continue;
            }

            switch (c) {
                case '"':
                    inQuotes = true;
                    rowStarted = true;
                    break;
                case ',':
                    row.add(field.toString());
                    field.setLength(0);
                    rowStarted = true;
                    break;
                case '\r':
                    // Part of a CRLF pair; the '\n' below closes the row.
                    break;
                case '\n':
                    row.add(field.toString());
                    field.setLength(0);
                    rows.add(row);
                    row = new ArrayList<>();
                    rowStarted = false;
                    break;
                default:
                    field.append(c);
                    rowStarted = true;
                    break;
            }
        }

        if (rowStarted || field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    /** Renders one row, quoting only the fields that need it. */
    @NonNull
    public static String formatRow(@NonNull List<String> values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(escape(values.get(i)));
        }
        return out.toString();
    }

    @NonNull
    public static String escape(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        boolean needsQuotes = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0
                || value.charAt(0) == ' '
                || value.charAt(value.length() - 1) == ' ';
        if (!needsQuotes) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    /** True when the row has no content at all, e.g. a stray blank line. */
    public static boolean isBlank(@NonNull List<String> row) {
        for (String value : row) {
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
