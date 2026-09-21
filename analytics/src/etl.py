from pathlib import Path
import pandas as pd
import numpy as np
import logging
from datetime import datetime, timedelta
from config import config

# Set up logging
logger = logging.getLogger(__name__)


# ============================================================
# EXTRACT
# ============================================================
def extract() -> pd.DataFrame:
    """
    Generate mock historical daily price data.
    Replace this function with yfinance in production.

    Returns:
        DataFrame with:
        symbol, date, open, high, low, close,
        volume, adj_close
    """

    logger.info("Extracting historical price data (MOCK)")
    
    symbols = (
            config.INSTRUMENTS_LIST[:3]
            if config.INSTRUMENTS_LIST
            else ["AAPL", "BND", "SPY"]
        )
    
    if not symbols:
        raise ValueError(
            "No instruments configured. "
            "Add tickers to config.INSTRUMENTS."
        )

    end_date = datetime.now()
    start_date = end_date - timedelta(days=365 * config.HISTORICAL_YEARS)

    # Trading days only
    dates = pd.bdate_range(start=start_date, end=end_date)

    starting_prices = {
        "AAPL": 100,
        "BND": 80,
        "SPY": 200,
    }

    # Different risk profiles
    volatility = {
        "AAPL": 0.018,
        "BND": 0.004,
        "SPY": 0.012,
    }

    rng = np.random.default_rng(42)
    rows = []

    for symbol in symbols:

        price = starting_prices.get(symbol, 100)
        daily_vol = volatility.get(symbol, 0.01)

        for date in dates:

            # Simulate daily return
            daily_return = rng.normal(loc=0, scale=daily_vol)
            open_price = price
            close_price = (open_price * (1 + daily_return))
            high_price = max(open_price, close_price) * (1 + rng.uniform(0, 0.01))
            low_price = min(open_price, close_price) * (1 - rng.uniform(0, 0.01))
            volume = int(rng.uniform(10_000_000, 100_000_000))

            rows.append({
                "symbol": symbol,
                "date": date,
                "open": round(open_price, 2),
                "high": round(high_price, 2),
                "low": round(low_price, 2),
                "close": round(close_price, 2),
                "volume": volume,
                "adj_close": round(close_price, 2),
            })

            # next trading day's starting price
            price = close_price

    df = pd.DataFrame(rows)

    logger.info(f"Extracted {len(df)} rows ({len(symbols)} symbols)")
    logger.info(f"Date range: {df['date'].min()} → {df['date'].max()}")

    return df


# ============================================================
# TRANSFORM
# ============================================================
def transform(df: pd.DataFrame) -> pd.DataFrame:
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


# ============================================================
# LOAD
# ============================================================
def load(df: pd.DataFrame, output_path_factory=None) -> dict:
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


# ============================================================
# PIPELINE RUNNER
# ============================================================
def run_etl():
    """
    Execute the complete ETL workflow.
    The individual stages remain independently callable:

        extract()
        transform(df)
        load(df)

    This function simply orchestrates them.
    """

    logger.info("Starting ETL pipeline")
    
    try: 
        raw = extract()
        
        if raw.empty:
            raise ValueError("Extraction returned no data")
        
        clean = transform(raw)
        result = load(clean)

        summary = {
            "status": ("success" if not result["errors"] else "partial"),
            "extracted": len(raw),
            "transformed": len(clean),
            "saved": result["saved"],
            "errors": result["errors"],
        }

        logger.info(f"ETL Summary: {summary}")

        return summary
    
    except Exception as e:
        logger.error("ETL pipeline failed", exc_info=True)
        
        return {
            "status": "failed",
            "extracted": 0,
            "transformed": 0,
            "saved": 0,
            "errors": [str(e)],
        }


if __name__ == "__main__":
    result = run_etl()
    print(result)

