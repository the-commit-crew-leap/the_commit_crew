import sys
from pathlib import Path
import logging
from datetime import datetime
import pandas as pd

analytics_dir = Path(__file__).resolve().parent.parent
if str(analytics_dir) not in sys.path: sys.path.insert(0, str(analytics_dir))
   
from config import config
from analysis import compute_insights, get_tickers_for_analysis


logger = logging.getLogger(__name__)
logger.setLevel(logging.DEBUG)

# Set up logging
file_handler = logging.FileHandler(config.LOG_FILE)
file_handler.setLevel(logging.DEBUG)
console_handler = logging.StreamHandler()
console_handler.setLevel(logging.INFO)
formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
file_handler.setFormatter(formatter)
console_handler.setFormatter(formatter)
logger.addHandler(file_handler)
logger.addHandler(console_handler)


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
        best = stats.loc[stats["Annualized Return (%)"].idxmax()]
        worst = stats.loc[stats["Annualized Return (%)"].idxmin()]
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
        highest = stats.loc[stats["Annualized Volatility (%)"].idxmax()]
        lowest = stats.loc[stats["Annualized Volatility (%)"].idxmin()]
        lines += [
            f"Highest historical volatility: {highest['Ticker']} ({highest['Annualized Volatility (%)']:.2f}%)",
            f"Lowest historical volatility: {lowest['Ticker']} ({lowest['Annualized Volatility (%)']:.2f}%)",
            ""
        ]

    if "Sharpe Ratio" in stats.columns:
        best_sharpe = stats.loc[stats["Sharpe Ratio"].idxmax()]
        lines += [
            f"Best historical Sharpe ratio: {best_sharpe['Ticker']} ({best_sharpe['Sharpe Ratio']:.2f})",
            ""
        ]

    return "\n".join(lines)


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


if __name__ == "__main__":
    run_dashboard(tickers=["AAPL", "BND", "SPY"], period="1y")
