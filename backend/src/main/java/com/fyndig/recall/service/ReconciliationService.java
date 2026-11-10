package com.fyndig.recall.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fyndig.recall.entity.*;
import com.fyndig.recall.ingest.IngestionService;
import com.fyndig.recall.ingest.Normalizer;
import com.fyndig.recall.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Reconciliation queue: unknown supplier codes and unknown lot references.
 * Suggests candidates by normalised-code similarity, lets a user resolve them, and replays the parked rows
 * so the missing lineage edges appear - after which any open recall can be recomputed.
 */
@Service
public class ReconciliationService {

    private static final ObjectMapper JSON = new ObjectMapper();

    public record Suggestion(String candidate, String label, int score, String reason) {}
    public record Resolution(int replayed, int accepted, List<IngestionService.RowResult> results) {}

    private final UnmatchedRecordRepo unmatched;
    private final SupplierRepo suppliers;
    private final SupplierAliasRepo aliases;
    private final InternalLotRepo internalLots;
    private final IngestionService ingestion;

    public ReconciliationService(UnmatchedRecordRepo unmatched, SupplierRepo suppliers, SupplierAliasRepo aliases,
                                 InternalLotRepo internalLots, IngestionService ingestion) {
        this.unmatched = unmatched; this.suppliers = suppliers; this.aliases = aliases;
        this.internalLots = internalLots; this.ingestion = ingestion;
    }

    public List<Suggestion> suggest(Long unmatchedId) {
        UnmatchedRecord u = unmatched.findById(unmatchedId).orElseThrow();
        List<Suggestion> out = new ArrayList<>();
        String val = u.getOffendingValue() == null ? "" : u.getOffendingValue();
        if ("UNKNOWN_SUPPLIER_CODE".equals(u.getIssue())) {
            String norm = Normalizer.supplierCode(val);
            String digits = val.replaceAll("\\D", "");
            for (Supplier s : suppliers.findAll()) {
                int score = 0;
                List<String> why = new ArrayList<>();
                if (s.getCanonicalCode().equals(norm)) { score += 100; why.add("normalises to canonical code"); }
                String sDigits = s.getCanonicalCode().replaceAll("\\D", "");
                if (!digits.isEmpty() && Integer.parseInt(digits) == Integer.parseInt(sDigits)) { score += 60; why.add("same numeric id"); }
                int d = levenshtein(norm, s.getCanonicalCode());
                if (d <= 2) { score += 30 - 10 * d; why.add("edit distance " + d); }
                String nameKey = s.getName().toUpperCase().replaceAll("[^A-Z0-9]", "");
                String valKey = val.toUpperCase().replaceAll("[^A-Z0-9]", "");
                if (valKey.length() >= 4 && (nameKey.contains(valKey) || valKey.contains(nameKey.substring(0, Math.min(5, nameKey.length()))))) { score += 40; why.add("matches supplier name"); }
                for (SupplierAlias a : aliases.findBySupplierId(s.getId())) {
                    if (levenshtein(a.getAliasNormalized(), norm) <= 1) { score += 25; why.add("close to alias " + a.getAliasRaw()); break; }
                }
                if (score > 0) out.add(new Suggestion(s.getCanonicalCode(), s.getCanonicalCode() + " · " + s.getName(), score, String.join(", ", why)));
            }
        } else { // unknown internal / finished lot
            String norm = Normalizer.lotCode(val);
            for (InternalLot l : internalLots.findAll()) {
                int d = levenshtein(norm, Normalizer.lotCode(l.getLotCode()));
                if (d <= 2) out.add(new Suggestion(l.getLotCode(), l.getLotCode() + " · " + l.getKind() + " " + l.getMaterialCode(), 100 - 30 * d, "edit distance " + d));
            }
        }
        out.sort(Comparator.comparingInt(Suggestion::score).reversed());
        return out.size() > 8 ? out.subList(0, 8) : out;
    }

    /**
     * Resolve an unmatched record to a canonical supplier code (adds a MANUAL alias and replays every open row
     * with the same raw code) or to an existing internal lot code (rewrites the offending field and replays that row).
     */
    @Transactional
    public Resolution resolve(Long unmatchedId, String resolveTo, String resolvedBy) throws IOException {
        UnmatchedRecord u = unmatched.findById(unmatchedId).orElseThrow();
        List<IngestionService.RowResult> results = new ArrayList<>();
        int accepted = 0;
        List<UnmatchedRecord> batch;

        if ("UNKNOWN_SUPPLIER_CODE".equals(u.getIssue())) {
            Supplier s = suppliers.findByCanonicalCode(Normalizer.supplierCode(resolveTo))
                    .orElseThrow(() -> new NoSuchElementException("No supplier with code " + resolveTo));
            ingestion.addAlias(s, u.getOffendingValue(), "MANUAL");
            batch = unmatched.findByStatusAndOffendingValue(UnmatchedRecord.Status.OPEN, u.getOffendingValue());
        } else {
            InternalLot target = internalLots.findByLotCode(Normalizer.internalCode(resolveTo))
                    .orElseThrow(() -> new NoSuchElementException("No internal lot " + resolveTo));
            Map<String, String> row = JSON.readValue(u.getRawRow(), new TypeReference<>() {});
            for (String f : List.of("fromLotCode", "finishedLotCode", "internalLotCode")) {
                if (row.containsKey(f) && Normalizer.internalCode(row.get(f)).equals(Normalizer.internalCode(u.getOffendingValue())))
                    row.put(f, target.getLotCode());
            }
            u.setRawRow(JSON.writeValueAsString(row));
            batch = List.of(u);
        }
        for (UnmatchedRecord rec : batch) {
            IngestionService.RowResult r = ingestion.replay(rec);
            results.add(r);
            if ("ACCEPTED".equals(r.outcome()) || "DUPLICATE".equals(r.outcome())) {
                accepted++;
                rec.setStatus(UnmatchedRecord.Status.RESOLVED);
                rec.setResolvedTo(resolveTo);
                rec.setResolvedBy(resolvedBy == null ? "operator" : resolvedBy);
                rec.setResolvedAt(LocalDateTime.now());
                unmatched.save(rec);
            }
        }
        return new Resolution(batch.size(), accepted, results);
    }

    @Transactional
    public UnmatchedRecord ignore(Long unmatchedId, String by) {
        UnmatchedRecord u = unmatched.findById(unmatchedId).orElseThrow();
        u.setStatus(UnmatchedRecord.Status.IGNORED);
        u.setResolvedBy(by);
        u.setResolvedAt(LocalDateTime.now());
        return unmatched.save(u);
    }

    static int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++)
            for (int j = 1; j <= b.length(); j++)
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
        return dp[a.length()][b.length()];
    }
}
