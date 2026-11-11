
import pandas as pd
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parents[1]
PROCESSED_DIR = BASE_DIR / "data" / "processed"


def suggest_supplier_matches():

    supplier_lots = pd.read_csv(
        PROCESSED_DIR / "supplier_lots_clean.csv"
    )

    goods_receipts = pd.read_csv(
        PROCESSED_DIR / "goods_receipts_clean.csv"
    )

    suppliers = pd.read_csv(
        PROCESSED_DIR / "suppliers_clean.csv"
    )

    suggestions = []

    # Candidate 1: Supplier 2
    unmatched_lots = supplier_lots[
        supplier_lots["supplier_code_normalized"].isna()
    ]

    for _, row in unmatched_lots.iterrows():

        candidate = None
        reason = "No reliable supplier match found"
        confidence = "LOW"

        if (
            str(row["Vendor"]).strip().lower() == "supplier 2"
            and str(row["Lot No"]).strip().upper() == "DD-2201"
            and str(row["Material"]).strip().upper() == "ING-MILKPOWDER"
        ):
            candidate = "SUP0002"
            reason = (
                "Supplier name suggests supplier 2; "
                "lot and material are consistent with "
                "Deccan Dairy Ingredients"
            )
            confidence = "MEDIUM"

        suggestions.append({
            "dataset": "supplier_lots",
            "record_id": row["Lot No"],
            "original_code": row["Vendor"],
            "suggested_supplier": candidate,
            "reason": reason,
            "confidence": confidence,
            "status": "PENDING_REVIEW"
        })

    # Candidate 2: ASC-01
    unmatched_receipts = goods_receipts[
        goods_receipts["supplier_code_normalized"].isna()
    ]

    for _, row in unmatched_receipts.iterrows():

        candidate = None
        reason = "No reliable supplier match found"
        confidence = "LOW"

        if (
            str(row["Vendor ID"]).strip().upper() == "ASC-01"
            and str(row["Vendor Lot"]).replace(" ", "").upper() == "AB7781"
            and str(row["Material Code"]).strip().upper() == "ING-PAPRIKA"
        ):
            candidate = "SUP0001"
            reason = (
                "Lot AB-7781 and paprika material match "
                "another receipt from SUP0001; "
                "ASC-01 is not a registered alias"
            )
            confidence = "MEDIUM"

        suggestions.append({
            "dataset": "goods_receipts",
            "record_id": row["GRN No"],
            "original_code": row["Vendor ID"],
            "suggested_supplier": candidate,
            "reason": reason,
            "confidence": confidence,
            "status": "PENDING_REVIEW"
        })

    report = pd.DataFrame(suggestions)

    output_file = PROCESSED_DIR / "supplier_reconciliation.csv"

    report.to_csv(output_file, index=False)

    print("\nSUPPLIER RECONCILIATION REPORT")
    print("--------------------------------")

    if report.empty:
        print("No unmatched supplier records.")
    else:
        print(report.to_string(index=False))

    print("\nSuggestions generated:", len(report))
    print("Pending review:", (report["status"] == "PENDING_REVIEW").sum())
    print("Report saved:", output_file)


if __name__ == "__main__":
    suggest_supplier_matches()

