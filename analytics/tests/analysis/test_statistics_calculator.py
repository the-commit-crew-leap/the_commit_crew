import numpy as np
import pandas as pd

from src.analysis.analysis_helper import AnalysisHelper
from src.analysis.statistics_calculator import StatisticsCalculator


def test_calculate_summary_statistics_preserves_existing_schema():
    returns = pd.DataFrame({"AAPL": [0.01, -0.005, 0.02]})

    result = StatisticsCalculator(AnalysisHelper()).calculate(returns)

    assert result.loc[0, "Ticker"] == "AAPL"
    assert result.loc[0, "Asset Class"] == "Stocks"
    assert result.loc[0, "Observations"] == 3
    assert result.loc[0, "Annualized Return (%)"] == (
        (1.01 * 0.995 * 1.02) ** (252 / 3) - 1
    ) * 100


def test_calculate_summary_statistics_skips_symbols_without_returns():
    returns = pd.DataFrame({"AAPL": [np.nan, np.nan], "SPY": [0.01, 0.02]})

    result = StatisticsCalculator(AnalysisHelper()).calculate(returns)

    assert result["Ticker"].tolist() == ["SPY"]


def test_calculate_summary_statistics_empty_returns():
    result = StatisticsCalculator(AnalysisHelper()).calculate(pd.DataFrame())

    assert result.empty
