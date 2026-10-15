import pandas as pd
import yfinance as yf
import logging

from config import config

# Set up logging
logger = logging.getLogger(__name__)


class Extractor:
    """Extract price data from yfinance."""
    
    def group_price_history(prices: pd.DataFrame) -> pd.DataFrame:
        """Return price history ordered by symbol and date.

        Args:
            prices: Raw or normalized price history records.

        Returns:
            A copy of the input records ordered by symbol then date.
        """
        return prices.sort_values(["symbol", "date"]).reset_index(drop=True)
    
    
    def extract(self, symbols: list[str]) -> pd.DataFrame:
        """
        Fetch historical price data from yfinance for given tickers.
        
        Args:
            symbols: List of instrument symbols to fetch.
            
        Returns:
            DataFrame with columns: date, symbol, open, high, low, close, volume, adj_close.
            
        Raises:
            ValueError: If no data is returned or required fields are missing.
        """
        logger.info("Extracting price history for %s symbols", len(symbols))
        
        raw = yf.download(
            tickers=symbols,
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
                .rename_axis(index=["date", "symbol"])
                .reset_index()
            )
        else:
            prices = raw.reset_index()
            prices["symbol"] = symbols[0]
        
        # Normalize column names
        prices.columns = [str(col).lower().replace(" ", "_") for col in prices.columns]
        
        # Validate required columns
        missing_columns = config.REQUIRED_PRICE_COLUMNS - set(prices.columns)
        if missing_columns:
            raise ValueError(f"Price history is missing columns: {sorted(missing_columns)}")
        
        # Validate symbols are present.
        found_symbols = set(prices["symbol"].dropna())
        missing_symbols = [symbol for symbol in symbols if symbol not in found_symbols]
        if missing_symbols:
            raise ValueError(f"Price history is missing symbols: {missing_symbols}")
        
        # Sort by symbol and date
        prices = prices.sort_values(["symbol", "date"]).reset_index(drop=True)
        
        logger.info("Extracted %s price history rows", len(prices))
        return prices
    
