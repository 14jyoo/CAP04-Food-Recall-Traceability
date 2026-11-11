
import pandas as pd
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parents[1]
PROCESSED_DIR = BASE_DIR / "data" / "processed"
RAW_DIR = BASE_DIR / "data" / "raw"

DECISIONS_FILE = PROCESSED_DIR / "supplier_reconciliation_decisions.csv"
SUPPLIERS_FILE = RAW_DIR / "suppliers.csv"
AUDIT_FILE = PROCESSED_DIR / "reconciliation_audit.csv"


def validate_decisions():
    decisions = pd.read_csv(
    DECISIONS_FILE,
    dtype=str,
    keep_default_na=False
)
    suppliers = pd.read_csv(SUPPLIERS_FILE)

    valid_suppliers = set(suppliers["supplier_code"].astype(str).str.strip())
    allowed_decisions = {"APPROVED", "REJECTED", "PENDING_REVIEW"}

    errors = []

    for index, row in decisions.iterrows():
        decision = str(row["decision"]).strip().upper()
        approved_supplier = str(row["approved_supplier"]).strip()

        if decision not in allowed_decisions:
            errors.append(f"Row {index + 1}: Invalid decision")

        if decision == "APPROVED":
            if approved_supplier not in valid_suppliers:
                errors.append(
                    f"Row {index + 1}: Approved supplier does not exist"
                )

            if not str(row["reviewer"]).strip():
                errors.append(f"Row {index + 1}: Reviewer is required")

            if not str(row["decision_date"]).strip():
                errors.append(f"Row {index + 1}: Decision date is required")

        elif decision in {"REJECTED", "PENDING_REVIEW"}:
            if approved_supplier:
                errors.append(
                    f"Row {index + 1}: Supplier must be blank unless approved"
                )

    if errors:
        print("Validation failed:")
        for error in errors:
            print("-", error)
        return

    decisions.to_csv(AUDIT_FILE, index=False)

    print("Reconciliation decisions validated successfully.")
    print("Audit file saved:", AUDIT_FILE)
    print(decisions[["record_id", "decision"]])


if __name__ == "__main__":
    validate_decisions()
