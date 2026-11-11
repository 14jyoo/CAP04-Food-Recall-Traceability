
import pandas as pd
from pathlib import Path

from data_cleaner import clean_datasets
from data_loader import load_datasets

BASE_DIR = Path(__file__).resolve().parents[1]
PROCESSED_DIR = BASE_DIR / "data" / "processed"

issues = []


def add_issue(dataset, record_id, field, value, issue, severity):
    issues.append({
        "dataset": dataset,
        "record_id": str(record_id),
        "field": field,
        "value": str(value),
        "issue": issue,
        "severity": severity
    })


def validate_supplier_lots(df):

    for _, row in df.iterrows():

        record_id = row["Lot No"]

        if pd.isna(row["supplier_code_normalized"]):
            add_issue(
                "supplier_lots", record_id,
                "Vendor", row["Vendor"],
                "Unknown supplier code", "WARNING"
            )

        if pd.isna(row["quantity_normalized"]) or row["quantity_normalized"] <= 0:
            add_issue(
                "supplier_lots", record_id,
                "Qty Received", row["Qty Received"],
                "Quantity must be greater than zero", "ERROR"
            )

        if pd.isna(row["grn_date_normalized"]):
            add_issue(
                "supplier_lots", record_id,
                "GRN Date", row["GRN Date"],
                "Invalid or missing date", "ERROR"
            )


def validate_goods_receipts(df):

    for _, row in df.iterrows():

        record_id = row["GRN No"]

        if pd.isna(row["supplier_code_normalized"]):
            add_issue(
                "goods_receipts", record_id,
                "Vendor ID", row["Vendor ID"],
                "Unknown supplier code", "WARNING"
            )

        if pd.isna(row["quantity_normalized"]) or row["quantity_normalized"] <= 0:
            add_issue(
                "goods_receipts", record_id,
                "Qty", row["Qty"],
                "Quantity must be greater than zero", "ERROR"
            )

        if pd.isna(row["received_at_normalized"]):
            add_issue(
                "goods_receipts", record_id,
                "Received At", row["Received At"],
                "Invalid or missing date", "ERROR"
            )


def validate_dispatches(df):

    # Duplicate shipment IDs
    duplicates = df[df["Shipment ID"].duplicated(keep=False)]

    for _, row in duplicates.iterrows():
        add_issue(
            "dispatches", row["Shipment ID"],
            "Shipment ID", row["Shipment ID"],
            "Duplicate shipment ID", "ERROR"
        )

    for _, row in df.iterrows():

        record_id = row["Shipment ID"]

        if pd.isna(row["quantity_normalized"]) or row["quantity_normalized"] <= 0:
            add_issue(
                "dispatches", record_id,
                "Qty Shipped", row["Qty Shipped"],
                "Quantity must be greater than zero", "ERROR"
            )

        if pd.isna(row["ship_date_normalized"]):
            add_issue(
                "dispatches", record_id,
                "Ship Date", row["Ship Date"],
                "Invalid or missing date", "ERROR"
            )


def validate_movements(df):

    allowed_types = {"CONSUME", "SPLIT", "MERGE"}

    for _, row in df.iterrows():

        record_id = row["txn_id"]

        if row["movement_type_normalized"] not in allowed_types:
            add_issue(
                "movements", record_id,
                "type", row["type"],
                "Invalid movement type", "ERROR"
            )

        if pd.isna(row["quantity_normalized"]) or row["quantity_normalized"] <= 0:
            add_issue(
                "movements", record_id,
                "qty_used", row["qty_used"],
                "Quantity must be greater than zero", "ERROR"
            )

        if pd.isna(row["timestamp_normalized"]):
            add_issue(
                "movements", record_id,
                "timestamp", row["timestamp"],
                "Invalid or missing timestamp", "ERROR"
            )


def run_validation():

    print("Loading and cleaning datasets...")

    raw = load_datasets()
    cleaned = clean_datasets(raw)

    print("\nRunning validation...")

    validate_supplier_lots(cleaned["supplier_lots"])
    validate_goods_receipts(cleaned["goods_receipts"])
    validate_dispatches(cleaned["dispatches"])
    validate_movements(cleaned["movements"])

    report = pd.DataFrame(issues)

    PROCESSED_DIR.mkdir(parents=True, exist_ok=True)

    report.to_csv(
        PROCESSED_DIR / "validation_report.csv",
        index=False
    )

    print("\nVALIDATION SUMMARY")
    print("------------------")

    if report.empty:
        print("No validation issues found.")
    else:
        print(report.groupby(["severity", "issue"]).size())

        print("\nTotal issues:", len(report))
        print("Errors:", (report["severity"] == "ERROR").sum())
        print("Warnings:", (report["severity"] == "WARNING").sum())

    print("\nReport saved: validation_report.csv")


if __name__ == "__main__":
    run_validation()
