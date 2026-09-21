import logging
from datetime import datetime
import pandas as pd

from config import config
from src.analysis import compute_insights, get_tickers_for_analysis

logger = logging.getLogger(__name__)


# ============================================================
# FORMATTING
# ============================================================
def format_dashboard(stats: pd.DataFrame, tickers: list, period: str) -> str:
    """
    Create a simple human-readable portfolio dashboard.

    Args:
        stats: DataFrame returned by compute_insights().
        tickers: Tickers included in the analysis.
        period: Analysis period such as '1y' or '5y'.

    Returns:
        Formatted dashboard text.
    """
    lines = [
        "=" * 70,
        "PORTFOLIO ANALYSIS DASHBOARD",
        "=" * 70,
        f"Generated: {datetime.now():%Y-%m-%d %H:%M:%S}",
        f"Period: {period.upper()}",
        f"Tickers: {', '.join(tickers)}",
        ""
    ]

    if stats is None or stats.empty:
        lines.append("No analysis data available.")
        return "\n".join(lines)

    lines += ["-" * 70, "SUMMARY", "-" * 70, ""]

    for _, row in stats.iterrows():
        ticker = row.get("Ticker", "N/A")
        asset_class = row.get("Asset Class", "Unknown")
        lines.append(f"{ticker} ({asset_class})")

        if pd.notna(row.get("Annualized Return (%)")):
            lines.append(f"  Annualized Return: {row['Annualized Return (%)']:.2f}%")

        if pd.notna(row.get("Annualized Volatility (%)")):
            lines.append(f"  Annualized Volatility: {row['Annualized Volatility (%)']:.2f}%")

        if pd.notna(row.get("Sharpe Ratio")):
            lines.append(f"  Sharpe Ratio: {row['Sharpe Ratio']:.2f}")

        lines.append("")

    if "Annualized Return (%)" in stats.columns:
        valid_returns = stats.dropna(subset=["Annualized Return (%)"])

        if not valid_returns.empty:
            best = valid_returns.loc[valid_returns["Annualized Return (%)"].idxmax()]
            worst = valid_returns.loc[valid_returns["Annualized Return (%)"].idxmin()]

            lines += [
                "-" * 70,
                "KEY INSIGHTS",
                "-" * 70,
                "",
                f"Best historical return: {best['Ticker']} ({best['Annualized Return (%)']:.2f}%)",
                f"Lowest historical return: {worst['Ticker']} ({worst['Annualized Return (%)']:.2f}%)",
                ""
            ]


    if "Annualized Volatility (%)" in stats.columns:
        valid_volatility = stats.dropna(subset=["Annualized Volatility (%)"])

        if not valid_volatility.empty:
            highest = valid_volatility.loc[valid_volatility["Annualized Volatility (%)"].idxmax()]
            lowest = valid_volatility.loc[valid_volatility["Annualized Volatility (%)"].idxmin()]

            lines += [
                f"Highest historical volatility: {highest['Ticker']} ({highest['Annualized Volatility (%)']:.2f}%)",
                f"Lowest historical volatility: {lowest['Ticker']} ({lowest['Annualized Volatility (%)']:.2f}%)",
                ""
            ]


    if "Sharpe Ratio" in stats.columns:
        valid_sharpe = stats.dropna(subset=["Sharpe Ratio"])

        if not valid_sharpe.empty:
            best_sharpe = valid_sharpe.loc[valid_sharpe["Sharpe Ratio"].idxmax()]

            lines += [
                f"Best historical Sharpe ratio: {best_sharpe['Ticker']} ({best_sharpe['Sharpe Ratio']:.2f})",
                ""
            ]


    return "\n".join(lines)


# ============================================================
# DASHBOARD CREATION
# ============================================================
def run_dashboard(tickers: list = None, asset_classes: list = None, period: str = "1y", volatility_window: int = 30, save_file: bool = True) -> str:
    """
    Run the analysis and create the human-readable dashboard.

    Args:
        tickers: Optional list of ticker symbols.
        asset_classes: Optional list of asset classes.
        period: Analysis period such as '1y', '5y', or '10y'.
        volatility_window: Rolling volatility window in trading days.
        save_file: Whether to save the report as a text file.

    Returns:
        Dashboard text.
    """
    selected_tickers = get_tickers_for_analysis(tickers, asset_classes)

    if not selected_tickers:
        logger.warning("No tickers selected.")
        return ""

    stats = compute_insights(tickers=selected_tickers, period=period, volatility_window=volatility_window)
    dashboard = format_dashboard(stats, selected_tickers, period)

    print(dashboard)

    if save_file:
        filename = f"dashboard_{'-'.join(selected_tickers)}_{period.upper()}.txt"
        path = config.REPORTS_DIR / filename
        path.write_text(dashboard, encoding="utf-8")
        logger.info(f"Dashboard saved to {path}")

    return dashboard


# ============================================================
# MAIN
# ============================================================
if __name__ == "__main__":
    run_dashboard(tickers=["AAPL", "BND", "SPY"], period="1y")
