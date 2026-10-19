import pandas as pd
from pathlib import Path
from typing import Optional
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
        Load historical price data for a single symbol from its CSV file.
        The CSV path is obtained from config.get_symbol_csv_path().
        Dates are normalized to midnight so that multiple timestamps belonging
        to the same trading day are treated as the same date.

        Duplicate dates are removed, keeping the last occurrence.

        Args:
            symbol: Instrument symbol, for example "AAPL".

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
        csv_path = config.get_symbol_csv_path(symbol)
        if not csv_path.exists():
            logger.warning(f"No CSV found for {symbol}: {csv_path}")
            return pd.DataFrame()

        try:
            df = pd.read_csv(csv_path)
        except Exception as e:
                logger.error(f"Error loading {symbol}: {e}", exc_info=True)
                return pd.DataFrame()

        if df.empty:
            logger.warning(f"CSV is empty for {symbol}")
            return pd.DataFrame()

        # ETL outputs and database seed exports use different column names.
        df = df.rename(columns={
            "price_date": "date",
            "open_price": "open",
            "high_price": "high",
            "low_price": "low",
            "close_price": "close",
        })
        if "symbol" in df.columns:
            df["symbol"] = df["symbol"].map(self._normalize_ticker)
            df = df[df["symbol"] == symbol]
        required_columns = {"date", "close"}
        if not required_columns.issubset(df.columns):
            logger.warning(f"Price data for {symbol} is missing required columns")
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

    def get_latest_price(self, symbol: str) -> Optional[float]:
        """Return the latest saved close price for one symbol."""
        price_data = self.load_price_data(symbol)
        if price_data.empty:
            return None
        close = pd.to_numeric(price_data["close"], errors="coerce").dropna()
        return float(close.iloc[-1]) if not close.empty else None

    def get_latest_date(self, symbols: list[str]) -> Optional[pd.Timestamp]:
        """Return the latest available price date across the requested symbols."""
        latest_dates = []
        for symbol in symbols:
            price_data = self.load_price_data(symbol)
            if not price_data.empty:
                latest_dates.append(price_data["date"].max())

        return max(latest_dates) if latest_dates else None