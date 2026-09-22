import pandas as pd
from pathlib import Path
import logging

from src.analysis.analysis_helper import AnalysisHelper
from config import config

# Set up logging
logger = logging.getLogger(__name__)


class DataLoader:
    """Handles loading and setting data from CSV files."""
    
    def __init__(self, csv_dir: Path = None):
        self.csv_dir = csv_dir or config.CSV_DIR
        
    
    @staticmethod
    def _normalize_ticker(ticker: str) -> str:
        """Normalize a ticker symbol to the same format used by the ETL."""
        return str(ticker).strip().upper()
        
        
    # DATA LOADING
    def load_price_data(self, symbol: str, start_date: str = None, end_date: str = None) -> pd.DataFrame:
        """
        Load historical price data for a single ticker from its CSV file.
        The CSV path is obtained from config.get_ticker_csv_path().
        Dates are normalized to midnight so that multiple timestamps belonging
        to the same trading day are treated as the same date.

        Duplicate dates are removed, keeping the last occurrence.

        Args:
            symbol: Ticker symbol, for example "AAPL".

            start_date: Optional inclusive start date in YYYY-MM-DD format.

            end_date: Optional inclusive end date in YYYY-MM-DD format.

        Returns:
            DataFrame containing the requested price data.

            An empty DataFrame is returned if:
                - The CSV file does not exist.
                - The CSV file is empty.
                - An error occurs while loading the file.
        """
        symbol = self._normalize_ticker(symbol)
        csv_path = config.get_ticker_csv_path(symbol)
        
        if not csv_path.exists():
            logger.warning(f"No CSV found for {symbol}: {csv_path}")
            return pd.DataFrame()

        try:
            df = pd.read_csv(csv_path, parse_dates=["date"])
        except Exception as e:
                logger.error(f"Error loading {symbol}: {e}", exc_info=True)
                return pd.DataFrame()

        if df.empty:
            logger.warning(f"CSV is empty for {symbol}")
            return pd.DataFrame()

        df["date"] = pd.to_datetime(df["date"]).dt.normalize()
        duplicate_count = df.duplicated(subset=["date"]).sum()

        if duplicate_count > 0:
            logger.warning(f"{symbol}: found {duplicate_count} duplicate dates; keeping latest row")
            df = df.drop_duplicates(subset=["date"], keep="last")
            
        df = df.sort_values("date")

        if start_date:
            df = df[df["date"] >= pd.Timestamp(start_date)]

        if end_date:
            df = df[df["date"] <= pd.Timestamp(end_date)]

        logger.debug(f"Loaded {len(df)} rows for {symbol}")

        return df.reset_index(drop=True)