package com.fyndig.recall.web;

import com.fyndig.recall.entity.SupplierLot;
import com.fyndig.recall.ingest.Normalizer;
import com.fyndig.recall.repo.*;
import com.fyndig.recall.service.RecallService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin
public class AdminController {

    private final RecallService recallService;
    private final SupplierRepo suppliers; private final SupplierAliasRepo aliases; private final SupplierLotRepo supplierLots;
    private final InternalLotRepo internalLots; private final OrderLineRepo orderLines; private final LineageEdgeRepo edges;
    private final UnmatchedRecordRepo unmatched; private final RecallEventRepo recalls; private final RecallImpactRepo impacts;
    private final NotificationRepo notifications; private final IngestionRunRepo runs;

    public AdminController(RecallService recallService, SupplierRepo suppliers, SupplierAliasRepo aliases, SupplierLotRepo supplierLots,
                           InternalLotRepo internalLots, OrderLineRepo orderLines, LineageEdgeRepo edges, UnmatchedRecordRepo unmatched,
                           RecallEventRepo recalls, RecallImpactRepo impacts, NotificationRepo notifications, IngestionRunRepo runs) {
        this.recallService = recallService; this.suppliers = suppliers; this.aliases = aliases; this.supplierLots = supplierLots;
        this.internalLots = internalLots; this.orderLines = orderLines; this.edges = edges; this.unmatched = unmatched;
        this.recalls = recalls; this.impacts = impacts; this.notifications = notifications; this.runs = runs;
    }

    /** Wipe every table so a different scenario (e.g. a team's hidden scenario) can be loaded. */
    @PostMapping("/reset")
    @Transactional
    public Map<String, String> reset() {
        notifications.deleteAllInBatch(); impacts.deleteAllInBatch(); recalls.deleteAllInBatch();
        edges.deleteAllInBatch(); unmatched.deleteAllInBatch(); orderLines.deleteAllInBatch();
        internalLots.deleteAllInBatch(); supplierLots.deleteAllInBatch(); aliases.deleteAllInBatch();
        suppliers.deleteAllInBatch(); runs.deleteAllInBatch();
        return Map.of("status", "empty");
    }

    public record VerifyRequest(String supplierCode, String lotCode, String expectedSha256) {}

    /**
     * Compare the system's affected-order-line set against a sealed answer (sha256 of the sorted
     * "ORDER/LINE" list). Tells a team whether they are complete without revealing the answer.
     */
    @PostMapping("/verify")
    public Map<String, Object> verify(@RequestBody VerifyRequest req) {
        var sup = suppliers.findByCanonicalCode(Normalizer.supplierCode(req.supplierCode()))
                .orElseThrow(() -> new NoSuchElementException("Supplier " + req.supplierCode() + " not found"));
        SupplierLot lot = supplierLots.findBySupplierIdAndLotCodeNormalized(sup.getId(), Normalizer.lotCode(req.lotCode()))
                .orElseThrow(() -> new NoSuchElementException("Lot " + req.lotCode() + " not found for " + sup.getCanonicalCode()));
        RecallService.Simulation sim = recallService.simulate(lot.getId());
        boolean match = sim.completeness().affectedOrderLinesSha256().equalsIgnoreCase(req.expectedSha256().trim());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("match", match);
        out.put("affectedOrderLines", sim.scope().orderLines());
        out.put("systemCompletenessVerified", sim.completeness().verified());
        out.put("fingerprint", sim.completeness().affectedOrderLinesSha256());
        out.put("verdict", match && sim.completeness().verified() ? "COMPLETE - matches sealed answer and all internal proofs pass"
                : match ? "Matches sealed answer, but internal proofs report open issues"
                : "NOT COMPLETE - affected set differs from the sealed answer. Check reconciliation queue and mass-balance notes.");
        return out;
    }
}
