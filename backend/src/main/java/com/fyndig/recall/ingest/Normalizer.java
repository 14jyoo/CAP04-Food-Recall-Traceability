package com.fyndig.recall.ingest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure functions that turn the inconsistent codes/dates/quantities coming from the supplier portal,
 * MES and WMS into comparable keys. The Python pipeline (data-pipeline/normalize.py) implements the
 * same rules so both paths agree.
 */
public final class Normalizer {

    private Normalizer() {}

    private static final Pattern SUPPLIER_CODE = Pattern.compile("^(?:SUPPLIER|SUPP|SUP|S)?[\\s\\-_./#]*0*(\\d{1,6})$");

    /**
     * "sup-1", "SUP001", "Supplier 0001", "S1" -> "SUP0001".
     * Anything that does not reduce to a number keeps a cleaned alphanumeric form, so it can still be
     * matched through the alias table but will not be *assumed* equal to any canonical code.
     */
    public static String supplierCode(String raw) {
        if (raw == null) return null;
        String cleaned = raw.trim().toUpperCase(Locale.ROOT);
        Matcher m = SUPPLIER_CODE.matcher(cleaned);
        if (m.matches()) {
            return "SUP" + String.format("%04d", Integer.parseInt(m.group(1)));
        }
        return cleaned.replaceAll("[^A-Z0-9]", "");
    }

    /** Lot codes: case-insensitive, separators ignored, leading/trailing space removed. "ab 77-81" == "AB7781". */
    public static String lotCode(String raw) {
        if (raw == null) return null;
        return raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    /** Plant-internal codes are authoritative; we only trim and upper-case them. */
    public static String internalCode(String raw) {
        return raw == null ? null : raw.trim().toUpperCase(Locale.ROOT);
    }

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,                       // 2026-03-04
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),              // 04/03/2026 (plant is in India/EU style)
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH),   // 4-Mar-2026
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),  // Mar 4, 2026
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    );

    public static LocalDate date(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        if (s.length() > 10 && s.charAt(10) == 'T') s = s.substring(0, 10);  // ISO datetime
        if (s.length() > 10 && s.charAt(10) == ' ') s = s.substring(0, 10);  // "2026-03-04 10:00"
        for (DateTimeFormatter f : DATE_FORMATS) {
            try { return LocalDate.parse(s, f); } catch (DateTimeParseException ignored) {}
        }
        throw new IllegalArgumentException("Unparseable date: '" + raw + "'");
    }

    public static LocalDateTime dateTime(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        try { return LocalDateTime.parse(s); } catch (DateTimeParseException ignored) {}
        try { return LocalDateTime.parse(s.replace(' ', 'T')); } catch (DateTimeParseException ignored) {}
        try { return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")); } catch (DateTimeParseException ignored) {}
        try { return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")); } catch (DateTimeParseException ignored) {}
        return date(s).atTime(LocalTime.MIDNIGHT);
    }

    /** "1,250.5", "1250,5" (EU), " 12 " -> BigDecimal. */
    public static BigDecimal quantity(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim().replace(" ", "");
        if (s.contains(",") && s.contains(".")) {
            s = s.lastIndexOf(',') > s.lastIndexOf('.') ? s.replace(".", "").replace(',', '.') : s.replace(",", "");
        } else if (s.contains(",")) {
            // single comma: thousands if followed by exactly 3 digits at end, else decimal
            s = s.matches(".*,\\d{3}$") ? s.replace(",", "") : s.replace(',', '.');
        }
        try { return new BigDecimal(s); } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Unparseable quantity: '" + raw + "'");
        }
    }

    public static String uom(String raw) {
        if (raw == null || raw.isBlank()) return "KG";
        String u = raw.trim().toUpperCase(Locale.ROOT);
        return switch (u) {
            case "KGS", "KILOGRAM", "KILOGRAMS", "KILO" -> "KG";
            case "LTR", "LITRE", "LITER", "LITRES", "LITERS" -> "L";
            case "PCS", "PIECE", "PIECES", "EA", "EACH", "UNITS", "UNIT" -> "EA";
            case "CS", "CASE", "CASES", "CTN", "CARTON" -> "CASE";
            default -> u;
        };
    }
}
