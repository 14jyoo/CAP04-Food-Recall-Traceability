package com.fyndig.recall.ingest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fyndig.recall.entity.*;
import com.fyndig.recall.repo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Validates and loads each feed into the lineage graph.
 *
 * Rules (also documented in docs/INGESTION_RULES.md):
 *  - required fields present, dates/quantities parseable, quantities > 0        -> else REJECT (row never enters the graph)
 *  - same source record id already loaded                                        -> DUPLICATE (skipped, counted)
 *  - supplier code resolves via canonical code or alias table                    -> else UNMATCHED (row parked for reconciliation,
 *                                                                                   graph node still created where possible so the
 *                                                                                   downstream lineage is kept; the missing edge is
 *                                                                                   added when the code is resolved and the row replayed)
 *  - internal lot referenced before it was ever registered                       -> UNMATCHED (UNKNOWN_INTERNAL_LOT)
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final SupplierRepo suppliers;
    private final SupplierAliasRepo aliases;
    private final SupplierLotRepo supplierLots;
    private final InternalLotRepo internalLots;
    private final OrderLineRepo orderLines;
    private final LineageEdgeRepo edges;
    private final UnmatchedRecordRepo unmatched;
    private final IngestionRunRepo runs;

    public IngestionService(SupplierRepo suppliers, SupplierAliasRepo aliases, SupplierLotRepo supplierLots,
                            InternalLotRepo internalLots, OrderLineRepo orderLines, LineageEdgeRepo edges,
                            UnmatchedRecordRepo unmatched, IngestionRunRepo runs) {
        this.suppliers = suppliers; this.aliases = aliases; this.supplierLots = supplierLots;
        this.internalLots = internalLots; this.orderLines = orderLines; this.edges = edges;
        this.unmatched = unmatched; this.runs = runs;
    }

    // ---------------------------------------------------------------- public API

    public record RowResult(int row, String outcome, String message) {}

    @Transactional
    public IngestionRun ingest(FeedType feed, String sourceSystem, String fileName, InputStream in) throws IOException {
        RowParser.ParsedFile parsed = RowParser.parse(feed, fileName, in);
        IngestionRun run = new IngestionRun();
        run.setFeedType(feed.name());
        run.setSourceSystem(sourceSystem == null ? "UNKNOWN" : sourceSystem);
        run.setFileName(fileName);
        run.setRowsRead(parsed.rows().size());

        List<RowResult> report = new ArrayList<>();
        if (!parsed.unmappedHeaders().isEmpty()) {
            report.add(new RowResult(0, "WARN", "Ignored unknown columns: " + parsed.unmappedHeaders()));
        }
        int rowNo = 0;
        for (Map<String, String> row : parsed.rows()) {
            rowNo++;
            RowResult r;
            try {
                r = ingestRow(feed, run.getSourceSystem(), rowNo, row);
            } catch (Exception ex) {
                r = new RowResult(rowNo, "REJECTED", ex.getMessage());
            }
            switch (r.outcome()) {
                case "ACCEPTED" -> run.setRowsAccepted(run.getRowsAccepted() + 1);
                case "DUPLICATE" -> run.setRowsDuplicate(run.getRowsDuplicate() + 1);
                case "UNMATCHED" -> run.setRowsUnmatched(run.getRowsUnmatched() + 1);
                default -> run.setRowsRejected(run.getRowsRejected() + 1);
            }
            if (!"ACCEPTED".equals(r.outcome())) report.add(r);
        }
        run.setFinishedAt(LocalDateTime.now());
        String json = JSON.writeValueAsString(report);
        run.setValidationReport(json.length() > 7900 ? json.substring(0, 7900) + "...]" : json);
        log.info("Ingested {} ({}): read={} accepted={} unmatched={} rejected={} duplicate={}", feed, parsed.format(),
                run.getRowsRead(), run.getRowsAccepted(), run.getRowsUnmatched(), run.getRowsRejected(), run.getRowsDuplicate());
        return runs.save(run);
    }

    /** Re-run a parked row after reconciliation resolved its problem. */
    @Transactional
    public RowResult replay(UnmatchedRecord rec) throws IOException {
        Map<String, String> row = JSON.readValue(rec.getRawRow(), new TypeReference<>() {});
        FeedType feed = FeedType.valueOf(rec.getRecordType());
        return ingestRow(feed, rec.getSourceSystem(), 0, row);
    }

    // ---------------------------------------------------------------- per-row dispatch

    private RowResult ingestRow(FeedType feed, String source, int rowNo, Map<String, String> row) throws IOException {
        for (String req : feed.required) {
            if (isBlank(row.get(req))) return new RowResult(rowNo, "REJECTED", "Missing required field '" + req + "'");
        }
        return switch (feed) {
            case SUPPLIERS -> ingestSupplier(rowNo, row);
            case SUPPLIER_LOTS -> ingestSupplierLot(source, rowNo, row);
            case RECEIPTS -> ingestReceipt(source, rowNo, row);
            case MOVEMENTS -> ingestMovement(source, rowNo, row);
            case DISPATCHES -> ingestDispatch(source, rowNo, row);
        };
    }

    private RowResult ingestSupplier(int rowNo, Map<String, String> row) {
        String canonical = Normalizer.supplierCode(row.get("canonicalCode"));
        Supplier s = suppliers.findByCanonicalCode(canonical).orElseGet(Supplier::new);
        s.setCanonicalCode(canonical);
        s.setName(row.get("name"));
        s.setCountry(row.get("country"));
        s.setContactEmail(row.get("contactEmail"));
        s = suppliers.save(s);
        addAlias(s, row.get("canonicalCode"), "AUTO");
        if (!isBlank(row.get("aliases"))) {
            for (String a : row.get("aliases").split("[|;]")) if (!a.isBlank()) addAlias(s, a.trim(), "AUTO");
        }
        return new RowResult(rowNo, "ACCEPTED", null);
    }

    private RowResult ingestSupplierLot(String source, int rowNo, Map<String, String> row) throws IOException {
        String recordId = recordId(row, "SL");
        Supplier supplier = resolveSupplier(row.get("supplierCode"));
        if (supplier == null) return park(source, FeedType.SUPPLIER_LOTS, recordId, "UNKNOWN_SUPPLIER_CODE", row.get("supplierCode"), row, rowNo);

        String norm = Normalizer.lotCode(row.get("lotCode"));
        Optional<SupplierLot> existing = supplierLots.findBySupplierIdAndLotCodeNormalized(supplier.getId(), norm);
        if (existing.isPresent() && recordId.equals(existing.get().getSourceRecordId()))
            return new RowResult(rowNo, "DUPLICATE", "Supplier lot already loaded from record " + recordId);

        BigDecimal qty = Normalizer.quantity(row.get("quantity"));
        if (qty != null && qty.signum() <= 0) return new RowResult(rowNo, "REJECTED", "Quantity must be > 0");

        SupplierLot lot = existing.orElseGet(SupplierLot::new);
        lot.setSupplier(supplier);
        lot.setLotCodeRaw(row.get("lotCode").trim());
        lot.setLotCodeNormalized(norm);
        lot.setIngredientCode(Normalizer.internalCode(row.get("ingredientCode")));
        if (!isBlank(row.get("ingredientName"))) lot.setIngredientName(row.get("ingredientName"));
        if (qty != null) lot.setQuantityReceived(qty);
        lot.setUom(Normalizer.uom(row.get("uom")));
        lot.setReceivedDate(Normalizer.date(row.get("receivedDate")));
        lot.setExpiryDate(Normalizer.date(row.get("expiryDate")));
        lot.setSourceSystem(source);
        lot.setSourceRecordId(recordId);
        supplierLots.save(lot);
        return new RowResult(rowNo, "ACCEPTED", null);
    }

    private RowResult ingestReceipt(String source, int rowNo, Map<String, String> row) throws IOException {
        String recordId = recordId(row, "GRN");
        BigDecimal qty = Normalizer.quantity(row.get("quantity"));
        if (qty != null && qty.signum() <= 0) return new RowResult(rowNo, "REJECTED", "Quantity must be > 0");
        LocalDateTime at = Normalizer.dateTime(row.get("receivedAt"));

        // The internal lot is created regardless, so that production/dispatch data referencing it keeps linking.
        InternalLot internal = upsertInternalLot(Normalizer.internalCode(row.get("internalLotCode")), InternalLot.Kind.INGREDIENT,
                Normalizer.internalCode(row.get("ingredientCode")), row.get("ingredientName"), qty, Normalizer.uom(row.get("uom")),
                at == null ? null : at.toLocalDate(), null, source, recordId);

        Supplier supplier = resolveSupplier(row.get("supplierCode"));
        if (supplier == null) return park(source, FeedType.RECEIPTS, recordId, "UNKNOWN_SUPPLIER_CODE", row.get("supplierCode"), row, rowNo);

        String norm = Normalizer.lotCode(row.get("supplierLotCode"));
        SupplierLot supLot = supplierLots.findBySupplierIdAndLotCodeNormalized(supplier.getId(), norm).orElse(null);
        if (supLot == null) {
            // Supplier never registered the lot but the plant received it: auto-register it (flagged) so lineage is not lost.
            supLot = new SupplierLot();
            supLot.setSupplier(supplier);
            supLot.setLotCodeRaw(row.get("supplierLotCode").trim());
            supLot.setLotCodeNormalized(norm);
            supLot.setIngredientCode(Normalizer.internalCode(row.get("ingredientCode")));
            supLot.setIngredientName(row.get("ingredientName"));
            supLot.setQuantityReceived(qty);
            supLot.setUom(Normalizer.uom(row.get("uom")));
            supLot.setReceivedDate(at == null ? null : at.toLocalDate());
            supLot.setSourceSystem(source + "(auto-from-receipt)");
            supLot.setSourceRecordId(recordId);
            supLot = supplierLots.save(supLot);
        }
        return addEdge(NodeType.SUPPLIER_LOT, supLot.getId(), NodeType.INTERNAL_LOT, internal.getId(),
                LineageEdge.EdgeType.RECEIPT, qty, Normalizer.uom(row.get("uom")), at, source, recordId, rowNo);
    }

    private RowResult ingestMovement(String source, int rowNo, Map<String, String> row) throws IOException {
        String recordId = recordId(row, "MOV");
        String type = row.get("movementType").trim().toUpperCase(Locale.ROOT);
        LineageEdge.EdgeType edgeType = switch (type) {
            case "SPLIT", "DIVIDE", "REPACK" -> LineageEdge.EdgeType.SPLIT;
            case "MERGE", "COMBINE", "BLEND" -> LineageEdge.EdgeType.MERGE;
            case "REWORK" -> LineageEdge.EdgeType.REWORK;
            case "CONSUMPTION", "CONSUME", "ISSUE", "PRODUCE", "PRODUCTION", "USE" -> LineageEdge.EdgeType.CONSUMPTION;
            default -> null;
        };
        if (edgeType == null) return new RowResult(rowNo, "REJECTED", "Unknown movement type '" + type + "'");

        BigDecimal qty = Normalizer.quantity(row.get("quantity"));
        if (qty != null && qty.signum() <= 0) return new RowResult(rowNo, "REJECTED", "Quantity must be > 0");
        LocalDateTime at = Normalizer.dateTime(row.get("eventTime"));

        String fromCode = Normalizer.internalCode(row.get("fromLotCode"));
        InternalLot from = internalLots.findByLotCode(fromCode).orElse(null);
        if (from == null) return park(source, FeedType.MOVEMENTS, recordId, "UNKNOWN_INTERNAL_LOT", fromCode, row, rowNo);

        InternalLot.Kind kind = kindFor(row.get("toLotKind"), edgeType);
        InternalLot to = upsertInternalLot(Normalizer.internalCode(row.get("toLotCode")), kind,
                isBlank(row.get("materialCode")) ? from.getMaterialCode() : Normalizer.internalCode(row.get("materialCode")),
                isBlank(row.get("materialName")) ? from.getMaterialName() : row.get("materialName"),
                null, Normalizer.uom(row.get("uom")), at == null ? null : at.toLocalDate(), row.get("productionLine"), source, recordId);

        return addEdge(NodeType.INTERNAL_LOT, from.getId(), NodeType.INTERNAL_LOT, to.getId(), edgeType, qty,
                Normalizer.uom(row.get("uom")), at, source, recordId, rowNo);
    }

    private RowResult ingestDispatch(String source, int rowNo, Map<String, String> row) throws IOException {
        String recordId = recordId(row, "DSP");
        BigDecimal qty = Normalizer.quantity(row.get("quantity"));
        if (qty != null && qty.signum() <= 0) return new RowResult(rowNo, "REJECTED", "Quantity must be > 0");
        int lineNo = isBlank(row.get("lineNo")) ? 1 : Integer.parseInt(row.get("lineNo").trim());
        String orderNo = row.get("orderNo").trim().toUpperCase(Locale.ROOT);

        OrderLine ol = orderLines.findByOrderNoAndLineNo(orderNo, lineNo).orElseGet(OrderLine::new);
        ol.setOrderNo(orderNo);
        ol.setLineNo(lineNo);
        ol.setCustomerCode(row.get("customerCode").trim().toUpperCase(Locale.ROOT));
        if (!isBlank(row.get("customerName"))) ol.setCustomerName(row.get("customerName"));
        if (!isBlank(row.get("customerEmail"))) ol.setCustomerEmail(row.get("customerEmail"));
        if (!isBlank(row.get("skuCode"))) ol.setSkuCode(Normalizer.internalCode(row.get("skuCode")));
        if (qty != null) ol.setQuantity(qty);
        ol.setUom(Normalizer.uom(row.get("uom")));
        ol.setDispatchDate(Normalizer.date(row.get("dispatchDate")));
        ol.setShipToRegion(row.get("region"));
        ol.setSourceSystem(source);
        ol.setSourceRecordId(recordId);
        ol = orderLines.save(ol);

        String fg = Normalizer.internalCode(row.get("finishedLotCode"));
        InternalLot batch = internalLots.findByLotCode(fg).orElse(null);
        if (batch == null) return park(source, FeedType.DISPATCHES, recordId, "UNKNOWN_FINISHED_LOT", fg, row, rowNo);
        if (ol.getSkuCode() == null) { ol.setSkuCode(batch.getMaterialCode()); orderLines.save(ol); }

        LocalDateTime at = ol.getDispatchDate() == null ? null : ol.getDispatchDate().atStartOfDay();
        return addEdge(NodeType.INTERNAL_LOT, batch.getId(), NodeType.ORDER_LINE, ol.getId(), LineageEdge.EdgeType.DISPATCH,
                qty, ol.getUom(), at, source, recordId, rowNo);
    }

    // ---------------------------------------------------------------- helpers

    private Supplier resolveSupplier(String raw) {
        if (isBlank(raw)) return null;
        String norm = Normalizer.supplierCode(raw);
        Optional<Supplier> byCanonical = suppliers.findByCanonicalCode(norm);
        if (byCanonical.isPresent()) return byCanonical.get();
        return aliases.findByAliasNormalized(norm).map(SupplierAlias::getSupplier)
                .or(() -> aliases.findByAliasNormalized(raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", ""))
                        .map(SupplierAlias::getSupplier))
                .orElse(null);
    }

    @Transactional
    public SupplierAlias addAlias(Supplier s, String raw, String resolutionType) {
        String norm = Normalizer.supplierCode(raw);
        return aliases.findByAliasNormalized(norm).orElseGet(() -> {
            SupplierAlias a = new SupplierAlias();
            a.setAliasRaw(raw);
            a.setAliasNormalized(norm);
            a.setSupplier(s);
            a.setResolutionType(resolutionType);
            return aliases.save(a);
        });
    }

    private InternalLot upsertInternalLot(String code, InternalLot.Kind kind, String materialCode, String materialName,
                                          BigDecimal qty, String uom, java.time.LocalDate date, String line,
                                          String source, String recordId) {
        InternalLot lot = internalLots.findByLotCode(code).orElseGet(InternalLot::new);
        boolean isNew = lot.getId() == null;
        lot.setLotCode(code);
        // once a lot is known to be FINISHED it stays FINISHED; otherwise take the most specific kind we have seen
        if (isNew || lot.getKind() == null || kind == InternalLot.Kind.FINISHED) lot.setKind(kind);
        if (materialCode != null) lot.setMaterialCode(materialCode);
        if (materialName != null) lot.setMaterialName(materialName);
        if (qty != null) lot.setQuantity(qty);
        if (uom != null) lot.setUom(uom);
        if (date != null && lot.getProducedDate() == null) lot.setProducedDate(date);
        if (line != null) lot.setProductionLine(line);
        if (isNew) { lot.setSourceSystem(source); lot.setSourceRecordId(recordId); }
        return internalLots.save(lot);
    }

    private static InternalLot.Kind kindFor(String raw, LineageEdge.EdgeType edgeType) {
        if (!isBlank(raw)) {
            String k = raw.trim().toUpperCase(Locale.ROOT);
            if (k.startsWith("FIN") || k.equals("FG") || k.equals("SKU") || k.equals("PRODUCT")) return InternalLot.Kind.FINISHED;
            if (k.startsWith("ING") || k.equals("RM") || k.equals("RAW")) return InternalLot.Kind.INGREDIENT;
            return InternalLot.Kind.WIP;
        }
        return edgeType == LineageEdge.EdgeType.CONSUMPTION ? InternalLot.Kind.FINISHED : InternalLot.Kind.WIP;
    }

    private RowResult addEdge(NodeType pt, Long pid, NodeType ct, Long cid, LineageEdge.EdgeType type, BigDecimal qty,
                              String uom, LocalDateTime at, String source, String recordId, int rowNo) {
        if (edges.existsByParentTypeAndParentIdAndChildTypeAndChildIdAndSourceRecordId(pt, pid, ct, cid, recordId))
            return new RowResult(rowNo, "DUPLICATE", "Movement already loaded from record " + recordId);
        LineageEdge e = new LineageEdge();
        e.setParentType(pt); e.setParentId(pid); e.setChildType(ct); e.setChildId(cid);
        e.setEdgeType(type); e.setQuantity(qty); e.setUom(uom); e.setEventTime(at);
        e.setSourceSystem(source); e.setSourceRecordId(recordId);
        edges.save(e);
        return new RowResult(rowNo, "ACCEPTED", null);
    }

    private RowResult park(String source, FeedType feed, String recordId, String issue, String value,
                           Map<String, String> row, int rowNo) throws IOException {
        // do not park the same record twice
        for (UnmatchedRecord u : unmatched.findByStatusAndOffendingValue(UnmatchedRecord.Status.OPEN, value)) {
            if (u.getSourceRecordId().equals(recordId) && u.getRecordType().equals(feed.name()))
                return new RowResult(rowNo, "UNMATCHED", issue + ": '" + value + "' (already in reconciliation queue)");
        }
        UnmatchedRecord u = new UnmatchedRecord();
        u.setSourceSystem(source);
        u.setSourceRecordId(recordId);
        u.setRecordType(feed.name());
        u.setIssue(issue);
        u.setOffendingValue(value);
        u.setRawRow(JSON.writeValueAsString(row));
        unmatched.save(u);
        return new RowResult(rowNo, "UNMATCHED", issue + ": '" + value + "' parked for reconciliation");
    }

    private static String recordId(Map<String, String> row, String prefix) {
        String id = row.get("recordId");
        if (!isBlank(id)) return id.trim();
        // deterministic fallback so re-ingesting the same file is idempotent
        return prefix + "-" + Integer.toHexString(row.toString().hashCode());
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
