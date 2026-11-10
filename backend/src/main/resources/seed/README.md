# Demo scenario

| File | Source system | Format | Deliberate problems |
|---|---|---|---|
| suppliers.csv | ERP | CSV `,` | aliases in a pipe-separated column |
| supplier_lots.csv | Supplier portal | CSV `,` | `sup-1`, `Supplier 2`, `NFM` codes; `co 5510` lot; `1,000` qty; 4 date formats; `kgs`/`LTR` units |
| goods_receipts.csv | WMS | CSV `;` | `S-001` / `CO-3` alias codes; `ab 7781`, `NF3300` lot spellings; **`ASC-01` unknown supplier code** |
| lot_movements.json | MES | JSON `{records:[...]}` | CONSUME / SPLIT / MERGE synonyms; **invalid type `TELEPORT`** |
| dispatches.csv | OMS | CSV `\|` | lower-case batch; 3 date formats; **qty −5**; **duplicate SHP-0001** |

Expected recall of SUP0001 / AB-7781: 8 order lines (not proven) → 9 order lines after resolving `ASC-01 → SUP0001` (proven).
