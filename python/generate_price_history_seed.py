#!/usr/bin/env python3
"""Generate SQL seed data for price_history table from price_history.csv"""

import csv
from pathlib import Path
from datetime import datetime


def generate_price_history_sql(csv_path: str, output_path: str, limit_days: int = 30) -> None:
    """Generate price_history-data.sql from price_history.csv
    
    Reads price history CSV and generates idempotent INSERT statement.
    
    Args:
        csv_path: Path to price_history.csv
        output_path: Path to output SQL file
        limit_days: If set, only include last N days of data (for smaller seed files)
                   Set to None to include all data
    """
    
    rows = []
    with open(csv_path, 'r') as f:
        reader = csv.DictReader(f)
        for row in reader:
            ticker = row['ticker'].upper()
            price_date = row['date']
            open_price = row['open']
            high_price = row['high']
            low_price = row['low']
            close_price = row['close']
            volume = row['volume']
            
            rows.append((ticker, price_date, open_price, high_price, low_price, close_price, volume))
    
    # Sort by date descending, then take first N (most recent)
    rows.sort(key=lambda x: x[1], reverse=True)
    if limit_days:
        rows = rows[:limit_days * 30]  # 30 tickers * limit_days
    rows.sort(key=lambda x: (x[0], x[1]))  # Re-sort by ticker, then date
    
    # Generate SQL
    sql_lines = [
        "-- Auto-generated from price_history.csv",
        "-- Do not edit manually; regenerate with: python python/generate_price_history_seed.py",
        "",
        "INSERT INTO price_history (symbol, price_date, open_price, high_price, low_price, close_price, volume) VALUES",
    ]
    
    for i, (ticker, price_date, open_price, high_price, low_price, close_price, volume) in enumerate(rows):
        is_last = i == len(rows) - 1
        comma = "" if is_last else ","
        sql_lines.append(
            f"    ('{ticker}', '{price_date}', {open_price}, {high_price}, {low_price}, {close_price}, {volume}){comma}"
        )
    
    sql_lines.append("ON CONFLICT (symbol, price_date) DO NOTHING;")
    sql_lines.append("")
    
    # Write output
    output = Path(output_path)
    output.write_text("\n".join(sql_lines))
    print(f"✅ Generated {output} with {len(rows)} price records")


if __name__ == "__main__":
    csv_file = Path(__file__).parent / "market_data" / "price_history.csv"
    sql_file = Path(__file__).parent.parent / "db" / "seeds" / "price_history-data.sql"
    
    if not csv_file.exists():
        print(f"❌ Error: {csv_file} not found")
        exit(1)
    
    # Generate with recent 30 days of data (to keep seed file reasonable size)
    # Change limit_days=None to include all historical data
    generate_price_history_sql(str(csv_file), str(sql_file), limit_days=30)