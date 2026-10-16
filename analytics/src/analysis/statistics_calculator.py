import numpy as np
import pandas as pd


class StatisticsCalculator:
    """Calculate summary statistics from a daily returns DataFrame."""

    def __init__(self, helper, annualization_factor: int = 252):
        self.helper = helper
        self.annualization_factor = annualization_factor

    def calculate(self, returns_df: pd.DataFrame) -> pd.DataFrame:
        """Return one summary row for each symbol with usable returns."""
        if returns_df.empty:
            return pd.DataFrame()

        rows = []
        for symbol in returns_df.columns:
            returns = returns_df[symbol].dropna()
            if returns.empty:
                continue

            annualized_return = (1 + returns).prod() ** (
                self.annualization_factor / len(returns)
            ) - 1
            annualized_volatility = returns.std() * np.sqrt(self.annualization_factor)
            sharpe_ratio = (
                annualized_return / annualized_volatility
                if annualized_volatility > 0
                else np.nan
            )

            rows.append(
                {
                    "Ticker": symbol,
                    "Asset Class": self.helper.get_asset_class(symbol),
                    "Observations": len(returns),
                    "Annualized Return (%)": annualized_return * 100,
                    "Annualized Volatility (%)": annualized_volatility * 100,
                    "Sharpe Ratio": sharpe_ratio,
                    "Best Daily Return (%)": returns.max() * 100,
                    "Worst Daily Return (%)": returns.min() * 100,
                }
            )

        if not rows:
            return pd.DataFrame()

        return pd.DataFrame(rows).sort_values("Ticker").reset_index(drop=True)
