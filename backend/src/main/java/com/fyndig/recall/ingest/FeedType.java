package com.fyndig.recall.ingest;

import java.util.List;
import java.util.Map;

/**
 * The five feeds the plant receives, each with the column synonyms seen across source systems.
 * Header matching is case/space/underscore-insensitive, so "Supplier Code", "supplier_code",
 * "SUPPLIERCODE" and "vendor_id" all land on the same canonical field.
 */
public enum FeedType {

    SUPPLIERS("Supplier master", cols(
            "canonicalCode", List.of("canonical_code", "supplier_code", "code", "supplier_id", "vendor_id"),
            "name", List.of("name", "supplier_name", "vendor_name"),
            "country", List.of("country"),
            "contactEmail", List.of("contact_email", "email"),
            "aliases", List.of("aliases", "known_as", "alias_codes")
    ), List.of("canonicalCode", "name")),

    SUPPLIER_LOTS("Supplier lot register (supplier portal)", cols(
            "supplierCode", List.of("supplier_code", "supplier", "supplier_id", "vendor", "vendor_code", "vendor_id"),
            "lotCode", List.of("supplier_lot", "supplier_lot_no", "lot_no", "lot", "lot_number", "batch_no", "coa_lot"),
            "ingredientCode", List.of("ingredient_code", "material", "material_code", "item_code", "ingredient"),
            "ingredientName", List.of("ingredient_name", "material_name", "description", "item_name"),
            "quantity", List.of("qty", "quantity", "qty_received", "quantity_received"),
            "uom", List.of("uom", "unit", "units"),
            "receivedDate", List.of("received_date", "received", "delivery_date", "date_received", "grn_date"),
            "expiryDate", List.of("expiry_date", "expiry", "best_before", "use_by"),
            "recordId", List.of("record_id", "id", "row_id", "coa_no", "delivery_note")
    ), List.of("supplierCode", "lotCode", "ingredientCode")),

    RECEIPTS("Goods receipt (WMS) - supplier lot to internal lot", cols(
            "internalLotCode", List.of("internal_lot", "internal_lot_code", "plant_lot", "lot_code", "grn_lot", "lot"),
            "supplierCode", List.of("supplier_code", "supplier", "supplier_id", "vendor", "vendor_code", "vendor_id"),
            "supplierLotCode", List.of("supplier_lot", "supplier_lot_no", "vendor_lot", "coa_lot", "supplier_batch"),
            "ingredientCode", List.of("ingredient_code", "material", "material_code", "item_code"),
            "ingredientName", List.of("ingredient_name", "material_name", "description"),
            "quantity", List.of("qty", "quantity", "qty_received"),
            "uom", List.of("uom", "unit"),
            "receivedAt", List.of("received_at", "received_date", "grn_date", "timestamp", "event_time"),
            "recordId", List.of("record_id", "id", "grn_no", "row_id")
    ), List.of("internalLotCode", "supplierCode", "supplierLotCode")),

    MOVEMENTS("Lot movements (MES) - split / merge / rework / consumption into production batches", cols(
            "movementType", List.of("movement_type", "type", "txn_type", "event", "action"),
            "fromLotCode", List.of("from_lot", "source_lot", "input_lot", "consumed_lot", "parent_lot"),
            "toLotCode", List.of("to_lot", "target_lot", "output_lot", "produced_lot", "child_lot", "batch", "production_batch"),
            "toLotKind", List.of("to_lot_kind", "output_kind", "kind", "lot_type"),
            "materialCode", List.of("material_code", "sku", "sku_code", "output_material", "product_code"),
            "materialName", List.of("material_name", "sku_name", "product_name", "description"),
            "quantity", List.of("qty", "quantity", "qty_used", "consumed_qty"),
            "uom", List.of("uom", "unit"),
            "eventTime", List.of("event_time", "timestamp", "txn_time", "produced_at", "date"),
            "productionLine", List.of("line", "production_line", "work_center"),
            "recordId", List.of("record_id", "id", "txn_id", "row_id")
    ), List.of("movementType", "fromLotCode", "toLotCode")),

    DISPATCHES("Dispatch / shipment (OMS+WMS) - finished batch to customer order line", cols(
            "orderNo", List.of("order_no", "order", "order_id", "sales_order", "so_no"),
            "lineNo", List.of("line_no", "line", "order_line", "item_no", "position"),
            "customerCode", List.of("customer_code", "customer", "customer_id", "account", "account_no"),
            "customerName", List.of("customer_name", "account_name", "ship_to_name"),
            "customerEmail", List.of("customer_email", "email", "contact_email"),
            "skuCode", List.of("sku", "sku_code", "product_code", "material_code", "item_code"),
            "finishedLotCode", List.of("finished_lot", "batch", "batch_no", "fg_lot", "lot", "lot_code", "production_batch"),
            "quantity", List.of("qty", "quantity", "qty_shipped", "shipped_qty"),
            "uom", List.of("uom", "unit"),
            "dispatchDate", List.of("dispatch_date", "shipped_date", "ship_date", "dispatched", "delivery_date"),
            "region", List.of("region", "ship_to_region", "ship_to", "state", "destination"),
            "recordId", List.of("record_id", "id", "shipment_id", "delivery_no", "row_id")
    ), List.of("orderNo", "customerCode", "finishedLotCode"));

    private static Map<String, List<String>> cols(Object... kv) {
        Map<String, List<String>> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            @SuppressWarnings("unchecked") List<String> v = (List<String>) kv[i + 1];
            m.put((String) kv[i], v);
        }
        return java.util.Collections.unmodifiableMap(m);
    }

    public final String description;
    public final Map<String, List<String>> columnSynonyms;
    public final List<String> required;

    FeedType(String description, Map<String, List<String>> columnSynonyms, List<String> required) {
        this.description = description;
        this.columnSynonyms = columnSynonyms;
        this.required = required;
    }
}
