import yfinance as yf
import pandas as pd
import logging
from pathlib import Path


logger = logging.getLogger(__name__)

equity_tickers = ["AAPL", "MSFT", "GOOGL", "AMZN", "TSLA", "META", "NVDA", "NFLX", "TTWO", "BABA"]
etf_tickers = ["XLF", "QQQ", "SPY", "VTI", "DIA", "IWM", "XLV", "XLK", "XLE", "VNQ"]
bond_tickers = ["TLT", "SHY", "IEI", "IEF", "TIP", "VGIT", "TLH", "MUB", "LQD", "HYG"]

all_tickers = equity_tickers + etf_tickers + bond_tickers

ticker_class = {ticker: "equity" for ticker in equity_tickers}
ticker_class.update({ticker: "etf" for ticker in etf_tickers})
ticker_class.update({ticker: "bond" for ticker in bond_tickers})

REQUIRED_PRICE_COLUMNS = {"date", "ticker", "open", "high", "low", "close", "volume"}
REQUIRED_METADATA_FIELDS = ("ticker", "asset_class", "long_name", "currency", "tradable")

DATA_DIR = Path(__file__).resolve().parent.parent / "market_data"
DATA_DIR.mkdir(parents=True, exist_ok=True)


def group_price_history(prices: pd.DataFrame) -> pd.DataFrame:
    """Return price history ordered by ticker and date.

    Args:
        prices: Raw or normalized price history records.

    Returns:
        A copy of the input records ordered by ticker then date.
    """
    return prices.sort_values(["ticker", "date"]).reset_index(drop=True)


def fetch_price_history(tickers: list[str]) -> pd.DataFrame:
    """Fetch historical price data from yfinance for given tickers.

    Args:
        tickers: List of ticker symbols to fetch.

    Returns:
        DataFrame with columns: date, ticker, open, high, low, close, volume.

    Raises:
        ValueError: If no data is returned or required fields are missing.
    """
    logger.info("Fetching price history for %s tickers", len(tickers))

    raw = yf.download(
        tickers=tickers,
        period="10y",
        interval="1d",
        auto_adjust=True,
        progress=False,
        group_by="column",
        threads=True,
    )

    if raw.empty:
        raise ValueError("No price data returned from yfinance")

    if isinstance(raw.columns, pd.MultiIndex):
        prices = (
            raw.stack(level=1)
            .rename_axis(index=["date", "ticker"])
            .reset_index()
        )
    else:
        prices = raw.reset_index()
        prices["ticker"] = tickers[0]

    prices.columns = [str(col).lower().replace(" ", "_") for col in prices.columns]

    missing_columns = REQUIRED_PRICE_COLUMNS - set(prices.columns)
    if missing_columns:
        raise ValueError(f"Price history is missing columns: {sorted(missing_columns)}")

    found_tickers = set(prices["ticker"].dropna())
    missing_tickers = [ticker for ticker in tickers if ticker not in found_tickers]
    if missing_tickers:
        raise ValueError(f"Price history is missing tickers: {missing_tickers}")

    prices = group_price_history(prices)

    logger.info("Fetched %s price history rows", len(prices))
    return prices


def fetch_ticker_metadata(tickers: list[str]) -> pd.DataFrame:
    """Fetch ticker metadata from yfinance for given symbols.

    Args:
        tickers: List of ticker symbols to fetch.

    Returns:
        DataFrame with ticker metadata including asset class and currency.

    Raises:
        ValueError: If metadata lookups fail or required fields are missing.
    """
    logger.info("Fetching metadata for %s tickers", len(tickers))

    rows = []
    failed_tickers = {}

    for symbol in tickers:
        ticker = yf.Ticker(symbol)
        info = {}

        try:
            info = ticker.get_info()
        except Exception as error:
            failed_tickers[symbol] = str(error)
            logger.warning("Metadata lookup failed for %s: %s", symbol, error)

        rows.append(
            {
                "ticker": symbol,
                "asset_class": ticker_class.get(symbol),
                "long_name": info.get("longName"),
                "sector": info.get("sector"),
                "currency": info.get("currency"),
                "tradable": True,
            }
        )

    metadata = pd.DataFrame(rows)

    if len(metadata) != len(tickers):
        raise ValueError("Metadata row count does not match the number of requested tickers")

    missing_columns = set(REQUIRED_METADATA_FIELDS) - set(metadata.columns)
    if missing_columns:
        raise ValueError(f"Metadata is missing columns: {sorted(missing_columns)}")

    found_tickers = set(metadata["ticker"].dropna())
    missing_tickers = [ticker for ticker in tickers if ticker not in found_tickers]
    if missing_tickers:
        raise ValueError(f"Metadata is missing tickers: {missing_tickers}")

    missing_required_values = {}
    for field in REQUIRED_METADATA_FIELDS:
        missing_for_field = metadata.loc[metadata[field].isna(), "ticker"].tolist()
        if missing_for_field:
            missing_required_values[field] = missing_for_field

    validation_errors = []
    if failed_tickers:
        validation_errors.append(
            "Metadata lookup failed for tickers: "
            + ", ".join(f"{ticker} ({message})" for ticker, message in failed_tickers.items())
        )
    if missing_required_values:
        validation_errors.append(
            "Metadata is missing required values: "
            + ", ".join(f"{field}={tickers}" for field, tickers in missing_required_values.items())
        )
    if validation_errors:
        raise ValueError("; ".join(validation_errors))

    logger.info("Fetched metadata for %s tickers", len(metadata))
    return metadata


def save_price_history(prices: pd.DataFrame) -> None:
    """Persist price history to CSV.

    Args:
        prices: Price history records.
    """
    grouped_prices = group_price_history(prices)

    logger.info("Writing price history to %s", DATA_DIR / "price_history.csv")
    grouped_prices.to_csv(DATA_DIR / "price_history.csv", index=False)


def save_ticker_metadata(metadata: pd.DataFrame) -> None:
    """Persist ticker metadata to CSV.

    Args:
        metadata: Ticker metadata records.
    """
    logger.info("Writing ticker metadata to %s", DATA_DIR / "ticker_metadata.csv")
    metadata.sort_values(["ticker"]).reset_index(drop=True).to_csv(
        DATA_DIR / "ticker_metadata.csv", index=False
    )


def main() -> None:
    """Fetch market data and persist it to disk."""
    logging.basicConfig(level=logging.INFO)

    prices = fetch_price_history(all_tickers)
    save_price_history(prices)
    
    metadata = fetch_ticker_metadata(all_tickers)
    save_ticker_metadata(metadata)

if __name__ == "__main__":
    main()
