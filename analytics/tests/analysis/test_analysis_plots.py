import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import pytest

from config import config
from src.analysis.analysis_helper import AnalysisHelper
from src.analysis.analysis_engine import AnalysisEngine
from src.analysis.data_loader import DataLoader


data_loader = DataLoader()
helper = AnalysisHelper()
engine = AnalysisEngine(data_loader=data_loader, helper=helper)


# SAVE PLOT
def test_save_plot(monkeypatch, tmp_path):
    monkeypatch.setattr(
        config,
        "CHARTS_DIR",
        tmp_path
    )

    fig = plt.figure()

    helper.save_plot(fig, "test_plot.png")

    assert (tmp_path / "test_plot.png").exists()
    assert (tmp_path / "test_plot.png").stat().st_size > 0

    # save_plot closes the figure
    assert not plt.fignum_exists(fig.number)


# CORRELATION HEATMAP
def test_plot_correlation_heatmap_no_data(monkeypatch):
    monkeypatch.setattr(
        engine,
        "get_returns_for_tickers",
        lambda *args: pd.DataFrame()
    )

    engine.plot_correlation_heatmap(
        ["AAPL"],
        "2026-01-01",
        "2026-01-03",
        "1Y"
    )


def test_plot_correlation_heatmap_requires_two_tickers(monkeypatch):
    returns = pd.DataFrame(
        {
            "AAPL": [0.01, 0.02, -0.01]
        },
        index=pd.date_range("2026-01-01", periods=3)
    )

    monkeypatch.setattr(
        engine,
        "get_returns_for_tickers",
        lambda *args: returns
    )

    engine.plot_correlation_heatmap(
        ["AAPL"],
        "2026-01-01",
        "2026-01-03",
        "1Y"
    )


def test_plot_correlation_heatmap(monkeypatch):
    returns = pd.DataFrame(
        {
            "AAPL": [0.01, 0.02, -0.01],
            "SPY": [0.02, 0.01, -0.02],
        },
        index=pd.date_range("2026-01-01", periods=3)
    )

    monkeypatch.setattr(
        engine,
        "get_returns_for_tickers",
        lambda *args: returns
    )

    saved = {}

    def fake_save_plotly_chart(fig, filename):
        saved["filename"] = filename
        saved["figure"] = fig

    monkeypatch.setattr(
        helper,
        "save_plotly_chart",
        fake_save_plotly_chart
    )

    engine.plot_correlation_heatmap(
        ["AAPL", "SPY"],
        "2026-01-01",
        "2026-01-03",
        "1Y"
    )

    assert saved["filename"] == "correlation/correlation_AAPL-SPY_1Y.html"
    assert saved["figure"] is not None


# ROLLING VOLATILITY
def test_plot_volatility_trends_invalid_window():
    with pytest.raises(ValueError, match="Volatility window must be at least 2"):
        engine.plot_volatility_trends(
            ["AAPL"],
            "2026-01-01",
            "2026-01-03",
            "1Y",
            window=1
        )


def test_plot_volatility_trends_no_data(monkeypatch):
    monkeypatch.setattr(
        engine,
        "get_returns_for_tickers",
        lambda *args: pd.DataFrame()
    )

    engine.plot_volatility_trends(
        ["AAPL"],
        "2026-01-01",
        "2026-01-03",
        "1Y"
    )


def test_plot_volatility_trends_not_enough_data(monkeypatch):
    returns = pd.DataFrame(
        {
            "AAPL": [0.01, 0.02]
        },
        index=pd.date_range("2026-01-01", periods=2)
    )

    monkeypatch.setattr(
        engine,
        "get_returns_for_tickers",
        lambda *args: returns
    )

    engine.plot_volatility_trends(
        ["AAPL"],
        "2026-01-01",
        "2026-01-03",
        "1Y",
        window=30
    )


def test_plot_volatility_trends(monkeypatch):
    returns = pd.DataFrame(
        {
            "AAPL": np.linspace(0.001, 0.01, 40),
            "SPY": np.linspace(0.002, 0.008, 40),
        },
        index=pd.date_range("2026-01-01", periods=40)
    )

    monkeypatch.setattr(
        engine,
        "get_returns_for_tickers",
        lambda *args: returns
    )

    saved = {}

    def fake_save_plotly_chart(fig, filename):
        saved["filename"] = filename
        saved["figure"] = fig

    monkeypatch.setattr(
        helper,
        "save_plotly_chart",
        fake_save_plotly_chart
    )

    engine.plot_volatility_trends(
        ["AAPL", "SPY"],
        "2026-01-01",
        "2026-03-01",
        "1Y",
        window=5
    )

    assert saved["filename"] == "rolling_volatility/rolling_volatility_5d_AAPL-SPY_1Y.html"
    assert saved["figure"] is not None


# ASSET CLASS VOLATILITY
def test_plot_asset_class_volatility_no_data(monkeypatch):
    monkeypatch.setattr(
        engine,
        "get_returns_for_tickers",
        lambda *args: pd.DataFrame()
    )

    engine.plot_asset_class_volatility(
        ["AAPL"],
        "2026-01-01",
        "2026-01-03",
        "1Y"
    )


def test_plot_asset_class_volatility(monkeypatch):
    returns = pd.DataFrame(
        {
            "AAPL": [0.01, 0.02, -0.01, 0.015],
            "BND": [0.002, 0.003, -0.001, 0.002],
            "SPY": [0.008, 0.012, -0.006, 0.01],
        },
        index=pd.date_range("2026-01-01", periods=4)
    )

    monkeypatch.setattr(
        engine,
        "get_returns_for_tickers",
        lambda *args: returns
    )

    monkeypatch.setattr(
        helper,
        "get_asset_class",
        lambda ticker: {
            "AAPL": "Stocks",
            "BND": "Bonds",
            "SPY": "Etfs",
        }[ticker]
    )

    saved = {}

    def fake_save_plotly_chart(fig, filename):
        saved["filename"] = filename
        saved["figure"] = fig

    monkeypatch.setattr(
        helper,
        "save_plotly_chart",
        fake_save_plotly_chart
    )

    engine.plot_asset_class_volatility(
        ["AAPL", "BND", "SPY"],
        "2026-01-01",
        "2026-01-03",
        "1Y"
    )

    assert saved["filename"] == (
        "asset_class_volatility/asset_class_volatility_AAPL-BND-SPY_1Y.html"
    )
    assert saved["figure"] is not None
