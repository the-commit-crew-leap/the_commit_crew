import pandas as pd
import numpy as np
import pytest

from config import config
from src.analysis.analysis_engine import AnalysisEngine
from src.analysis.analysis_helper import AnalysisHelper
from src.analysis.data_loader import DataLoader


helper = AnalysisHelper()
data_loader = DataLoader()
engine = AnalysisEngine(data_loader=data_loader, helper=helper)


# RETURNS
def test_get_returns_for_tickers(monkeypatch, csv_file):
    monkeypatch.setattr(
        config,
        "get_ticker_csv_path",
        lambda _: csv_file
    )

    result = engine.get_returns_for_tickers(
        ["AAPL"],
        "2026-01-01",
        "2026-01-03"
    )

    assert isinstance(result, pd.DataFrame)
    assert "AAPL" in result.columns

    # 100 -> 102 -> 101
    assert result.loc[pd.Timestamp("2026-01-02"), "AAPL"] == pytest.approx(0.02)

    assert result.loc[pd.Timestamp("2026-01-03"), "AAPL"] == pytest.approx(101 / 102 - 1)


def test_get_returns_invalid_date_range():
    with pytest.raises(ValueError):
        engine.get_returns_for_tickers(
            ["AAPL"],
            "2026-01-03",
            "2026-01-01"
        )


def test_get_returns_for_tickers_missing_data(monkeypatch):
    monkeypatch.setattr(
        data_loader,
        "load_price_data",
        lambda *args, **kwargs: pd.DataFrame()
    )

    result = engine.get_returns_for_tickers(
        ["AAPL"],
        "2026-01-01",
        "2026-01-03"
    )

    assert isinstance(result, pd.DataFrame)
    assert result.empty


def test_get_returns_uses_close_when_adj_close_has_no_values(monkeypatch):
    price_data = pd.DataFrame({
        "date": pd.to_datetime([
            "2026-01-01",
            "2026-01-02",
            "2026-01-03",
        ]),
        "close": [100, 102, 101],
        "adj_close": [None, None, None],
    })

    monkeypatch.setattr(
        data_loader,
        "load_price_data",
        lambda *args, **kwargs: price_data.copy()
    )

    result = engine.get_returns_for_tickers(
        ["AAPL"],
        "2026-01-01",
        "2026-01-03"
    )

    assert "AAPL" in result.columns
    assert result.loc[pd.Timestamp("2026-01-02"), "AAPL"] == pytest.approx(0.02)


# SUMMARY STATISTICS
def test_calculate_summary_statistics(monkeypatch, csv_file):
    monkeypatch.setattr(
        config,
        "get_ticker_csv_path",
        lambda _: csv_file
    )

    result = engine.calculate_summary_statistics(
        ["AAPL"],
        "2026-01-01",
        "2026-01-03"
    )

    assert isinstance(result, pd.DataFrame)
    assert len(result) == 1
    assert result.iloc[0]["Ticker"] == "AAPL"

    expected_columns = {
        "Observations",
        "Annualized Return (%)",
        "Annualized Volatility (%)",
        "Sharpe Ratio",
    }

    assert expected_columns.issubset(result.columns)


def test_calculate_summary_statistics_no_data(monkeypatch):
    monkeypatch.setattr(
        engine,
        "get_returns_for_tickers",
        lambda *args: pd.DataFrame()
    )

    result = engine.calculate_summary_statistics(
        ["AAPL"],
        "2026-01-01",
        "2026-01-03"
    )

    assert isinstance(result, pd.DataFrame)
    assert result.empty


def test_calculate_summary_statistics_skips_empty_ticker(monkeypatch):
    returns = pd.DataFrame({
        "AAPL": [np.nan, np.nan],
    })

    monkeypatch.setattr(
        engine,
        "get_returns_for_tickers",
        lambda *args: returns
    )

    result = engine.calculate_summary_statistics(
        ["AAPL"],
        "2026-01-01",
        "2026-01-03"
    )

    assert isinstance(result, pd.DataFrame)
    assert result.empty


# ============================================================
# COMPUTE INSIGHTS
# ============================================================
def test_compute_insights(monkeypatch):
    fake_summary = pd.DataFrame({
        "Ticker": ["AAPL"],
        "Annualized Return (%)": [10]
    })

    monkeypatch.setattr(
        engine,
        "plot_correlation_heatmap",
        lambda *args: None
    )

    monkeypatch.setattr(
        engine,
        "plot_volatility_trends",
        lambda *args, **kwargs: None
    )

    monkeypatch.setattr(
        engine,
        "plot_asset_class_volatility",
        lambda *args: None
    )

    monkeypatch.setattr(
        engine,
        "calculate_summary_statistics",
        lambda *args: fake_summary
    )

    result = engine.compute_insights(
        tickers=["AAPL"]
    )

    assert isinstance(result, pd.DataFrame)
    assert result.iloc[0]["Ticker"] == "AAPL"


def test_compute_insights_invalid_volatility_window():
    with pytest.raises(ValueError):

        engine.compute_insights(
            tickers=["AAPL"],
            volatility_window=1
        )


def test_compute_insights_no_tickers(monkeypatch):
    monkeypatch.setattr(
        helper,
        "get_tickers_for_analysis",
        lambda *args, **kwargs: []
    )

    result = engine.compute_insights()

    assert isinstance(result, pd.DataFrame)
    assert result.empty


def test_compute_insights_empty_summary(monkeypatch):
    monkeypatch.setattr(
        helper,
        "get_tickers_for_analysis",
        lambda *args, **kwargs: ["AAPL"]
    )

    monkeypatch.setattr(
        helper,
        "get_date_range",
        lambda *args, **kwargs: ("2026-01-01", "2026-01-03")
    )

    monkeypatch.setattr(
        engine,
        "plot_correlation_heatmap",
        lambda *args, **kwargs: None
    )

    monkeypatch.setattr(
        engine,
        "plot_volatility_trends",
        lambda *args, **kwargs: None
    )

    monkeypatch.setattr(
        engine,
        "plot_asset_class_volatility",
        lambda *args, **kwargs: None
    )

    monkeypatch.setattr(
        engine,
        "calculate_summary_statistics",
        lambda *args, **kwargs: pd.DataFrame()
    )

    result = engine.compute_insights(tickers=["AAPL"])

    assert isinstance(result, pd.DataFrame)
    assert result.empty
