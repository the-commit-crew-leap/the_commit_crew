import pandas as pd
import logging
from datetime import datetime

# Set up logging
logger = logging.getLogger(__name__)


class Transformer:
    """Transform raw price data into clean, validated format."""
    
    def transform(self, df: pd.DataFrame) -> pd.DataFrame:
        """
        Clean and enrich extracted price data.
        
        This stage is intentionally independent from extract() and
        load(). It can therefore be tested using an arbitrary
        DataFrame without touching the network or filesystem.
        """

        logger.info(f"Transforming {len(df)} rows")
        
        if df.empty:
            raise ValueError("Cannot transform an empty DataFrame.")

        initial_count = len(df)
        df = df.copy()
        
        # Required columns
        required_columns = {
            "symbol",
            "date",
            "open",
            "high",
            "low",
            "close",
            "volume",
            "adj_close",
        }

        missing_columns = required_columns - set(df.columns)
        if missing_columns:
            raise ValueError(f"Missing required columns: {sorted(missing_columns)}")

        # Basic cleaning
        # Remove rows with invalid required values
        df = df.dropna(subset=["symbol", "date", "close", "volume"])
        
        # Normalize symbol names.
        df["symbol"] = (df["symbol"].astype(str).str.upper().str.strip())
        
        # Convert dates and normalize them to midnight.
        df["date"] = (pd.to_datetime(df["date"],errors="coerce").dt.normalize())
        
        # Convert numeric fields.
        numeric_columns = [
            "open",
            "high",
            "low",
            "close",
            "adj_close"
        ]
        
        for col in numeric_columns:
            df[col] = pd.to_numeric(df[col], errors="coerce")

        df["volume"] = (pd.to_numeric(df["volume"], errors="coerce").astype("Int64"))
        
        invalid_volume = df["volume"] < 0
        
        if invalid_volume.any():
            logger.warning(f"Removing {invalid_volume.sum()} rows with invalid volume")
            df = df.loc[~invalid_volume]

        # Validate prices
        invalid_prices = (
            (df["high"] < df["low"]) |
            (df["high"] < df["open"]) |
            (df["high"] < df["close"]) |
            (df["low"] > df["open"]) |
            (df["low"] > df["close"]) |
            (df["close"] <= 0)
        )

        if invalid_prices.any():
            logger.warning(f"Removing {invalid_prices.sum()} invalid rows")
            df = df.loc[ ~invalid_prices]

        # Remove duplicates
        before = len(df)
        df = df.drop_duplicates(subset=["symbol", "date"], keep="last")
        df = df.sort_values(["symbol", "date"])

        logger.info(f"Removed {before - len(df)} duplicates")
        
        # Make sure cleaning didn't remove everything
        if df.empty:
            raise ValueError("No valid rows remain after transformation.")

        # Enrichment
        df["price_change"] = (df["close"] - df["open"])
        df["pct_change"] = ((df["close"] - df["open"]) / df["open"] * 100).round(2)
        df["load_timestamp"] = datetime.now()
        df = (df.sort_values(["symbol", "date"]).reset_index(drop=True))

        logger.info(f"Transformation complete: {initial_count} → {len(df)} rows")

        return df
  