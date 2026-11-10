package com.fyndig.recall.ingest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Reads CSV (comma, semicolon, tab or pipe delimited - auto-detected) or JSON (array of objects, or
 * {"records":[...]}) into rows keyed by canonical field names for a given feed.
 */
public final class RowParser {

    private static final ObjectMapper JSON = new ObjectMapper();

    private RowParser() {}

    public record ParsedFile(List<Map<String, String>> rows, List<String> unmappedHeaders, String format) {}

    public static ParsedFile parse(FeedType feed, String fileName, InputStream in) throws IOException {
        byte[] bytes = in.readAllBytes();
        String text = new String(bytes, StandardCharsets.UTF_8).replace("﻿", "");
        String trimmed = text.stripLeading();
        boolean isJson = (fileName != null && fileName.toLowerCase().endsWith(".json"))
                || trimmed.startsWith("[") || trimmed.startsWith("{");
        return isJson ? parseJson(feed, text) : parseCsv(feed, text);
    }

    private static ParsedFile parseJson(FeedType feed, String text) throws IOException {
        List<Map<String, Object>> raw;
        if (text.stripLeading().startsWith("[")) {
            raw = JSON.readValue(text, new TypeReference<>() {});
        } else {
            Map<String, Object> wrapper = JSON.readValue(text, new TypeReference<>() {});
            Object records = wrapper.getOrDefault("records", wrapper.getOrDefault("data", wrapper.get("rows")));
            if (!(records instanceof List<?> list)) throw new IOException("JSON must be an array or have a records/data/rows array");
            raw = new ArrayList<>();
            for (Object o : list) //noinspection unchecked
                raw.add((Map<String, Object>) o);
        }
        Set<String> unmapped = new LinkedHashSet<>();
        List<Map<String, String>> rows = new ArrayList<>();
        for (Map<String, Object> r : raw) {
            Map<String, String> row = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : r.entrySet()) {
                String field = canonicalField(feed, e.getKey());
                if (field == null) { unmapped.add(e.getKey()); continue; }
                row.put(field, e.getValue() == null ? null : String.valueOf(e.getValue()));
            }
            rows.add(row);
        }
        return new ParsedFile(rows, new ArrayList<>(unmapped), "JSON");
    }

    private static ParsedFile parseCsv(FeedType feed, String text) throws IOException {
        char delimiter = detectDelimiter(text);
        CSVFormat fmt = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter).setHeader().setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true).setTrim(true).setIgnoreEmptyLines(true).build();
        try (CSVParser parser = fmt.parse(new InputStreamReader(
                new java.io.ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8))) {
            Map<String, String> headerToField = new LinkedHashMap<>();
            List<String> unmapped = new ArrayList<>();
            for (String h : parser.getHeaderNames()) {
                String f = canonicalField(feed, h);
                if (f == null) unmapped.add(h); else headerToField.put(h, f);
            }
            List<Map<String, String>> rows = new ArrayList<>();
            for (CSVRecord rec : parser) {
                Map<String, String> row = new LinkedHashMap<>();
                boolean allBlank = true;
                for (Map.Entry<String, String> e : headerToField.entrySet()) {
                    String v = rec.isMapped(e.getKey()) ? rec.get(e.getKey()) : null;
                    if (v != null && !v.isBlank()) allBlank = false;
                    row.put(e.getValue(), v);
                }
                if (!allBlank) rows.add(row);
            }
            return new ParsedFile(rows, unmapped, "CSV(" + (delimiter == '\t' ? "TAB" : String.valueOf(delimiter)) + ")");
        }
    }

    private static char detectDelimiter(String text) {
        String firstLine = text.lines().findFirst().orElse("");
        char best = ',';
        long bestCount = -1;
        for (char c : new char[]{',', ';', '\t', '|'}) {
            long n = firstLine.chars().filter(ch -> ch == c).count();
            if (n > bestCount) { bestCount = n; best = c; }
        }
        return best;
    }

    static String normHeader(String h) {
        return h == null ? "" : h.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s\\-_./#()]", "");
    }

    static String canonicalField(FeedType feed, String header) {
        String nh = normHeader(header);
        for (Map.Entry<String, List<String>> e : feed.columnSynonyms.entrySet()) {
            if (normHeader(e.getKey()).equals(nh)) return e.getKey();
            for (String syn : e.getValue()) if (normHeader(syn).equals(nh)) return e.getKey();
        }
        return null;
    }
}
