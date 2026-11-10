package com.fyndig.recall.web;

import com.fyndig.recall.entity.*;
import com.fyndig.recall.ingest.FeedType;
import com.fyndig.recall.ingest.IngestionService;
import com.fyndig.recall.repo.*;
import com.fyndig.recall.service.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class ApiController {

    private final IngestionService ingestion;
    private final LineageService lineage;
    private final RecallService recallService;
    private final ReconciliationService reconciliation;
    private final AnalyticsService analytics;
    private final SupplierRepo suppliers; private final SupplierLotRepo supplierLots; private final InternalLotRepo internalLots;
    private final OrderLineRepo orderLines; private final RecallEventRepo recalls; private final RecallImpactRepo impacts;
    private final NotificationRepo notifications; private final UnmatchedRecordRepo unmatched; private final IngestionRunRepo runs;
    private final SupplierAliasRepo aliases;

    public ApiController(IngestionService ingestion, LineageService lineage, RecallService recallService, ReconciliationService reconciliation,
                         AnalyticsService analytics, SupplierRepo suppliers, SupplierLotRepo supplierLots, InternalLotRepo internalLots,
                         OrderLineRepo orderLines, RecallEventRepo recalls, RecallImpactRepo impacts, NotificationRepo notifications,
                         UnmatchedRecordRepo unmatched, IngestionRunRepo runs, SupplierAliasRepo aliases) {
        this.ingestion = ingestion; this.lineage = lineage; this.recallService = recallService; this.reconciliation = reconciliation;
        this.analytics = analytics; this.suppliers = suppliers; this.supplierLots = supplierLots; this.internalLots = internalLots;
        this.orderLines = orderLines; this.recalls = recalls; this.impacts = impacts; this.notifications = notifications;
        this.unmatched = unmatched; this.runs = runs; this.aliases = aliases;
    }

    // ------------------------------------------------------------------ ingestion

    @GetMapping("/ingest/feeds")
    public List<Map<String, Object>> feeds() {
        return Arrays.stream(FeedType.values()).map(f -> Map.<String, Object>of(
                "name", f.name(), "description", f.description, "required", f.required,
                "columns", f.columnSynonyms)).toList();
    }

    @PostMapping(value = "/ingest/{feed}", consumes = "multipart/form-data")
    public IngestionRun ingest(@PathVariable FeedType feed, @RequestParam("file") MultipartFile file,
                               @RequestParam(value = "sourceSystem", required = false) String sourceSystem) throws IOException {
        return ingestion.ingest(feed, sourceSystem, file.getOriginalFilename(), file.getInputStream());
    }

    /** Raw text body (CSV or JSON) - handy for curl / CI. */
    @PostMapping(value = "/ingest/{feed}/raw", consumes = {"text/plain", "text/csv", "application/json"})
    public IngestionRun ingestRaw(@PathVariable FeedType feed, @RequestBody String body,
                                  @RequestParam(value = "sourceSystem", required = false) String sourceSystem,
                                  @RequestParam(value = "fileName", required = false) String fileName) throws IOException {
        return ingestion.ingest(feed, sourceSystem, fileName, new java.io.ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @GetMapping("/ingest/runs")
    public List<IngestionRun> runs() { return runs.findAllByOrderByStartedAtDesc(); }

    // ------------------------------------------------------------------ master data

    @GetMapping("/suppliers")
    public List<Map<String, Object>> suppliers() {
        return suppliers.findAll().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId()); m.put("canonicalCode", s.getCanonicalCode()); m.put("name", s.getName());
            m.put("country", s.getCountry()); m.put("contactEmail", s.getContactEmail());
            m.put("aliases", aliases.findBySupplierId(s.getId()).stream().map(a -> a.getAliasRaw() + " (" + a.getResolutionType() + ")").toList());
            m.put("lots", supplierLots.findBySupplierId(s.getId()).size());
            return m;
        }).toList();
    }

    @GetMapping("/supplier-lots")
    public List<SupplierLot> supplierLots(@RequestParam(value = "q", required = false) String q) {
        return q == null || q.isBlank() ? supplierLots.findAll() : supplierLots.search(q);
    }

    @GetMapping("/internal-lots")
    public List<InternalLot> internalLots(@RequestParam(value = "q", required = false) String q,
                                          @RequestParam(value = "kind", required = false) InternalLot.Kind kind) {
        if (kind != null) return internalLots.findByKind(kind);
        return q == null || q.isBlank() ? internalLots.findAll() : internalLots.search(q);
    }

    @GetMapping("/orders")
    public List<OrderLine> orders(@RequestParam(value = "orderNo", required = false) String orderNo,
                                  @RequestParam(value = "customer", required = false) String customer) {
        if (orderNo != null && !orderNo.isBlank()) return orderLines.findByOrderNo(orderNo.trim().toUpperCase());
        if (customer != null && !customer.isBlank()) return orderLines.findByCustomerCode(customer.trim().toUpperCase());
        return orderLines.findAll();
    }

    // ------------------------------------------------------------------ lineage

    @GetMapping("/lineage/forward/{type}/{id}")
    public LineageService.Graph forward(@PathVariable NodeType type, @PathVariable Long id) { return lineage.forward(type, id); }

    @GetMapping("/lineage/backward/{type}/{id}")
    public LineageService.Graph backward(@PathVariable NodeType type, @PathVariable Long id) { return lineage.backward(type, id); }

    /** Trace an order back to every supplier lot that fed it. */
    @GetMapping("/lineage/order/{orderNo}")
    public Map<String, Object> traceOrder(@PathVariable String orderNo) {
        List<OrderLine> lines = orderLines.findByOrderNo(orderNo.trim().toUpperCase());
        if (lines.isEmpty()) throw new NoSuchElementException("Order " + orderNo + " not found");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orderNo", orderNo.toUpperCase());
        out.put("lines", lines.stream().map(l -> Map.of("line", l, "lineage", lineage.backward(NodeType.ORDER_LINE, l.getId()))).toList());
        return out;
    }

    // ------------------------------------------------------------------ recall

    public record RecallRequest(Long supplierLotId, String reason, String severity, Boolean simulationOnly) {}

    @GetMapping("/recalls/simulate/{supplierLotId}")
    public RecallService.Simulation simulate(@PathVariable Long supplierLotId) { return recallService.simulate(supplierLotId); }

    @PostMapping("/recalls")
    @ResponseStatus(HttpStatus.CREATED)
    public RecallEvent create(@RequestBody RecallRequest req) {
        return recallService.commit(req.supplierLotId(), req.reason(), req.severity(), Boolean.TRUE.equals(req.simulationOnly()));
    }

    @GetMapping("/recalls")
    public List<AnalyticsService.RecallRow> recalls() { return analytics.recallTable(); }

    @GetMapping("/recalls/{id}")
    public Map<String, Object> recall(@PathVariable Long id) {
        RecallEvent r = recalls.findById(id).orElseThrow(() -> new NoSuchElementException("Recall " + id + " not found"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("recall", r);
        out.put("impacts", impacts.findByRecall_Id(id));
        out.put("notifications", notifications.findByRecall_Id(id));
        out.put("minutesToLineage", RecallService.minutesBetween(r.getCreatedAt(), r.getLineageComputedAt()));
        out.put("minutesToAllNotified", RecallService.minutesBetween(r.getCreatedAt(), r.getAllNotifiedAt()));
        return out;
    }

    @PostMapping("/recalls/{id}/recompute")
    public RecallEvent recompute(@PathVariable Long id) { return recallService.recompute(id); }

    @PostMapping("/recalls/{id}/close")
    public RecallEvent close(@PathVariable Long id) { return recallService.close(id); }

    /** Affected-order search across all committed recalls. */
    @GetMapping("/affected-orders")
    public List<Map<String, Object>> affectedOrders(@RequestParam(value = "orderNo", required = false) String orderNo,
                                                    @RequestParam(value = "customer", required = false) String customer,
                                                    @RequestParam(value = "recallId", required = false) Long recallId) {
        List<RecallImpact> list;
        if (recallId != null) list = impacts.findByRecall_Id(recallId);
        else if (orderNo != null && !orderNo.isBlank()) list = impacts.findByOrderLineOrderNo(orderNo.trim().toUpperCase());
        else if (customer != null && !customer.isBlank()) list = impacts.findByOrderLineCustomerCode(customer.trim().toUpperCase());
        else list = impacts.findAll();
        return list.stream().map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("recallId", i.getRecall().getId()); m.put("recallRef", i.getRecall().getReference());
            m.put("recallStatus", i.getRecall().getStatus()); m.put("severity", i.getRecall().getSeverity());
            m.put("orderNo", i.getOrderLine().getOrderNo()); m.put("lineNo", i.getOrderLine().getLineNo());
            m.put("customerCode", i.getOrderLine().getCustomerCode()); m.put("customerName", i.getOrderLine().getCustomerName());
            m.put("skuCode", i.getOrderLine().getSkuCode()); m.put("quantity", i.getOrderLine().getQuantity());
            m.put("dispatchDate", i.getOrderLine().getDispatchDate()); m.put("region", i.getOrderLine().getShipToRegion());
            m.put("finishedLotCode", i.getFinishedLotCode()); m.put("depth", i.getDepth()); m.put("lineagePath", i.getLineagePath());
            return m;
        }).toList();
    }

    // ------------------------------------------------------------------ notifications

    public record NotificationUpdate(Notification.Status status, String note) {}

    @GetMapping("/notifications")
    public List<Notification> notifications(@RequestParam(value = "recallId", required = false) Long recallId) {
        return recallId == null ? notifications.findAll() : notifications.findByRecall_Id(recallId);
    }

    @PostMapping("/notifications/{id}")
    public Notification updateNotification(@PathVariable Long id, @RequestBody NotificationUpdate u) {
        return recallService.updateNotification(id, u.status(), u.note());
    }

    @PostMapping("/notifications/send-all/{recallId}")
    public List<Notification> sendAll(@PathVariable Long recallId) {
        return notifications.findByRecall_Id(recallId).stream()
                .filter(n -> n.getStatus() == Notification.Status.PENDING)
                .map(n -> recallService.updateNotification(n.getId(), Notification.Status.SENT, "Sent via " + n.getChannel() + " (simulated gateway)"))
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------ reconciliation

    public record ResolveRequest(String resolveTo, String resolvedBy) {}

    @GetMapping("/reconciliation")
    public List<UnmatchedRecord> reconciliation(@RequestParam(value = "status", defaultValue = "OPEN") UnmatchedRecord.Status status) {
        return unmatched.findByStatus(status);
    }

    @GetMapping("/reconciliation/{id}/suggest")
    public List<ReconciliationService.Suggestion> suggest(@PathVariable Long id) { return reconciliation.suggest(id); }

    @PostMapping("/reconciliation/{id}/resolve")
    public ReconciliationService.Resolution resolve(@PathVariable Long id, @RequestBody ResolveRequest req) throws IOException {
        return reconciliation.resolve(id, req.resolveTo(), req.resolvedBy());
    }

    @PostMapping("/reconciliation/{id}/ignore")
    public UnmatchedRecord ignore(@PathVariable Long id, @RequestParam(value = "by", defaultValue = "operator") String by) {
        return reconciliation.ignore(id, by);
    }

    // ------------------------------------------------------------------ analytics

    @GetMapping("/analytics/kpis")
    public AnalyticsService.Kpis kpis() { return analytics.kpis(); }

    @GetMapping("/analytics/supplier-impact")
    public List<AnalyticsService.SupplierImpact> supplierImpact() { return analytics.supplierImpact(); }

    @GetMapping("/analytics/ingestion")
    public List<AnalyticsService.IngestionStat> ingestionStats() { return analytics.ingestionStats(); }

    // ------------------------------------------------------------------ errors

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage() == null ? "not found" : e.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IOException.class})
    public ResponseEntity<Map<String, String>> bad(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
    }
}
