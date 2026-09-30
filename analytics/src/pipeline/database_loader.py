from pathlib import Path
import logging
import psycopg2
import pandas as pd

from config import config

logger = logging.getLogger(__name__)


class DatabaseLoader:
    """Load data from CSVs into PostgreSQL."""
    
    def __init__(self, db_connection_params: dict = None):
        self.db_params = db_connection_params or config.DB_CONNECTION_PARAMS
    
    
    def _reset_price_history_table(self) -> dict:
        """Drop and recreate the price_history table only."""
        try:
            conn = psycopg2.connect(**self.db_params)
            cursor = conn.cursor()
            
            # Drop price_history table if it exists
            cursor.execute("DROP TABLE IF EXISTS price_history CASCADE;")
            
            # Recreate price_history table with foreign key to instruments
            cursor.execute("""
                CREATE TABLE price_history (
                    symbol VARCHAR(20) NOT NULL,
                    price_date DATE NOT NULL,
                    open_price NUMERIC(15, 6),
                    high_price NUMERIC(15, 6),
                    low_price NUMERIC(15, 6),
                    close_price NUMERIC(15, 6),
                    volume BIGINT,
                    PRIMARY KEY (symbol, price_date),
                    FOREIGN KEY (symbol) REFERENCES instruments(symbol) ON DELETE CASCADE
                );
            """)
            
            conn.commit()
            cursor.close()
            conn.close()
            
            logger.info("✅ Price history table reset")
            return {"status": "success"}
            
        except Exception as e:
            logger.error(f"Failed to reset price_history table: {str(e)}")
            return {"status": "failed", "error": str(e)}
    
    
    def load_price_history_from_csv(self, csv_path: str, limit_days: int = None) -> dict:
        """Load price_history.csv directly into PostgreSQL (daily updates).
        
        Drops and recreates the price_history table on every run to ensure fresh data.
        
        Args:
            csv_path: Path to price_history.csv
            limit_days: If set, only load the most recent N days of data across all tickers.
                       Set to None (default) to load all historical data.
                       
        Returns:
            Dictionary with status and error details if any.
        """
        try:
            # Reset price_history table (drop and recreate)
            reset_result = self._reset_price_history_table()
            if reset_result["status"] != "success":
                return reset_result
            
            # Read CSV and select/rename columns to match database schema
            df = pd.read_csv(csv_path)
            df_filtered = df[['date', 'symbol', 'open', 'high', 'low', 'close', 'volume']].copy()
            df_filtered.columns = ['price_date', 'symbol', 'open_price', 'high_price', 'low_price', 'close_price', 'volume']
            
            # If limit_days specified, keep only recent data
            if limit_days is not None:
                # Convert date to datetime and sort descending to get most recent first
                df_filtered['price_date'] = pd.to_datetime(df_filtered['price_date'])
                df_filtered = df_filtered.sort_values('price_date', ascending=False)
                # Keep most recent N days * ~30 tickers (accounts for multiple tickers per day)
                df_filtered = df_filtered.head(limit_days * 30)
                df_filtered['price_date'] = df_filtered['price_date'].dt.strftime('%Y-%m-%d')
                logger.info(f"Limited to {limit_days} days of data ({len(df_filtered)} rows)")
            
            conn = psycopg2.connect(**self.db_params)
            cursor = conn.cursor()
            
            # Insert data (table is fresh, no conflicts)
            for _, row in df_filtered.iterrows():
                cursor.execute("""
                    INSERT INTO price_history (price_date, symbol, open_price, high_price, low_price, close_price, volume)
                    VALUES (%s, %s, %s, %s, %s, %s, %s)
                """, tuple(row))
            
            conn.commit()
            cursor.close()
            conn.close()
            
            logger.info(f"Loaded price_history from {csv_path}")
            return {"status": "success"}
            
        except Exception as e:
            logger.error(f"Failed to load: {str(e)}")
            return {"status": "failed", "error": str(e)}


    def load_instruments_from_csv(self, csv_path: str) -> dict:
        """Load ticker_metadata.csv directly into PostgreSQL."""
        try:
            # Read CSV and select/rename columns to match database schema
            df = pd.read_csv(csv_path)
            df_filtered = df[['ticker', 'long_name', 'asset_class', 'currency', 'tradable']].copy()
            df_filtered.columns = ['symbol', 'name', 'asset_class', 'currency', 'tradable']
            
            # Map asset class values using config mapping (e.g., equity -> EQUITY)
            df_filtered['asset_class'] = df_filtered['asset_class'].map(config.ASSET_CLASS_MAP)
            
            conn = psycopg2.connect(**self.db_params)
            cursor = conn.cursor()
            
            # Use UPSERT to handle existing instruments
            for _, row in df_filtered.iterrows():
                cursor.execute("""
                    INSERT INTO instruments (symbol, name, asset_class, currency, tradable)
                    VALUES (%s, %s, %s, %s, %s)
                    ON CONFLICT (symbol) DO UPDATE SET
                        name = EXCLUDED.name,
                        asset_class = EXCLUDED.asset_class,
                        currency = EXCLUDED.currency,
                        tradable = EXCLUDED.tradable
                """, tuple(row))
            
            conn.commit()
            cursor.close()
            conn.close()
            
            logger.info(f"✅ Loaded instruments from {csv_path}")
            return {"status": "success"}
            
        except Exception as e:
            logger.error(f"Failed to load: {str(e)}")
            return {"status": "failed", "error": str(e)}
        

        
if __name__ == "__main__":
    loader = DatabaseLoader()
    
    # Load instruments metadata (UPSERT only - table persists across runs)
    # This will only actually reload when the docker compose volume is restarted
    metadata_csv = Path(__file__).parent.parent.parent / "outputs" / "csv" / "ticker_metadata.csv"
    if not metadata_csv.exists():
        print(f"Error: {metadata_csv} not found")
    else:
        result = loader.load_instruments_from_csv(str(metadata_csv))
        print(f"Instruments load: {result}")
    
    # Load price history (DROP + RECREATE + FILL every time)
    # Fresh data every day when the scheduler runs
    price_csv = Path(__file__).parent.parent.parent / "outputs" / "csv" / "price_history.csv"
    if not price_csv.exists():
        print(f"Error: {price_csv} not found")
    else:
        # Use limit_days=None to load all historical data
        # Use limit_days=30 to load only the most recent 30 days
        result = loader.load_price_history_from_csv(str(price_csv), limit_days=1)
        print(f"Price history load: {result}")
    
    # Load instruments metadata
    metadata_csv = Path(__file__).parent.parent.parent / "outputs" / "csv" / "ticker_metadata.csv"
    if not metadata_csv.exists():
        print(f"Error: {metadata_csv} not found")
    else:
        result = loader.load_instruments_from_csv(str(metadata_csv))
        print(f"Instruments load: {result}")