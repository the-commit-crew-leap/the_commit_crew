import csv
from pathlib import Path

from config import config

class SeedGenerator:
    
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