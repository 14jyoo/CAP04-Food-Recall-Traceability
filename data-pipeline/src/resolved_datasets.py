
import pandas as pd
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parents[1]
PROCESSED_DIR = BASE_DIR / "data" / "processed"

DATASET_FILES = {
    "supplier_lots": "supplier_lots_clean.csv",
    "goods_receipts": "goods_receipts_clean.csv"
}

SUPPLIER_COLUMNS = {
    "supplier_lots": "supplier_code",
    "goods_receipts": "supplier_code"
}


def generate_resolved_datasets():
    decisions = pd.read_csv(
        PROCESSED_DIR / "supplier_reconciliation_decisions.csv",
        dtype=str,
        keep_default_na=False
    )

    for dataset, filename in DATASET_FILES.items():

        input_file = PROCESSED_DIR / filename
        dataframe = pd.read_csv(input_file, dtype=str)

        supplier_column = SUPPLIER_COLUMNS[dataset]

        approved = decisions[
            (decisions["dataset"] == dataset) &
            (decisions["decision"] == "APPROVED")
        ]

        for _, decision in approved.iterrows():

            record_id = decision["record_id"]
            original_code = decision["original_code"]
            approved_code = decision["approved_supplier"]

            if not approved_code.strip():
                raise ValueError(
                    f"Missing approved supplier for {record_id}"
                )

            if dataset == "supplier_lots":
                record_mask = dataframe["Lot No"] == record_id
            else:
                record_mask = dataframe["GRN No"] == record_id

            record_mask &= dataframe[supplier_column] == original_code

            if record_mask.sum() != 1:
                raise ValueError(
                    f"Expected exactly one matching record for {record_id}"
                )

            dataframe.loc[record_mask, supplier_column] = approved_code

        output_file = PROCESSED_DIR / filename.replace(
            "_clean.csv", "_resolved.csv"
        )

        dataframe.to_csv(output_file, index=False)

        print(f"Created: {output_file}")
        print(f"Records: {len(dataframe)}")


if __name__ == "__main__":
    generate_resolved_datasets()
