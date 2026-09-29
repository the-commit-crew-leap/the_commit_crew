import pandas as pd
import logging
from pathlib import Path

from config import config

# Set up logging
logger = logging.getLogger(__name__)


class Loader:
    """Load transformed data into CSV files."""
    
    def __init__(self, output_dir: Path = None):
        self.output_dir = output_dir or config.CSV_DIR
    
    def load(self, df: pd.DataFrame, output_path_factory=None) -> dict:
        """
        Save each ticker into its own CSV.
        Ensures one row per:
            symbol + date
            
        Args:
            df:
                Cleaned market data.

            output_path_factory:
                Optional function receiving a symbol and returning
                its CSV path.

                Defaults to config.get_ticker_csv_path.

                Keeping this injectable makes load() easy to test
                without modifying the real application data.
        """

        if df.empty:
            logger.warning("Nothing to load: DataFrame is empty.")
            return {"saved": 0, "errors": ["Empty dataframe"]}
        
        if output_path_factory is None:
            output_path_factory = config.get_ticker_csv_path

        errors = []
        saved = 0
        
        # Save merged price history (all tickers combined)
        try:
            merged = df.sort_values(["symbol", "date"]).reset_index(drop=True)
            config.PRICE_HISTORY_FILE.parent.mkdir(parents=True, exist_ok=True)
            merged.to_csv(config.PRICE_HISTORY_FILE, index=False)
            logger.info(f"Saved merged price history to {config.PRICE_HISTORY_FILE}")
        except Exception as e:
            msg = f"Failed to save price_history.csv: {str(e)}"
            logger.error(msg)
            errors.append(msg)

        for symbol in df["symbol"].unique():

            try:
                ticker_df = (df[df["symbol"] == symbol].copy())
                if ticker_df.empty:
                    continue
                
                path = Path(output_path_factory(symbol))

                # Load existing data
                if path.exists():
                    existing = pd.read_csv(path, parse_dates=["date"])
                    existing["date"] = (pd.to_datetime(existing["date"], errors="coerce").dt.normalize())
                    combined = pd.concat([existing, ticker_df], ignore_index=True)
                else:
                    combined = ticker_df

                # Ensure clean dates
                combined["date"] = (pd.to_datetime(combined["date"], errors="coerce").dt.normalize())
                
                # Keep newest version
                # Incoming data is last, so it replaces existing data for the same date.
                combined = combined.drop_duplicates(subset=["date"], keep="last")
                combined = combined.sort_values("date").reset_index(drop=True)
                
                # Create parent directories and save
                path.parent.mkdir(parents=True, exist_ok=True)
                combined.to_csv(path, index=False)

                logger.info(f"Saved {symbol}: {len(combined)} rows")
                saved += 1

            except Exception as e:
                msg = (f"{symbol}: {str(e)}")
                logger.error(msg)
                errors.append(msg)

        return {
            "saved": saved,
            "errors": errors
        }

