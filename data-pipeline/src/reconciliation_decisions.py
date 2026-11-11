
import pandas as pd
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parents[1]
PROCESSED_DIR = BASE_DIR / "data" / "processed"

suggestions_file = PROCESSED_DIR / "supplier_reconciliation.csv"
decisions_file = PROCESSED_DIR / "supplier_reconciliation_decisions.csv"

suggestions = pd.read_csv(suggestions_file)

decisions = suggestions[
    ["dataset", "record_id", "original_code"]
].copy()

decisions["approved_supplier"] = ""
decisions["decision"] = "PENDING_REVIEW"
decisions["reviewer"] = ""
decisions["decision_date"] = ""
decisions["notes"] = ""

decisions.to_csv(decisions_file, index=False)

print("Reconciliation decision template created:")
print(decisions_file)
print(decisions)
