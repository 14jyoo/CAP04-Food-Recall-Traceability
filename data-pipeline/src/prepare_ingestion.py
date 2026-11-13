
import pandas as pd
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROCESSED = ROOT / "data" / "processed"
RAW = ROOT / "data" / "raw"
OUTPUT = ROOT / "data" / "ingestion"

OUTPUT.mkdir(parents=True, exist_ok=True)

rejections = []


def read_csv(name):
    return pd.read_csv(PROCESSED / name)


def save_csv(df, name):
    df.to_csv(OUTPUT / name, index=False)
    print(f"Created {name}: {len(df)} records")


# 1. SUPPLIERS
suppliers = read_csv("suppliers_clean.csv")

supplier_feed = pd.DataFrame({
    "canonicalCode": suppliers["supplier_code"],
    "name": suppliers["supplier_name"],
    "country": suppliers["country"],
    "contactEmail": suppliers["contact_email"],
    "aliases": suppliers["aliases"]
})

save_csv(supplier_feed, "suppliers.csv")


# 2. SUPPLIER LOTS
lots = read_csv("supplier_lots_resolved.csv")

supplier_lots_feed = pd.DataFrame({
    "supplierCode": lots["supplier_code_normalized"],
    "lotCode": lots["Lot No"],
    "ingredientCode": lots["material_code_normalized"],
    "ingredientName": lots["Description"],
    "quantity": lots["quantity_normalized"],
    "uom": lots["unit_normalized"],
    "receivedDate": lots["grn_date_normalized"],
    "expiryDate": lots["best_before_normalized"],
    "recordId": lots["CoA No"]
})

save_csv(supplier_lots_feed, "supplier_lots.csv")


# 3. GOODS RECEIPTS
receipts = read_csv("goods_receipts_resolved.csv")

receipts_feed = pd.DataFrame({
    "internalLotCode": receipts["Plant Lot"],
    "supplierCode": receipts["supplier_code_normalized"],
    "supplierLotCode": receipts["Vendor Lot"],
    "ingredientCode": receipts["material_code_normalized"],
    "ingredientName": receipts["Material Name"],
    "quantity": receipts["quantity_normalized"],
    "uom": receipts["unit_normalized"],
    "receivedAt": receipts["received_at_normalized"],
    "recordId": receipts["GRN No"]
})

save_csv(receipts_feed, "receipts.csv")


# 4. MOVEMENTS
movements = read_csv("movements_clean.csv")

allowed_types = {"CONSUME", "SPLIT", "MERGE"}

invalid_movements = movements[
    ~movements["type"].astype(str).str.upper().isin(allowed_types)
].copy()

for _, row in invalid_movements.iterrows():
    rejections.append({
        "feed": "MOVEMENTS",
        "recordId": row["txn_id"],
        "reason": f"Invalid movement type: {row['type']}"
    })

valid_movements = movements[
    movements["type"].astype(str).str.upper().isin(allowed_types)
].copy()

movement_feed = pd.DataFrame({
    "movementType": valid_movements["type"],
    "fromLotCode": valid_movements["input_lot"],
    "toLotCode": valid_movements["output_lot"],
    "toLotKind": valid_movements["kind"],
    "materialCode": valid_movements["sku"],
    "materialName": valid_movements["product_name"],
    "quantity": valid_movements["quantity_normalized"],
    "uom": valid_movements["unit_normalized"],
    "eventTime": valid_movements["timestamp_normalized"],
    "productionLine": valid_movements["line"],
    "recordId": valid_movements["txn_id"]
})

save_csv(movement_feed, "movements.csv")


# 5. DISPATCHES
dispatches = read_csv("dispatches_clean.csv")

# Record duplicate shipment IDs for the rejection report.
duplicate_mask = dispatches["Shipment ID"].duplicated(keep="first")

for _, row in dispatches[duplicate_mask].iterrows():
    rejections.append({
        "feed": "DISPATCHES",
        "recordId": row["Shipment ID"],
        "reason": "Duplicate shipment ID"
    })

# Keep the first occurrence of each shipment.
dispatches = dispatches.drop_duplicates(
    subset=["Shipment ID"], keep="first"
).copy()

# Exclude non-positive quantities.
invalid_quantity = pd.to_numeric(
    dispatches["quantity_normalized"], errors="coerce"
) <= 0

for _, row in dispatches[invalid_quantity].iterrows():
    rejections.append({
        "feed": "DISPATCHES",
        "recordId": row["Shipment ID"],
        "reason": "Quantity must be greater than zero"
    })

dispatches = dispatches[~invalid_quantity].copy()

dispatch_feed = pd.DataFrame({
    "orderNo": dispatches["Sales Order"],
    "lineNo": dispatches["Line"],
    "customerCode": dispatches["Account"],
    "customerName": dispatches["Account Name"],
    "customerEmail": dispatches["Email"],
    "skuCode": dispatches["SKU"],
    "finishedLotCode": dispatches["Batch No"],
    "quantity": dispatches["quantity_normalized"],
    "uom": dispatches["unit_normalized"],
    "dispatchDate": dispatches["ship_date_normalized"],
    "region": dispatches["Region"],
    "recordId": dispatches["Shipment ID"]
})

save_csv(dispatch_feed, "dispatches.csv")


# Save excluded records separately.
rejection_report = pd.DataFrame(
    rejections,
    columns=["feed", "recordId", "reason"]
)

rejection_report.to_csv(
    OUTPUT / "ingestion_rejections.csv", index=False
)

print("\nPreparation completed.")
print("Rejection records:", len(rejection_report))
print("Output folder:", OUTPUT)
