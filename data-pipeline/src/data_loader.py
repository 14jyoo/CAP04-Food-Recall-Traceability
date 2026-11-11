import pandas as pd
import json
from pathlib import Path

# Locate the raw data folder
BASE_DIR = Path(__file__).resolve().parents[1]
RAW_DIR = BASE_DIR / "data" / "raw"


def load_datasets():

    # Read comma-separated files
    suppliers = pd.read_csv(RAW_DIR / "suppliers.csv")
    supplier_lots = pd.read_csv(RAW_DIR / "supplier_lots.csv")

    # Read semicolon-separated file
    goods_receipts = pd.read_csv(
        RAW_DIR / "goods_receipts.csv",
        sep=";"
    )

    # Read pipe-separated file
    dispatches = pd.read_csv(
        RAW_DIR / "dispatches.csv",
        sep="|"
    )

    # Read JSON movement records
    with open(RAW_DIR / "lot_movements.json", "r") as file:
        movement_data = json.load(file)

    movements = pd.json_normalize(movement_data["records"])

    return {
        "suppliers": suppliers,
        "supplier_lots": supplier_lots,
        "goods_receipts": goods_receipts,
        "dispatches": dispatches,
        "movements": movements
    }


if __name__ == "__main__":

    datasets = load_datasets()

    for name, dataframe in datasets.items():

        print("\n" + "=" * 50)
        print("DATASET:", name.upper())
        print("=" * 50)

        print("Rows:", len(dataframe))
        print("Columns:", list(dataframe.columns))

        print("\nFirst 3 records:")
        print(dataframe.head(3).to_string(index=False))
