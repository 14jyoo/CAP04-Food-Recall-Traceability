package com.fyndig.recall.service;

import com.fyndig.recall.entity.*;
import com.fyndig.recall.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/** Recall impact analytics - the same numbers the Power BI model exposes, available to the React dashboard. */
@Service
public class AnalyticsService {

    public record Kpis(long suppliers, long supplierLots, long internalLots, long finishedBatches, long orderLines,
                       long edges, long recalls, long openRecalls, long unmatchedOpen, long unmatchedResolved,
                       double avgTraversalMillis, Double avgMinutesToFullNotification, double verifiedRecallPct) {}

    public record SupplierImpact(String supplierCode, String supplierName, long supplierLots, long recalls,
                                 long orderLinesAffected, long customersAffected, long openUnmatched) {}

    public record RecallRow(Long id, String reference, String supplier, String supplierLot, String ingredient, String status,
                            String severity, String createdAt, int orderLines, int orders, int customers, int batches,
                            long traversalMillis, int maxDepth, boolean verified, Long minutesToLineage, Long minutesToAllNotified,
                            long notificationsTotal, long notificationsSent, long notificationsAcked) {}

    public record IngestionStat(String feedType, long runs, long read, long accepted, long unmatched, long rejected, long duplicate) {}

    private final SupplierRepo suppliers; private final SupplierLotRepo supplierLots; private final InternalLotRepo internalLots;
    private final OrderLineRepo orderLines; private final LineageEdgeRepo edges; private final RecallEventRepo recalls;
    private final UnmatchedRecordRepo unmatched; private final NotificationRepo notifications; private final IngestionRunRepo runs;

    public AnalyticsService(SupplierRepo suppliers, SupplierLotRepo supplierLots, InternalLotRepo internalLots, OrderLineRepo orderLines,
                            LineageEdgeRepo edges, RecallEventRepo recalls, UnmatchedRecordRepo unmatched, NotificationRepo notifications,
                            IngestionRunRepo runs) {
        this.suppliers = suppliers; this.supplierLots = supplierLots; this.internalLots = internalLots; this.orderLines = orderLines;
        this.edges = edges; this.recalls = recalls; this.unmatched = unmatched; this.notifications = notifications; this.runs = runs;
    }

    @Transactional(readOnly = true)
    public Kpis kpis() {
        List<RecallEvent> all = recalls.findAll();
        double avgT = all.stream().mapToLong(RecallEvent::getTraversalMillis).average().orElse(0);
        OptionalDouble avgN = all.stream().filter(r -> r.getAllNotifiedAt() != null)
                .mapToLong(r -> Duration.between(r.getCreatedAt(), r.getAllNotifiedAt()).toMinutes()).average();
        double verifiedPct = all.isEmpty() ? 0 : 100.0 * all.stream().filter(RecallEvent::isCompletenessVerified).count() / all.size();
        return new Kpis(suppliers.count(), supplierLots.count(), internalLots.count(),
                internalLots.findByKind(InternalLot.Kind.FINISHED).size(), orderLines.count(), edges.count(),
                all.size(), all.stream().filter(r -> r.getStatus() == RecallEvent.Status.OPEN || r.getStatus() == RecallEvent.Status.NOTIFYING).count(),
                unmatched.countByStatus(UnmatchedRecord.Status.OPEN), unmatched.countByStatus(UnmatchedRecord.Status.RESOLVED),
                Math.round(avgT * 10) / 10.0, avgN.isPresent() ? Math.round(avgN.getAsDouble() * 10) / 10.0 : null,
                Math.round(verifiedPct * 10) / 10.0);
    }

    @Transactional(readOnly = true)
    public List<SupplierImpact> supplierImpact() {
        List<SupplierImpact> out = new ArrayList<>();
        List<UnmatchedRecord> open = unmatched.findByStatus(UnmatchedRecord.Status.OPEN);
        for (Supplier s : suppliers.findAll()) {
            List<RecallEvent> rs = recalls.findBySupplierLotSupplierId(s.getId());
            long openForSupplier = open.stream().filter(u -> u.getOffendingValue() != null
                    && com.fyndig.recall.ingest.Normalizer.supplierCode(u.getOffendingValue()).equals(s.getCanonicalCode())).count();
            out.add(new SupplierImpact(s.getCanonicalCode(), s.getName(), supplierLots.findBySupplierId(s.getId()).size(), rs.size(),
                    rs.stream().mapToLong(RecallEvent::getOrderLinesAffected).sum(),
                    rs.stream().mapToLong(RecallEvent::getCustomersAffected).sum(), openForSupplier));
        }
        out.sort(Comparator.comparingLong(SupplierImpact::orderLinesAffected).reversed().thenComparing(SupplierImpact::supplierCode));
        return out;
    }

    @Transactional(readOnly = true)
    public List<RecallRow> recallTable() {
        List<RecallRow> out = new ArrayList<>();
        for (RecallEvent r : recalls.findAllByOrderByCreatedAtDesc()) {
            List<Notification> ns = notifications.findByRecall_Id(r.getId());
            out.add(new RecallRow(r.getId(), r.getReference(), r.getSupplierLot().getSupplier().getCanonicalCode() + " " + r.getSupplierLot().getSupplier().getName(),
                    r.getSupplierLot().getLotCodeRaw(), r.getSupplierLot().getIngredientName(), r.getStatus().name(), r.getSeverity(),
                    r.getCreatedAt().toString(), r.getOrderLinesAffected(), r.getOrdersAffected(), r.getCustomersAffected(),
                    r.getFinishedBatchesAffected(), r.getTraversalMillis(), r.getMaxDepth(), r.isCompletenessVerified(),
                    RecallService.minutesBetween(r.getCreatedAt(), r.getLineageComputedAt()),
                    RecallService.minutesBetween(r.getCreatedAt(), r.getAllNotifiedAt()),
                    ns.size(), ns.stream().filter(n -> n.getStatus() != Notification.Status.PENDING).count(),
                    ns.stream().filter(n -> n.getStatus() == Notification.Status.ACKNOWLEDGED).count()));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<IngestionStat> ingestionStats() {
        Map<String, List<IngestionRun>> byFeed = runs.findAll().stream().collect(Collectors.groupingBy(IngestionRun::getFeedType, TreeMap::new, Collectors.toList()));
        List<IngestionStat> out = new ArrayList<>();
        byFeed.forEach((f, rs) -> out.add(new IngestionStat(f, rs.size(),
                rs.stream().mapToLong(IngestionRun::getRowsRead).sum(), rs.stream().mapToLong(IngestionRun::getRowsAccepted).sum(),
                rs.stream().mapToLong(IngestionRun::getRowsUnmatched).sum(), rs.stream().mapToLong(IngestionRun::getRowsRejected).sum(),
                rs.stream().mapToLong(IngestionRun::getRowsDuplicate).sum())));
        return out;
    }
}
