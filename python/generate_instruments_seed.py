#!/usr/bin/env python3
"""Generate SQL seed data for instruments table from ticker_metadata.csv"""

import csv
from pathlib import Path


ASSET_CLASS_MAP = {
    "equity": "EQUITY",
    "etf": "FUND",
    "bond": "BOND"
}


def generate_instruments_sql(csv_path: str, output_path: str) -> None:
    """Generate instruments-data.sql from ticker_metadata.csv
    
    Reads ticker metadata, maps asset classes, escapes quotes, and generates
    idempotent INSERT statement.
    """
    
    rows = []
    with open(csv_path, 'r') as f:
        reader = csv.DictReader(f)
        for row in reader:
            ticker = row['ticker'].upper()
            name = row['long_name'].replace("'", "''")  # Escape single quotes
            asset_class = ASSET_CLASS_MAP.get(row['asset_class'].lower(), 'FUND')
            currency = row['currency']
            tradable = str(row['tradable']).lower() == 'true'
            
            rows.append((ticker, name, asset_class, currency, tradable))
    
    # Generate SQL
    sql_lines = [
        "-- Auto-generated from ticker_metadata.csv",
        "-- Do not edit manually; regenerate with: python python/generate_instruments_seed.py",
        "",
        "INSERT INTO instruments (symbol, name, asset_class, currency, tradable) VALUES",
    ]
    
    for i, (ticker, name, asset_class, currency, tradable) in enumerate(rows):
        is_last = i == len(rows) - 1
        comma = "" if is_last else ","
        sql_lines.append(
            f"    ('{ticker}', '{name}', '{asset_class}', '{currency}', {str(tradable).lower()}){comma}"
        )
    
    sql_lines.append("ON CONFLICT (symbol) DO NOTHING;")
    sql_lines.append("")
    
    # Write output
    output = Path(output_path)
    output.write_text("\n".join(sql_lines))
    print(f"✅ Generated {output} with {len(rows)} instruments")


if __name__ == "__main__":
    csv_file = Path(__file__).parent / "market_data" / "ticker_metadata.csv"
    sql_file = Path(__file__).parent.parent / "db" / "seeds" / "instruments-data.sql"
    
    if not csv_file.exists():
        print(f"❌ Error: {csv_file} not found")
        exit(1)
    
    generate_instruments_sql(str(csv_file), str(sql_file))