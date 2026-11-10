package com.fyndig.recall;

import com.fyndig.recall.entity.*;
import com.fyndig.recall.ingest.Normalizer;
import com.fyndig.recall.repo.*;
import com.fyndig.recall.service.RecallService;
import com.fyndig.recall.service.ReconciliationService;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The CAP-04 demonstration as an executable test:
 * one supplier lot (AB-7781) -> two production batches (+ split + merge) -> multiple customer orders,
 * with an unmatched supplier code (ASC-01) that hides part of the lot until it is reconciled.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:scenario;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.seed.enabled=true"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RecallScenarioTest {

    @Autowired RecallService recalls;
    @Autowired ReconciliationService reconciliation;
    @Autowired SupplierRepo suppliers;
    @Autowired SupplierLotRepo supplierLots;
    @Autowired UnmatchedRecordRepo unmatched;
    @Autowired IngestionRunRepo runs;

    private Long ab7781() {
        Supplier s = suppliers.findByCanonicalCode("SUP0001").orElseThrow();
        return supplierLots.findBySupplierIdAndLotCodeNormalized(s.getId(), Normalizer.lotCode("AB-7781")).orElseThrow().getId();
    }

    private static List<String> keys(RecallService.Simulation sim) {
        return sim.affectedLines().stream().map(l -> l.orderNo() + "/" + l.lineNo()).sorted().collect(Collectors.toList());
    }

    @Test @Order(1)
    void normalizerHandlesInconsistentCodes() {
        assertThat(Normalizer.supplierCode("sup-1")).isEqualTo("SUP0001");
        assertThat(Normalizer.supplierCode("Supplier 2")).isEqualTo("SUP0002");
        assertThat(Normalizer.supplierCode("S-001")).isEqualTo("SUP0001");
        assertThat(Normalizer.supplierCode("ASC-01")).isEqualTo("ASC01");
        assertThat(Normalizer.lotCode("ab 77-81")).isEqualTo("AB7781");
        assertThat(Normalizer.quantity("1,000")).isEqualByComparingTo("1000");
        assertThat(Normalizer.quantity("1.250,5")).isEqualByComparingTo("1250.5");
        assertThat(Normalizer.date("5-Mar-2026")).hasToString("2026-03-05");
        assertThat(Normalizer.date("03.03.2026")).hasToString("2026-03-03");
    }

    @Test @Order(2)
    void ingestionValidationCountsRejectsDuplicatesAndUnmatched() {
        var byFeed = runs.findAll().stream().collect(Collectors.toMap(IngestionRun::getFeedType, r -> r));
        assertThat(byFeed.get("DISPATCHES").getRowsRejected()).isEqualTo(1);   // negative quantity
        assertThat(byFeed.get("DISPATCHES").getRowsDuplicate()).isEqualTo(1);  // SHP-0001 twice
        assertThat(byFeed.get("MOVEMENTS").getRowsRejected()).isEqualTo(1);    // unknown movement type TELEPORT
        assertThat(byFeed.get("RECEIPTS").getRowsUnmatched()).isEqualTo(1);    // ASC-01
        assertThat(unmatched.countByStatus(UnmatchedRecord.Status.OPEN)).isEqualTo(1);
    }

    @Test @Order(3)
    void beforeReconciliationScopeIsFlaggedIncomplete() {
        RecallService.Simulation sim = recalls.simulate(ab7781());
        assertThat(keys(sim)).containsExactly(
                "ORD-1001/1", "ORD-1002/1", "ORD-1003/1", "ORD-1003/2", "ORD-1004/1", "ORD-1005/1", "ORD-1006/1", "ORD-1008/1");
        assertThat(sim.scope().finishedBatches()).isEqualTo(5);   // 0210, 0211, 0211-A, 0211-B, 0214
        assertThat(sim.completeness().bfsEqualsSql()).isTrue();
        assertThat(sim.completeness().closureClosed()).isTrue();
        assertThat(sim.completeness().massBalanced()).isFalse();  // 100 kg of AB-7781 unaccounted
        assertThat(sim.completeness().unresolvedRelated()).isEqualTo(1);
        assertThat(sim.completeness().verified()).isFalse();
        // ORD-1002 line 2 (crackers from AB-7790) must NOT be in scope - line-level precision
        assertThat(keys(sim)).doesNotContain("ORD-1002/2", "ORD-1009/1", "ORD-1010/1", "ORD-1011/1");
    }

    @Test @Order(4)
    void reconciliationResolvesUnmatchedCodeAndRecallBecomesComplete() throws Exception {
        UnmatchedRecord u = unmatched.findByStatus(UnmatchedRecord.Status.OPEN).get(0);
        assertThat(reconciliation.suggest(u.getId()).get(0).candidate()).isEqualTo("SUP0001");
        RecallEvent committed = recalls.commit(ab7781(), "Salmonella positive on supplier CoA re-test", "CLASS_I", false);
        assertThat(committed.getOrderLinesAffected()).isEqualTo(8);

        var res = reconciliation.resolve(u.getId(), "SUP0001", "test");
        assertThat(res.accepted()).isEqualTo(1);

        RecallService.Simulation sim = recalls.simulate(ab7781());
        assertThat(keys(sim)).contains("ORD-1007/1").hasSize(9);
        assertThat(sim.scope().orders()).isEqualTo(8);
        assertThat(sim.scope().customers()).isEqualTo(6);
        assertThat(sim.completeness().massBalanced()).isTrue();
        assertThat(sim.completeness().verified()).isTrue();

        RecallEvent recomputed = recalls.recompute(committed.getId());
        assertThat(recomputed.getOrderLinesAffected()).isEqualTo(9);
        assertThat(recomputed.isCompletenessVerified()).isTrue();
    }
}
