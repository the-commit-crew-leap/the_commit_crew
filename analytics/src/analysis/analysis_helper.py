from datetime import datetime, timedelta
import logging
import matplotlib.pyplot as plt

from config import config

# Set up logging
logger = logging.getLogger(__name__)

class AnalysisHelper:
    
    def __init__(self):
        self.logger = logging.getLogger(__name__)
    
    
    # TICKER HELPER
    @staticmethod
    def normalize_ticker(ticker: str) -> str:
        """Normalize a ticker symbol to the same format used by the ETL."""
        return str(ticker).strip().upper()
    
    
    # TICKER SELECTION
    def get_tickers_for_analysis(self, tickers: list = None, asset_classes: list = None) -> list:
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
            return list(dict.fromkeys(self.normalize_ticker(t) for t in tickers if t is not None and str(t).strip()))

        instruments = {str(k).strip().lower(): v for k, v in config.INSTRUMENTS.items()}
        
        if asset_classes:
            selected = []

            for asset_class in asset_classes:
                key = str(asset_class).strip().lower()

                if key not in instruments:
                    logger.warning(f"Unknown asset class: {asset_class}")
                    continue

                selected.extend(self.normalize_ticker(t) for t in instruments[key])

            return list(dict.fromkeys(selected))

        
        return list(dict.fromkeys(self.normalize_ticker(t) for t in (config.INSTRUMENTS_LIST or [])))
    
    
    # DATE HANDLING
    @staticmethod
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
    
    
    # ASSET CLASS HELPERS
    def get_asset_class(self, ticker: str) -> str:
        """
        The function searches config.INSTRUMENTS and returns the first matching
        asset class.

        Args:
            ticker: Ticker symbol.

        Returns:
            Capitalized asset class name, such as "Stocks", "Bonds", or "Etfs".
            Returns "Unknown" if the ticker is not configured in any asset class.
        """
        ticker = self.normalize_ticker(ticker=ticker)
        for asset_class, class_tickers in config.INSTRUMENTS.items():
            if ticker in class_tickers:
                return asset_class.capitalize()

        return "Unknown"


    # PLOT HELPERS
    def save_plot(self, fig: plt.Figure, filename: str) -> None:
        """
        Save a Matplotlib figure to the configured charts directory.
        The charts directory is created automatically if it does not already
        exist.

        Args:
            fig: Matplotlib Figure object to save.
            filename: Name of the output PNG file.
        """
        config.CHARTS_DIR.mkdir(parents=True, exist_ok=True)

        path = config.CHARTS_DIR / filename

        fig.savefig(path, dpi=300, bbox_inches="tight")
        plt.close(fig)

        logger.info(f"Saved plot: {path}")


    def get_ticker_label(self, tickers: list) -> str:
        """
        Convert a list of ticker symbols into a filesystem-friendly label.
        The ticker symbols are converted to uppercase and joined with hyphens.
        """
        cleaned_tickers = []

        for ticker in tickers:
            ticker = self.normalize_ticker(ticker=ticker)
            ticker = ticker.replace("/", "-")
            ticker = ticker.replace(" ", "-")
            cleaned_tickers.append(ticker)

        return "-".join(cleaned_tickers)


    def get_ticker_title(self, tickers: list) -> str:
        """
        Convert a list of ticker symbols into a readable plot-title label.
        """
        return ", ".join(self.normalize_ticker(ticker=ticker) for ticker in tickers)
    