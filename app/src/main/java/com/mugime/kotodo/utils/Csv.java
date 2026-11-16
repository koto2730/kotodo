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

    /** UTF-8 byte order mark, which Excel expects at the start of UTF-8 CSV files. */
    public static final char BOM = '\uFEFF';

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
                    row.add(unsanitizeFormula(field.toString()));
                    field.setLength(0);
                    rowStarted = true;
                    break;
                case '\r':
                    // Part of a CRLF pair; the '\n' below closes the row.
                    break;
                case '\n':
                    row.add(unsanitizeFormula(field.toString()));
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
            row.add(unsanitizeFormula(field.toString()));
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
        String safe = sanitizeFormula(value);
        boolean needsQuotes = safe.indexOf(',') >= 0
                || safe.indexOf('"') >= 0
                || safe.indexOf('\n') >= 0
                || safe.indexOf('\r') >= 0
                || safe.charAt(0) == ' '
                || safe.charAt(safe.length() - 1) == ' ';
        if (!needsQuotes) {
            return safe;
        }
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    /**
     * Prefixes fields starting with {@code =}, {@code +}, {@code -}, {@code @} or tab
     * with a single quote so spreadsheet apps do not evaluate them as formulas.
     */
    @NonNull
    public static String sanitizeFormula(@NonNull String value) {
        if (!value.isEmpty() && isFormulaTrigger(value.charAt(0))) {
            return "'" + value;
        }
        return value;
    }

    /** Reverses {@link #sanitizeFormula} on import so round-tripping is lossless. */
    @NonNull
    public static String unsanitizeFormula(@NonNull String value) {
        if (value.length() >= 2 && value.charAt(0) == '\'' && isFormulaTrigger(value.charAt(1))) {
            return value.substring(1);
        }
        return value;
    }

    private static boolean isFormulaTrigger(char c) {
        return c == '=' || c == '+' || c == '-' || c == '@' || c == '\t';
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
