import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
import logging
from datetime import datetime, timedelta

from config import config

logger = logging.getLogger(__name__)


# ============================================================
# TICKER HELPER
# ============================================================
def normalize_ticker(ticker: str) -> str:
    """Normalize a ticker symbol to the same format used by the ETL."""
    return str(ticker).strip().upper()


# ============================================================
# PLOT HELPERS
# ============================================================
def save_plot(fig: plt.Figure, filename: str) -> None:
    """
    Save a Matplotlib figure to the configured charts directory.
    The charts directory is created automatically if it does not already
    exist.

    Args:
        fig: Matplotlib Figure object to save.
        filename: Name of the output PNG file.

    Returns:
        None.
    """
    config.CHARTS_DIR.mkdir(parents=True, exist_ok=True)

    path = config.CHARTS_DIR / filename

    fig.savefig(path, dpi=300, bbox_inches="tight")
    plt.close(fig)

    logger.info(f"Saved plot: {path}")


def get_ticker_label(tickers: list) -> str:
    """
    Convert a list of ticker symbols into a filesystem-friendly label.
    The ticker symbols are converted to uppercase and joined with hyphens.
    """
    cleaned_tickers = []

    for ticker in tickers:
        ticker = normalize_ticker(ticker=ticker)
        ticker = ticker.replace("/", "-")
        ticker = ticker.replace(" ", "-")
        cleaned_tickers.append(ticker)

    return "-".join(cleaned_tickers)


def get_ticker_title(tickers: list) -> str:
    """
    Convert a list of ticker symbols into a readable plot-title label.
    """
    return ", ".join(normalize_ticker(ticker=ticker) for ticker in tickers)


# ============================================================
# TICKER SELECTION
# ============================================================
def get_tickers_for_analysis(tickers: list = None, asset_classes: list = None) -> list:
    """
    Determine which instruments should be included in the analysis.
    Selection priority is:

        1. Explicit tickers.
        2. Tickers belonging to the requested asset classes.
        3. All instruments configured in config.INSTRUMENTS_LIST.

    Duplicate ticker symbols are removed while preserving their original
    order.

    Args:
        tickers: Optional list of specific ticker symbols, for example
            ["AAPL", "BND", "SPY"].

        asset_classes: Optional list of asset classes, for example
            ["stocks", "bonds", "etfs"].

    Returns:
        List of ticker symbols to analyze.

    Raises:
        No exception is raised for unknown asset classes. Unknown classes
        generate a warning and are skipped.
    """
    if tickers:
        return list(dict.fromkeys(normalize_ticker(t) for t in tickers if t is not None and str(t).strip()))

    instruments = {str(k).strip().lower(): v for k, v in config.INSTRUMENTS.items()}
    
    if asset_classes:
        selected = []

        for asset_class in asset_classes:
            key = str(asset_class).strip().lower()

            if key not in instruments:
                logger.warning(f"Unknown asset class: {asset_class}")
                continue

            selected.extend(normalize_ticker(t) for t in instruments[key])

        return list(dict.fromkeys(selected))

    
    return list(dict.fromkeys(normalize_ticker(t) for t in (config.INSTRUMENTS_LIST or [])))


# ============================================================
# DATA LOADING
# ============================================================
def load_price_data(symbol: str, start_date: str = None, end_date: str = None) -> pd.DataFrame:
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
    symbol = normalize_ticker(symbol)
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


# ============================================================
# DATE HANDLING
# ============================================================
def get_date_range(period: str = "1y") -> tuple:
    """
    Convert a human-readable analysis period into start and end dates.
    Supported periods are:

        "1y"  - approximately one year.
        "5y"  - approximately five years.
        "10y" - approximately ten years.
        "all" - all available historical data.

    Calendar days are used when calculating the starting date. Trading-day
    filtering is handled naturally by the data available in the CSV files.

    Args:
        period: Analysis period. Supported values are "1y", "5y", "10y",
            and "all".

    Returns:
        Tuple containing:
            start_date: String in YYYY-MM-DD format.
            end_date: String in YYYY-MM-DD format.

    Raises:
        ValueError: If an unsupported period is supplied.
    """
    end_date = datetime.now()

    if period == "1y":
        start_date = end_date - timedelta(days=365)
    elif period == "5y":
        start_date = end_date - timedelta(days=365 * 5)
    elif period == "10y":
        start_date = end_date - timedelta(days=365 * 10)
    elif period == "all":
        start_date = datetime(1900, 1, 1)
    else:
        raise ValueError(f"Unknown period '{period}'. Use '1y', '5y', '10y', or 'all'.")

    return start_date.strftime("%Y-%m-%d"), end_date.strftime("%Y-%m-%d")


# ============================================================
# RETURN CALCULATIONS
# ============================================================
def get_returns_for_tickers(tickers: list, start_date: str, end_date: str) -> pd.DataFrame:
    """
    Calculate daily close-to-close returns for multiple instruments.
    Adjusted closing price is used when available. Otherwise, regular
    closing price is used.

    The function loads seven additional calendar days before start_date.
    This is necessary because the first requested trading day's return
    requires the previous trading day's price.

    Args:
        tickers: List of ticker symbols.

        start_date: Inclusive analysis start date.

        end_date: Inclusive analysis end date.

    Returns:
        DataFrame indexed by date with one column per ticker.

        Each value represents that ticker's daily return.

        An empty DataFrame is returned if none of the requested tickers
        have usable data.
    """
    returns_data = {}
    requested_start = pd.Timestamp(start_date)
    requested_end = pd.Timestamp(end_date)

    if requested_start > requested_end:
        raise ValueError("start_date must be before or equal to end_date")

    data_start = requested_start - pd.Timedelta(days=7)

    for symbol in tickers:
        symbol = normalize_ticker(ticker=symbol)
        price_df = load_price_data(symbol, data_start.strftime("%Y-%m-%d"), end_date)

        if price_df.empty:
            logger.warning(f"No price data available for {symbol}")
            continue

        # Prefer adjusted close only when it actually contains data.
        if "adj_close" in price_df.columns:
            adjusted = pd.to_numeric(price_df["adj_close"], errors="coerce")
        else:
            adjusted = pd.Series(dtype="float64")

        if adjusted.notna().any():
            price = adjusted
        else:
            price = pd.to_numeric(price_df["close"], errors="coerce")
        
        price_df = price_df.copy()
        price_df["price"] = price
        price_df = price_df.dropna(subset=["price"])
        price_df = price_df[price_df["price"] > 0]

        if price_df.empty:
            logger.warning(f"No valid price data available for {symbol}")
            continue

        price_df = price_df.set_index("date").sort_index()
        
        # Do not fill missing observations when calculating returns.
        returns = price_df["price"].pct_change(fill_method=None)
        returns.name = symbol
        returns_data[symbol] = returns

    if not returns_data:
        logger.warning("No return data could be calculated")
        return pd.DataFrame()

    returns_df = pd.concat(returns_data.values(), axis=1)
    returns_df = returns_df.loc[(returns_df.index >= requested_start) & (returns_df.index <= requested_end)]
    returns_df = returns_df.sort_index()

    return returns_df


# ============================================================
# ASSET CLASS HELPERS
# ============================================================
def get_asset_class(ticker: str) -> str:
    """
    The function searches config.INSTRUMENTS and returns the first matching
    asset class.

    Args:
        ticker: Ticker symbol.

    Returns:
        Capitalized asset class name, such as "Stocks", "Bonds", or "Etfs".

        Returns "Unknown" if the ticker is not configured in any asset class.
    """
    ticker = normalize_ticker(ticker=ticker)
    for asset_class, class_tickers in config.INSTRUMENTS.items():
        if ticker in class_tickers:
            return asset_class.capitalize()

    return "Unknown"


# ============================================================
# CORRELATION ANALYSIS
# ============================================================
def plot_correlation_heatmap(tickers: list, start_date: str, end_date: str, period_label: str) -> None:
    """
    Generate and save a correlation heatmap of daily returns.
    Pearson correlation is calculated between the daily returns of each
    pair of instruments.

    Args:
        tickers: List of ticker symbols to analyze.
        start_date: Inclusive analysis start date.
        end_date: Inclusive analysis end date.
        period_label: Human-readable period label such as "1Y" or "5Y".

    Returns:
        None. The resulting PNG is saved to config.CHARTS_DIR.
    """
    returns_df = get_returns_for_tickers(tickers, start_date, end_date)

    if returns_df.empty:
        logger.warning("No data available for correlation plot")
        return

    if len(returns_df.columns) < 2:
        logger.warning("At least two tickers are required for a correlation heatmap")
        return

    correlation = returns_df.corr()
    ticker_label = get_ticker_label(returns_df.columns.tolist())
    ticker_title = get_ticker_title(returns_df.columns.tolist())

    fig, ax = plt.subplots(figsize=(10, 8))
    sns.heatmap(correlation, annot=True, fmt=".2f", cmap="coolwarm", center=0, vmin=-1, vmax=1, square=True, cbar_kws={"label": "Correlation"}, ax=ax)

    ax.set_title(f"Correlation Matrix of Daily Returns — {ticker_title} — {period_label}", fontsize=16, fontweight="bold")
    fig.tight_layout()
    filename = f"correlation_{ticker_label}_{period_label}.png"
    save_plot(fig, filename)


# ============================================================
# ROLLING VOLATILITY
# ============================================================
def plot_volatility_trends(tickers: list, start_date: str, end_date: str, period_label: str, window: int = 30) -> None:
    """
    Generate and save rolling annualized volatility trends.
    The resulting values are displayed as percentages -> 0.20 annualized volatility = 20%

    Args:
        tickers: List of ticker symbols to analyze.
        start_date: Inclusive analysis start date.
        end_date: Inclusive analysis end date.
        period_label: Human-readable period label such as "1Y" or "5Y".
        window: Number of trading observations used for the rolling
            volatility calculation. Default is 30 trading days.

    Returns:
        None. The resulting PNG is saved to config.CHARTS_DIR.
    """
    if window < 2:
        raise ValueError("Volatility window must be at least 2 trading days")
    
    returns_df = get_returns_for_tickers(tickers, start_date, end_date)

    if returns_df.empty:
        logger.warning("No data available for volatility plot")
        return

    ticker_label = get_ticker_label(returns_df.columns.tolist())
    ticker_title = get_ticker_title(returns_df.columns.tolist())

    fig, ax = plt.subplots(figsize=(14, 7))
    plotted = 0

    for symbol in returns_df.columns:
        rolling_volatility = returns_df[symbol].rolling(window=window, min_periods=window).std() * np.sqrt(252) * 100

        if rolling_volatility.notna().any():
            ax.plot(rolling_volatility.index, rolling_volatility, linewidth=1.8, label=symbol, alpha=0.85)
            plotted += 1

    if plotted == 0:
        plt.close(fig)
        logger.warning(f"Not enough data to calculate {window}-day rolling volatility")
        return

    ax.set_title(f"{window}-Day Rolling Annualized Volatility — {ticker_title} — {period_label}", fontsize=14, fontweight="bold")
    ax.set_xlabel("Date", fontsize=12)
    ax.set_ylabel("Annualized Volatility (%)", fontsize=12)
    ax.grid(True, alpha=0.3)
    ax.legend(loc="best", fontsize=9)
    fig.tight_layout()
    filename = f"rolling_volatility_{window}d_{ticker_label}_{period_label}.png"
    save_plot(fig, filename)


# ============================================================
# ASSET CLASS VOLATILITY
# ============================================================
def plot_asset_class_volatility(tickers: list, start_date: str, end_date: str, period_label: str) -> None:
    """
    Compare annualized volatility across individual securities and
    configured asset classes.
    Each ticker is represented as an individual point while the box plot
    summarizes the distribution of volatility within each asset class.

    Args:
        tickers: List of ticker symbols to analyze.
        start_date: Inclusive analysis start date.
        end_date: Inclusive analysis end date.
        period_label: Human-readable period label such as "1Y" or "5Y".
    """
    returns_df = get_returns_for_tickers(tickers, start_date, end_date)

    if returns_df.empty:
        logger.warning("No data available for asset class volatility plot")
        return

    volatility = returns_df.std() * np.sqrt(252) * 100
    data_for_plot = []

    for ticker in returns_df.columns:
        if pd.isna(volatility[ticker]):
            continue
        data_for_plot.append({"Asset Class": get_asset_class(ticker), "Ticker": ticker, "Annualized Volatility": volatility[ticker]})

    plot_df = pd.DataFrame(data_for_plot)

    if plot_df.empty:
        logger.warning("No valid volatility data for asset class plot")
        return

    ticker_label = get_ticker_label(plot_df["Ticker"].tolist())
    ticker_title = get_ticker_title(plot_df["Ticker"].tolist())

    fig, ax = plt.subplots(figsize=(12, 7))
    sns.boxplot(data=plot_df, x="Asset Class", y="Annualized Volatility", color="lightblue", width=0.5, showfliers=False, ax=ax)
    sns.stripplot(data=plot_df, x="Asset Class", y="Annualized Volatility", hue="Ticker", size=8, jitter=True, ax=ax)
    ax.set_title(f"Annualized Volatility by Asset Class — {ticker_title} — {period_label}", fontsize=14, fontweight="bold")
    ax.set_xlabel("Asset Class", fontsize=12)
    ax.set_ylabel("Annualized Volatility (%)", fontsize=12)
    ax.grid(True, alpha=0.3, axis="y")
    ax.legend(title="Ticker", bbox_to_anchor=(1.02, 1), loc="upper left")
    fig.tight_layout()
    filename = f"asset_class_volatility_{ticker_label}_{period_label}.png"
    save_plot(fig, filename)


# ============================================================
# SUMMARY STATISTICS
# ============================================================
def calculate_summary_statistics(tickers: list, start_date: str, end_date: str) -> pd.DataFrame:
    """
    Calculate summary statistics for each analyzed instrument.

    The following metrics are calculated:

        Observations
            Number of available daily return observations.

        Annualized Return
            Geometric annualized return based on the sequence of daily
            returns.

        Annualized Volatility
            Standard deviation of daily returns multiplied by sqrt(252).

        Sharpe Ratio
            Annualized return divided by annualized volatility.

            This is a simplified Sharpe ratio that assumes a zero
            risk-free rate.

        Best Daily Return
            Largest observed daily return.

        Worst Daily Return
            Smallest observed daily return.

    Args:
        tickers: List of ticker symbols.
        start_date: Inclusive analysis start date.
        end_date: Inclusive analysis end date.

    Returns:
        DataFrame containing one row per ticker.
        An empty DataFrame is returned if no usable return data exists.
    """
    returns_df = get_returns_for_tickers(tickers, start_date, end_date)

    if returns_df.empty:
        return pd.DataFrame()

    rows = []

    for ticker in returns_df.columns:
        returns = returns_df[ticker].dropna()

        if returns.empty:
            continue

        annualized_return = (1 + returns).prod() ** (252 / len(returns)) - 1
        annualized_volatility = returns.std() * np.sqrt(252)
        sharpe_ratio = annualized_return / annualized_volatility if annualized_volatility > 0 else np.nan
        
        rows.append({
            "Ticker": ticker, 
            "Asset Class": get_asset_class(ticker), 
            "Observations": len(returns), 
            "Annualized Return (%)": annualized_return * 100, 
            "Annualized Volatility (%)": annualized_volatility * 100, 
            "Sharpe Ratio": sharpe_ratio, 
            "Best Daily Return (%)": returns.max() * 100, 
            "Worst Daily Return (%)": returns.min() * 100
        })

    if not rows:
        return pd.DataFrame()

    return pd.DataFrame(rows).sort_values("Ticker").reset_index(drop=True)


# ============================================================
# MAIN ANALYSIS FUNCTION
# ============================================================
def compute_insights(tickers: list = None, asset_classes: list = None, period: str = "1y", volatility_window: int = 30) -> pd.DataFrame:
    """
    Run the complete portfolio analysis.
    The function selects the requested instruments, determines the analysis
    date range, generates all requested plots, calculates summary
    statistics, and returns those statistics as a DataFrame.

    Generated plots:
        1. Correlation heatmap.
        2. Rolling annualized volatility.
        3. Asset-class volatility comparison.

    Args:
        tickers: Optional list of specific ticker symbols.

        asset_classes: Optional list of asset classes.

        period: Analysis period.

        volatility_window: Rolling window size for the volatility chart.

    Returns:
        DataFrame containing summary statistics for each analyzed ticker.
        Returns None if the analysis fails.
    """
    if volatility_window < 2:
        raise ValueError("Volatility window must be at least 2")
    
    analysis_tickers = get_tickers_for_analysis(tickers, asset_classes)

    if not analysis_tickers:
        logger.error("No tickers selected. Check config.INSTRUMENTS.")
        return pd.DataFrame()

    start_date, end_date = get_date_range(period)
    period_label = period.upper()

    logger.info(f"Analysis date range: {start_date} to {end_date}")
    logger.info(f"Analyzing tickers: {analysis_tickers}")
    
    # plots
    plot_correlation_heatmap(analysis_tickers, start_date, end_date, period_label)
    plot_volatility_trends(analysis_tickers, start_date, end_date, period_label, window=volatility_window)
    plot_asset_class_volatility(analysis_tickers, start_date, end_date, period_label)

    # summary
    summary = calculate_summary_statistics(analysis_tickers, start_date, end_date)

    if not summary.empty:
        logger.info("Summary statistics:")
        logger.info("\n" + summary.to_string(index=False))
    else:
        logger.warning("No summary statistics could be calculated.")

    return summary


# ============================================================
# MAIN
# ============================================================
if __name__ == "__main__":
    summary = compute_insights(
        tickers=["AAPL", "BND", "SPY"], 
        period="1y", 
        volatility_window=30
    )

    if summary is not None and not summary.empty:
        print("\nAnalysis Summary:")
        print(summary.to_string(index=False))