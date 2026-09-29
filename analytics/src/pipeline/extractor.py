import pandas as pd
import yfinance as yf
import logging

from config import config

# Set up logging
logger = logging.getLogger(__name__)


class Extractor:
    """Extract price data from yfinance."""
    
    def group_price_history(prices: pd.DataFrame) -> pd.DataFrame:
        """Return price history ordered by ticker and date.

        Args:
            prices: Raw or normalized price history records.

        Returns:
            A copy of the input records ordered by ticker then date.
        """
        return prices.sort_values(["ticker", "date"]).reset_index(drop=True)
    
    
    def extract(self, tickers: list[str]) -> pd.DataFrame:
        """
        Fetch historical price data from yfinance for given tickers.
        
        Args:
            tickers: List of ticker symbols to fetch.
            
        Returns:
            DataFrame with columns: date, symbol, open, high, low, close, volume, adj_close.
            
        Raises:
            ValueError: If no data is returned or required fields are missing.
        """
        logger.info("Extracting price history for %s tickers", len(tickers))
        
        raw = yf.download(
            tickers=tickers,
            period="10y",
            interval="1d",
            auto_adjust=False,
            progress=False,
            group_by="column",
            threads=True
        )
        
        if raw.empty:
            raise ValueError("No price data returned from yfinance")
        
        # Handle single vs multiple tickers
        if isinstance(raw.columns, pd.MultiIndex):
            prices = (
                raw.stack(level=1)
                .rename_axis(index=["date", "ticker"])
                .reset_index()
            )
        else:
            prices = raw.reset_index()
            prices["ticker"] = tickers[0]
        
        # Normalize column names
        prices.columns = [str(col).lower().replace(" ", "_") for col in prices.columns]
        
        # Validate required columns
        missing_columns = config.REQUIRED_PRICE_COLUMNS - set(prices.columns)
        if missing_columns:
            raise ValueError(f"Price history is missing columns: {sorted(missing_columns)}")
        
        # Validate tickers are present
        found_tickers = set(prices["ticker"].dropna())
        missing_tickers = [ticker for ticker in tickers if ticker not in found_tickers]
        if missing_tickers:
            raise ValueError(f"Price history is missing tickers: {missing_tickers}")
        
        # Rename ticker → symbol for consistency
        prices = prices.rename(columns={"ticker": "symbol"})
        
        # Sort by symbol and date
        prices = prices.sort_values(["symbol", "date"]).reset_index(drop=True)
        
        logger.info("Extracted %s price history rows", len(prices))
        return prices
    
