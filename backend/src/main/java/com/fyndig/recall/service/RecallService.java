package com.fyndig.recall.service;

import com.fyndig.recall.entity.*;
import com.fyndig.recall.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecallService {

    public record Completeness(boolean verified, boolean bfsEqualsSql, boolean closureClosed, boolean massBalanced, int unresolvedRelated,
                               int unresolvedTotal, int affectedNodesBfs, int affectedNodesSql, List<String> notes,
                               String affectedOrderLinesSha256) {}

    public record Scope(int supplierLots, int ingredientLots, int wipLots, int finishedBatches, int orderLines,
                        int orders, int customers, String quantityDispatched, int maxDepth, long traversalMillis) {}

    public record AffectedLine(String orderNo, int lineNo, String customerCode, String customerName, String customerEmail,
                               String skuCode, String finishedLotCode, String quantity, String uom, String dispatchDate,
                               String region, int depth, List<String> paths) {}

    public record Simulation(LineageService.NodeView supplierLot, Scope scope, Completeness completeness,
                             List<AffectedLine> affectedLines, LineageService.Graph graph) {}

    private final LineageService lineage;
    private final SupplierLotRepo supplierLots;
    private final InternalLotRepo internalLots;
    private final OrderLineRepo orderLines;
    private final LineageEdgeRepo edges;
    private final UnmatchedRecordRepo unmatched;
    private final RecallEventRepo recalls;
    private final RecallImpactRepo impacts;
    private final NotificationRepo notifications;
    private final SupplierAliasRepo aliases;

    public RecallService(LineageService lineage, SupplierLotRepo supplierLots, InternalLotRepo internalLots,
                         OrderLineRepo orderLines, LineageEdgeRepo edges, UnmatchedRecordRepo unmatched,
                         RecallEventRepo recalls, RecallImpactRepo impacts, NotificationRepo notifications,
                         SupplierAliasRepo aliases) {
        this.lineage = lineage; this.supplierLots = supplierLots; this.internalLots = internalLots;
        this.orderLines = orderLines; this.edges = edges; this.unmatched = unmatched; this.recalls = recalls;
        this.impacts = impacts; this.notifications = notifications; this.aliases = aliases;
    }

    // ------------------------------------------------------------------ simulate

    @Transactional(readOnly = true)
    public Simulation simulate(Long supplierLotId) {
        SupplierLot lot = supplierLots.findById(supplierLotId)
                .orElseThrow(() -> new NoSuchElementException("Supplier lot " + supplierLotId + " not found"));
        LineageService.Graph g = lineage.forward(NodeType.SUPPLIER_LOT, supplierLotId);

        List<AffectedLine> lines = new ArrayList<>();
        Map<String, String> fgByOrderKey = finishedLotForOrderLine(g);
        for (LineageService.NodeView n : g.ofType(NodeType.ORDER_LINE)) {
            OrderLine o = orderLines.findById(n.id()).orElseThrow();
            lines.add(new AffectedLine(o.getOrderNo(), o.getLineNo(), o.getCustomerCode(), o.getCustomerName(), o.getCustomerEmail(),
                    o.getSkuCode(), fgByOrderKey.getOrDefault(n.key(), "?"),
                    o.getQuantity() == null ? null : o.getQuantity().toPlainString(), o.getUom(),
                    o.getDispatchDate() == null ? null : o.getDispatchDate().toString(), o.getShipToRegion(), n.depth(), n.paths()));
        }
        lines.sort(Comparator.comparing(AffectedLine::orderNo).thenComparingInt(AffectedLine::lineNo));

        Scope scope = scopeOf(g, lines);
        Completeness completeness = prove(lot, g, lines);
        return new Simulation(g.root(), scope, completeness, lines, g);
    }

    private Map<String, String> finishedLotForOrderLine(LineageService.Graph g) {
        Map<String, String> codeByKey = new HashMap<>();
        for (LineageService.NodeView n : g.nodes()) codeByKey.put(n.key(), n.code());
        Map<String, String> out = new HashMap<>();
        for (LineageService.EdgeView e : g.edges()) {
            if ("DISPATCH".equals(e.edgeType())) out.merge(e.to(), codeByKey.getOrDefault(e.from(), "?"), (a, b) -> a.contains(b) ? a : a + ", " + b);
        }
        return out;
    }

    private Scope scopeOf(LineageService.Graph g, List<AffectedLine> lines) {
        int ing = 0, wip = 0, fin = 0;
        for (LineageService.NodeView n : g.ofType(NodeType.INTERNAL_LOT)) {
            switch (n.kind()) { case "INGREDIENT" -> ing++; case "WIP" -> wip++; case "FINISHED" -> fin++; default -> {} }
        }
        java.math.BigDecimal qty = java.math.BigDecimal.ZERO;
        for (AffectedLine l : lines) if (l.quantity() != null) qty = qty.add(new java.math.BigDecimal(l.quantity()));
        return new Scope(1, ing, wip, fin, lines.size(),
                (int) lines.stream().map(AffectedLine::orderNo).distinct().count(),
                (int) lines.stream().map(AffectedLine::customerCode).distinct().count(),
                qty.toPlainString(), g.maxDepth(), g.millis());
    }

    // ------------------------------------------------------------------ completeness proof

    /**
     * Three independent checks, all of which must pass for the recall to be marked "completeness verified":
     *  1. The Java BFS closure equals the SQL recursive-CTE closure (two implementations, same answer).
     *  2. The closure is closed: scanning the *whole* edge table, no edge leaves the affected set
     *     (parent in set, child not in set). Catches a traversal that stopped early.
     *  3. No OPEN unmatched records can plausibly belong to this recall (same supplier / any affected lot code).
     *     Unmatched rows elsewhere are reported but do not block, because they cannot reach this lot.
     */
    @Transactional(readOnly = true)
    public Completeness prove(SupplierLot lot, LineageService.Graph g, List<AffectedLine> lines) {
        List<String> notes = new ArrayList<>();
        Set<String> bfs = g.nodes().stream().map(LineageService.NodeView::key).collect(Collectors.toSet());
        Set<String> sql = lineage.forwardClosureSql(NodeType.SUPPLIER_LOT, lot.getId());
        boolean equal = bfs.equals(sql);
        notes.add(equal ? "Java BFS and SQL recursive CTE agree on " + bfs.size() + " downstream nodes."
                : "MISMATCH: BFS reached " + bfs.size() + " nodes, SQL reached " + sql.size() + ". Diff=" + symmetricDiff(bfs, sql));

        Set<String> inSet = new HashSet<>(bfs);
        inSet.add(g.root().key());
        int leaks = 0;
        for (LineageEdge e : edges.findAll()) {
            String p = LineageService.key(e.getParentType(), e.getParentId());
            String c = LineageService.key(e.getChildType(), e.getChildId());
            if (inSet.contains(p) && !inSet.contains(c)) leaks++;
        }
        boolean closed = leaks == 0;
        notes.add(closed ? "Closure check passed: scanned " + edges.count() + " edges, none leave the affected set."
                : "CLOSURE BROKEN: " + leaks + " edge(s) leave the affected set.");

        // unresolved references that could belong to this recall
        Set<String> watch = new HashSet<>();
        watch.add(lot.getSupplier().getCanonicalCode());
        aliases.findBySupplierId(lot.getSupplier().getId()).forEach(a -> { watch.add(a.getAliasNormalized()); watch.add(a.getAliasRaw().toUpperCase()); });
        watch.add(lot.getLotCodeNormalized());
        for (LineageService.NodeView n : g.nodes()) watch.add(n.code().toUpperCase());

        List<UnmatchedRecord> open = unmatched.findByStatus(UnmatchedRecord.Status.OPEN);
        int related = 0, unknownLots = 0;
        for (UnmatchedRecord u : open) {
            String raw = (u.getRawRow() == null ? "" : u.getRawRow()).toUpperCase();
            String val = u.getOffendingValue() == null ? "" : u.getOffendingValue();
            if (!"UNKNOWN_SUPPLIER_CODE".equals(u.getIssue())) {
                // an unknown lot reference can never be ruled out of a recall until it is resolved
                unknownLots++;
                continue;
            }
            String rawNorm = com.fyndig.recall.ingest.Normalizer.lotCode(raw);
            boolean hit = watch.contains(com.fyndig.recall.ingest.Normalizer.supplierCode(val))
                    || raw.contains("\"" + lot.getLotCodeRaw().toUpperCase() + "\"")
                    || rawNorm.contains(lot.getLotCodeNormalized())
                    || watch.stream().anyMatch(w -> w.length() >= 4 && raw.contains("\"" + w + "\""));
            if (hit) related++;
        }
        related += unknownLots;
        if (related > 0) notes.add("BLOCKED: " + related + " unresolved record(s) in the reconciliation queue could belong to this recall ("
                + (related - unknownLots) + " unknown supplier code(s) referencing this supplier/lot, " + unknownLots
                + " unknown lot reference(s) that cannot be ruled out). Resolve them, then recompute.");
        else if (!open.isEmpty()) notes.add(open.size() + " unresolved supplier-code record(s) exist for other suppliers; they cannot reach this supplier lot and do not affect this scope.");
        else notes.add("Reconciliation queue is empty.");

        // mass balance: everything the supplier says it delivered must be accounted for by receipts into the plant
        boolean balanced = true;
        if (lot.getQuantityReceived() != null) {
            java.math.BigDecimal received = java.math.BigDecimal.ZERO;
            for (LineageEdge e : edges.findByParentTypeAndParentId(NodeType.SUPPLIER_LOT, lot.getId()))
                if (e.getEdgeType() == LineageEdge.EdgeType.RECEIPT && e.getQuantity() != null) received = received.add(e.getQuantity());
            java.math.BigDecimal gap = lot.getQuantityReceived().subtract(received);
            java.math.BigDecimal tolerance = lot.getQuantityReceived().multiply(new java.math.BigDecimal("0.005"));
            if (gap.abs().compareTo(tolerance) > 0) {
                balanced = false;
                notes.add("MASS BALANCE GAP: supplier declared " + lot.getQuantityReceived().toPlainString() + " " + lot.getUom()
                        + " for lot " + lot.getLotCodeRaw() + " but plant receipts account for " + received.toPlainString()
                        + " (" + gap.toPlainString() + " unaccounted). Some of this lot entered the plant under a record we cannot link.");
            } else {
                notes.add("Mass balance OK: " + received.toPlainString() + " of " + lot.getQuantityReceived().toPlainString() + " " + lot.getUom() + " received into tracked internal lots.");
            }
        }
        // split balance: children of a SPLIT must not exceed the parent's known quantity
        for (LineageService.NodeView n : g.ofType(NodeType.INTERNAL_LOT)) {
            InternalLot il = internalLots.findById(n.id()).orElse(null);
            if (il == null || il.getQuantity() == null) continue;
            java.math.BigDecimal out = java.math.BigDecimal.ZERO;
            for (LineageEdge e : edges.findByParentTypeAndParentId(NodeType.INTERNAL_LOT, il.getId()))
                if (e.getEdgeType() != LineageEdge.EdgeType.DISPATCH && e.getQuantity() != null && Objects.equals(e.getUom(), il.getUom())) out = out.add(e.getQuantity());
            if (out.compareTo(il.getQuantity().multiply(new java.math.BigDecimal("1.005"))) > 0)
                notes.add("WARNING: lot " + il.getLotCode() + " issues " + out.toPlainString() + " " + il.getUom() + " but only " + il.getQuantity().toPlainString() + " was received (over-consumption - check for a mis-keyed lot).");
        }

        // deterministic fingerprint of the result (used to check against the sealed hidden-scenario answer)
        String fp = sha256(lines.stream().map(l -> l.orderNo() + "/" + l.lineNo()).sorted().collect(Collectors.joining("\n")));
        boolean verified = equal && closed && related == 0 && balanced;
        notes.add("Fingerprint (sha256 of sorted affected order lines): " + fp);
        return new Completeness(verified, equal, closed, balanced, related, open.size(), bfs.size(), sql.size(), notes, fp);
    }

    private static String symmetricDiff(Set<String> a, Set<String> b) {
        Set<String> d = new TreeSet<>(a); d.removeAll(b);
        Set<String> e = new TreeSet<>(b); e.removeAll(a);
        d.addAll(e);
        return d.toString();
    }

    public static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    // ------------------------------------------------------------------ commit / lifecycle

    @Transactional
    public RecallEvent commit(Long supplierLotId, String reason, String severity, boolean simulationOnly) {
        LocalDateTime created = LocalDateTime.now();
        Simulation sim = simulate(supplierLotId);
        RecallEvent r = new RecallEvent();
        r.setCreatedAt(created);
        r.setReference(String.format("RC-%d-%04d", created.getYear(), recalls.count() + 1));
        r.setReason(reason == null || reason.isBlank() ? "Supplier-initiated ingredient recall" : reason);
        r.setSeverity(severity == null || severity.isBlank() ? "CLASS_II" : severity);
        r.setSupplierLot(supplierLots.findById(supplierLotId).orElseThrow());
        r.setStatus(simulationOnly ? RecallEvent.Status.SIMULATED : RecallEvent.Status.OPEN);
        r.setLineageComputedAt(LocalDateTime.now());
        r.setInternalLotsAffected(sim.scope().ingredientLots() + sim.scope().wipLots() + sim.scope().finishedBatches());
        r.setFinishedBatchesAffected(sim.scope().finishedBatches());
        r.setOrderLinesAffected(sim.scope().orderLines());
        r.setOrdersAffected(sim.scope().orders());
        r.setCustomersAffected(sim.scope().customers());
        r.setUnresolvedReferences(sim.completeness().unresolvedRelated());
        r.setTraversalMillis(sim.scope().traversalMillis());
        r.setMaxDepth(sim.scope().maxDepth());
        r.setCompletenessVerified(sim.completeness().verified());
        r.setCompletenessNotes(String.join("\n", sim.completeness().notes()));
        r = recalls.save(r);

        for (AffectedLine l : sim.affectedLines()) {
            RecallImpact i = new RecallImpact();
            i.setRecall(r);
            i.setOrderLine(orderLines.findByOrderNoAndLineNo(l.orderNo(), l.lineNo()).orElseThrow());
            i.setFinishedLotCode(l.finishedLotCode());
            i.setDepth(l.depth());
            String path = l.paths().isEmpty() ? "" : String.join(" || ", l.paths());
            i.setLineagePath(path.length() > 1990 ? path.substring(0, 1990) + "…" : path);
            impacts.save(i);
        }
        if (!simulationOnly) {
            Map<String, List<AffectedLine>> byCustomer = sim.affectedLines().stream()
                    .collect(Collectors.groupingBy(AffectedLine::customerCode, TreeMap::new, Collectors.toList()));
            for (Map.Entry<String, List<AffectedLine>> e : byCustomer.entrySet()) {
                Notification n = new Notification();
                n.setRecall(r);
                n.setCustomerCode(e.getKey());
                n.setCustomerName(e.getValue().get(0).customerName());
                n.setCustomerEmail(e.getValue().get(0).customerEmail());
                n.setOrderLinesAffected(e.getValue().size());
                notifications.save(n);
            }
            r.setStatus(byCustomer.isEmpty() ? RecallEvent.Status.CLOSED : RecallEvent.Status.NOTIFYING);
            if (byCustomer.isEmpty()) { r.setAllNotifiedAt(LocalDateTime.now()); r.setClosedAt(LocalDateTime.now()); }
        }
        return recalls.save(r);
    }

    @Transactional
    public Notification updateNotification(Long notificationId, Notification.Status status, String note) {
        Notification n = notifications.findById(notificationId).orElseThrow();
        n.setStatus(status);
        if (note != null) n.setNote(note);
        LocalDateTime now = LocalDateTime.now();
        if (status == Notification.Status.SENT && n.getSentAt() == null) n.setSentAt(now);
        if (status == Notification.Status.ACKNOWLEDGED) { if (n.getSentAt() == null) n.setSentAt(now); n.setAcknowledgedAt(now); }
        notifications.save(n);

        RecallEvent r = n.getRecall();
        if (notifications.countByRecall_IdAndStatusNot(r.getId(), Notification.Status.PENDING) == notifications.findByRecall_Id(r.getId()).size()
                && r.getAllNotifiedAt() == null) {
            r.setAllNotifiedAt(now);
            recalls.save(r);
        }
        return n;
    }

    @Transactional
    public RecallEvent close(Long recallId) {
        RecallEvent r = recalls.findById(recallId).orElseThrow();
        r.setStatus(RecallEvent.Status.CLOSED);
        r.setClosedAt(LocalDateTime.now());
        return recalls.save(r);
    }

    /** Re-run lineage for an existing recall (after reconciliation changed the graph). */
    @Transactional
    public RecallEvent recompute(Long recallId) {
        RecallEvent r = recalls.findById(recallId).orElseThrow();
        Simulation sim = simulate(r.getSupplierLot().getId());
        impacts.deleteByRecall_Id(recallId);
        for (AffectedLine l : sim.affectedLines()) {
            RecallImpact i = new RecallImpact();
            i.setRecall(r);
            i.setOrderLine(orderLines.findByOrderNoAndLineNo(l.orderNo(), l.lineNo()).orElseThrow());
            i.setFinishedLotCode(l.finishedLotCode());
            i.setDepth(l.depth());
            i.setLineagePath(String.join(" || ", l.paths()));
            impacts.save(i);
        }
        // add notifications for newly affected customers
        Set<String> notified = notifications.findByRecall_Id(recallId).stream().map(Notification::getCustomerCode).collect(Collectors.toSet());
        if (r.getStatus() != RecallEvent.Status.SIMULATED) {
            sim.affectedLines().stream().collect(Collectors.groupingBy(AffectedLine::customerCode)).forEach((cust, ls) -> {
                if (!notified.contains(cust)) {
                    Notification n = new Notification();
                    n.setRecall(r); n.setCustomerCode(cust); n.setCustomerName(ls.get(0).customerName());
                    n.setCustomerEmail(ls.get(0).customerEmail()); n.setOrderLinesAffected(ls.size());
                    notifications.save(n);
                    r.setAllNotifiedAt(null);
                }
            });
        }
        r.setLineageComputedAt(LocalDateTime.now());
        r.setInternalLotsAffected(sim.scope().ingredientLots() + sim.scope().wipLots() + sim.scope().finishedBatches());
        r.setFinishedBatchesAffected(sim.scope().finishedBatches());
        r.setOrderLinesAffected(sim.scope().orderLines());
        r.setOrdersAffected(sim.scope().orders());
        r.setCustomersAffected(sim.scope().customers());
        r.setUnresolvedReferences(sim.completeness().unresolvedRelated());
        r.setTraversalMillis(sim.scope().traversalMillis());
        r.setMaxDepth(sim.scope().maxDepth());
        r.setCompletenessVerified(sim.completeness().verified());
        r.setCompletenessNotes(String.join("\n", sim.completeness().notes()));
        return recalls.save(r);
    }

    public static Long minutesBetween(LocalDateTime a, LocalDateTime b) {
        if (a == null || b == null) return null;
        return Duration.between(a, b).toMinutes();
    }
}
