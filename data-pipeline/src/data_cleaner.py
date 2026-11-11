
import pandas as pd
import re
from pathlib import Path

from data_loader import load_datasets

BASE_DIR = Path(__file__).resolve().parents[1]
PROCESSED_DIR = BASE_DIR / "data" / "processed"


def normalize_code(value):
    """Remove spaces and special characters and convert to uppercase."""
    if pd.isna(value):
        return None

    return re.sub(r"[^A-Z0-9]", "", str(value).upper())


def build_supplier_alias_map(suppliers):
    """Create a mapping from supplier aliases to canonical supplier codes."""
    alias_map = {}

    for _, row in suppliers.iterrows():
        canonical = str(row["supplier_code"]).strip().upper()

        alias_map[normalize_code(canonical)] = canonical

        aliases = str(row["aliases"]).split("|")

        for alias in aliases:
            alias_map[normalize_code(alias)] = canonical

    return alias_map


def normalize_units(series):
    """Standardize common measurement units."""
    unit_map = {
        "KGS": "KG",
        "KG": "KG",
        "LTR": "L",
        "L": "L",
        "CASES": "CS",
        "CASE": "CS",
        "CS": "CS"
    }

    return series.astype("string").str.strip().str.upper().map(
        lambda value: unit_map.get(value, value)
        if pd.notna(value) else pd.NA
    )


def clean_quantity(series):
    """Convert quantities containing commas into numeric values."""
    values = series.astype("string").str.replace(",", "", regex=False)
    return pd.to_numeric(values, errors="coerce")



def parse_dates(series):
    values = series.astype("string").str.strip()

    parsed = pd.Series(
        pd.NaT,
        index=series.index,
        dtype="datetime64[ns]"
    )

    # ISO dates: 2026-03-04 or 2026-03-04 09:15
    iso_mask = values.str.match(r"^\d{4}-\d{2}-\d{2}")

    parsed.loc[iso_mask] = pd.to_datetime(
        values.loc[iso_mask],
        errors="coerce"
    )

    # Other formats: 04/03/2026, 4-Mar-2026, etc.
    parsed.loc[~iso_mask] = pd.to_datetime(
        values.loc[~iso_mask],
        format="mixed",
        dayfirst=True,
        errors="coerce"
    )

    return parsed


def clean_datasets(datasets):

    suppliers = datasets["suppliers"].copy()
    supplier_lots = datasets["supplier_lots"].copy()
    goods_receipts = datasets["goods_receipts"].copy()
    dispatches = datasets["dispatches"].copy()
    movements = datasets["movements"].copy()

    # Build canonical supplier mapping
    alias_map = build_supplier_alias_map(suppliers)

    # 1. Supplier lots
    supplier_lots["supplier_code_normalized"] = (
        supplier_lots["Vendor"]
        .apply(lambda value: alias_map.get(normalize_code(value)))
    )

    supplier_lots["lot_code_normalized"] = (
        supplier_lots["Lot No"].apply(normalize_code)
    )

    supplier_lots["material_code_normalized"] = (
        supplier_lots["Material"].astype("string").str.strip().str.upper()
    )

    supplier_lots["quantity_normalized"] = clean_quantity(
        supplier_lots["Qty Received"]
    )

    supplier_lots["unit_normalized"] = normalize_units(
        supplier_lots["Unit"]
    )

    supplier_lots["grn_date_normalized"] = parse_dates(
        supplier_lots["GRN Date"]
    )

    supplier_lots["best_before_normalized"] = parse_dates(
        supplier_lots["Best Before"]
    )

    # 2. Goods receipts
    goods_receipts["supplier_code_normalized"] = (
        goods_receipts["Vendor ID"]
        .apply(lambda value: alias_map.get(normalize_code(value)))
    )

    goods_receipts["supplier_lot_normalized"] = (
        goods_receipts["Vendor Lot"].apply(normalize_code)
    )

    goods_receipts["plant_lot_normalized"] = (
        goods_receipts["Plant Lot"].apply(normalize_code)
    )

    goods_receipts["material_code_normalized"] = (
        goods_receipts["Material Code"]
        .astype("string").str.strip().str.upper()
    )

    goods_receipts["quantity_normalized"] = clean_quantity(
        goods_receipts["Qty"]
    )

    goods_receipts["unit_normalized"] = normalize_units(
        goods_receipts["UoM"]
    )

    goods_receipts["received_at_normalized"] = parse_dates(
        goods_receipts["Received At"]
    )

    # 3. Dispatches
    dispatches["batch_no_normalized"] = (
        dispatches["Batch No"].apply(normalize_code)
    )

    dispatches["quantity_normalized"] = clean_quantity(
        dispatches["Qty Shipped"]
    )

    dispatches["unit_normalized"] = normalize_units(
        dispatches["Unit"]
    )

    dispatches["ship_date_normalized"] = parse_dates(
        dispatches["Ship Date"]
    )

    # 4. Production movements
    movements["movement_type_normalized"] = (
        movements["type"].astype("string").str.strip().str.upper()
    )

    movements["input_lot_normalized"] = (
        movements["input_lot"].apply(normalize_code)
    )

    movements["output_lot_normalized"] = (
        movements["output_lot"].apply(normalize_code)
    )

    movements["quantity_normalized"] = clean_quantity(
        movements["qty_used"]
    )

    movements["unit_normalized"] = normalize_units(
        movements["unit"]
    )

    movements["timestamp_normalized"] = parse_dates(
        movements["timestamp"]
    )

    # Return cleaned datasets
    return {
        "suppliers": suppliers,
        "supplier_lots": supplier_lots,
        "goods_receipts": goods_receipts,
        "dispatches": dispatches,
        "movements": movements
    }


def save_datasets(datasets):

    PROCESSED_DIR.mkdir(parents=True, exist_ok=True)

    for name, dataframe in datasets.items():

        output_file = PROCESSED_DIR / f"{name}_clean.csv"

        dataframe.to_csv(output_file, index=False)

        print(f"Saved: {output_file.name} ({len(dataframe)} rows)")


if __name__ == "__main__":

    print("Loading raw datasets...")

    raw_datasets = load_datasets()

    print("\nCleaning datasets...")

    cleaned_datasets = clean_datasets(raw_datasets)

    print("\nSaving processed datasets...")

    save_datasets(cleaned_datasets)

    print("\nSupplier codes requiring reconciliation:")

    for name in ["supplier_lots", "goods_receipts"]:

        dataframe = cleaned_datasets[name]

        column = "supplier_code_normalized"

        unmatched = dataframe[dataframe[column].isna()]

        if not unmatched.empty:
            print(f"\n{name}:")
            print(unmatched.to_string(index=False))

    print("\nData cleaning completed.")
