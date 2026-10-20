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
            cumulative_value = (1 + returns).cumprod()
            running_peak = cumulative_value.cummax()
            drawdown = cumulative_value / running_peak - 1
            below_peak = drawdown < 0
            drawdown_groups = (~below_peak).cumsum()
            drawdown_duration = int(
                below_peak.groupby(drawdown_groups).sum().max()
            ) if below_peak.any() else 0
            var_threshold = returns.quantile(0.05)
            tail_returns = returns[returns <= var_threshold]

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
                    "Maximum Drawdown (%)": drawdown.min() * 100,
                    "Current Drawdown (%)": drawdown.iloc[-1] * 100,
                    "Max Drawdown Duration (days)": drawdown_duration,
                    "Historical VaR 95% (%)": max(0.0, -var_threshold * 100),
                    "Expected Shortfall 95% (%)": max(0.0, -tail_returns.mean() * 100),
                }
            )

        if not rows:
            return pd.DataFrame()

        return pd.DataFrame(rows).sort_values("Ticker").reset_index(drop=True)
