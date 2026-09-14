import pandas as pd
import numpy as np
import logging
from datetime import datetime, timedelta
from pathlib import Path
from sqlalchemy import create_engine, text
from sqlalchemy.exc import SQLAlchemyError
import config

# Set up logging
logger = logging.getLogger(__name__)
logger.setLevel(logging.DEBUG)

# File handler
log_file = config.REPORTS_DIR / "analytics.log"
file_handler = logging.FileHandler(log_file)
file_handler.setLevel(logging.DEBUG)

# Console handler
console_handler = logging.StreamHandler()
console_handler.setLevel(logging.INFO)

# Formatter
formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
file_handler.setFormatter(formatter)
console_handler.setFormatter(formatter)

logger.addHandler(file_handler)
logger.addHandler(console_handler)


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
    
    symbols = config.INSTRUMENTS_LIST
    if not symbols:
        raise ValueError(
            "No instruments configured. "
            "Add tickers to config.INSTRUMENTS."
        )

    end_date = datetime.now()
    start_date = end_date - timedelta(days=365 * config.HISTORICAL_YEARS)

    # Trading days only
    dates = pd.bdate_range(start=start_date, end=end_date)

    symbols = (
        config.INSTRUMENTS_LIST[:3]
        if config.INSTRUMENTS_LIST 
        else ["AAPL", "BND", "SPY"]
    )

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
    """

    logger.info(f"Transforming {len(df)} rows")

    initial_count = len(df)
    df = df.copy()

    # -------------------------
    # Basic cleaning
    # -------------------------

    df = df.dropna(subset=["symbol", "date", "close", "volume"])
    df["symbol"] = (df["symbol"].astype(str).str.upper())
    df["date"] = (pd.to_datetime(df["date"]).dt.normalize())
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


    # -------------------------
    # Validate prices
    # -------------------------

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


    # -------------------------
    # Remove duplicates
    # -------------------------

    before = len(df)
    df = (df.sort_values(["symbol", "date"]).drop_duplicates(subset=["symbol","date"],keep="last"))

    logger.info(f"Removed {before - len(df)} duplicates")


    # -------------------------
    # Enrichment
    # -------------------------

    df["price_change"] = (df["close"] - df["open"])
    df["pct_change"] = ((df["close"] - df["open"]) / df["open"] * 100).round(2)
    df["load_timestamp"] = datetime.now()
    df = (df.sort_values(["symbol", "date"]).reset_index(drop=True))

    logger.info(f"Transformation complete: {initial_count} → {len(df)} rows")

    return df


# ============================================================
# LOAD
# ============================================================

def load(df: pd.DataFrame) -> dict:
    """
    Save each ticker into its own CSV.

    Ensures one row per:
        symbol + date
    """

    if df.empty:
        return {"saved": 0, "errors": ["Empty dataframe"]}

    errors = []
    saved = 0

    for symbol in df["symbol"].unique():

        try:
            ticker_df = (df[df["symbol"] == symbol].copy())
            path = (config.get_ticker_csv_path(symbol))

            if path.exists():
                existing = pd.read_csv(path, parse_dates=["date"])

                existing["date"] = (pd.to_datetime(existing["date"]).dt.normalize())
                combined = pd.concat([existing, ticker_df], ignore_index=True)
            else:
                combined = ticker_df

            # Ensure clean dates
            combined["date"] = (pd.to_datetime(combined["date"]).dt.normalize())
            
            # Keep newest version
            combined = (combined.sort_values(["date","load_timestamp"])
                        .drop_duplicates(subset=["date"],keep="last")
                        .sort_values("date"))
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

    logger.info("Starting ETL pipeline")

    raw = extract()
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


if __name__ == "__main__":
    result = run_etl()
    print(result)

