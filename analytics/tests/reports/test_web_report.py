import json

import pandas as pd
import pytest

from src import web_report


# ============================================================
# FIXTURES
# ============================================================
@pytest.fixture
def full_stats():
    return pd.DataFrame({
        "Ticker": ["AAPL", "BND", "SPY"],
        "Asset Class": ["Stocks", "Bonds", "Etfs"],
        "Annualized Return (%)": [12.50, 4.25, 9.75],
        "Annualized Volatility (%)": [20.00, 5.00, 15.00],
        "Sharpe Ratio": [0.62, 0.85, 0.65],
    })

@pytest.fixture
def empty_stats():
    return pd.DataFrame()


# ============================================================
# GENERATE WEB INSIGHTS
# ============================================================
def test_generate_web_insights_empty_dataframe(empty_stats):
    result = web_report.generate_web_insights(empty_stats)

    assert result == {
        "best_return": None,
        "worst_return": None,
        "highest_volatility": None,
        "lowest_volatility": None,
        "best_sharpe": None,
        "risk_ranking": [],
    }


def test_generate_web_insights_none():
    result = web_report.generate_web_insights(None)

    assert result["best_return"] is None
    assert result["worst_return"] is None
    assert result["highest_volatility"] is None
    assert result["lowest_volatility"] is None
    assert result["best_sharpe"] is None
    assert result["risk_ranking"] == []


def test_generate_web_insights_full_stats(full_stats):
    result = web_report.generate_web_insights(full_stats)

    assert result["best_return"] == {"ticker": "AAPL", "value": 12.50,}
    assert result["worst_return"] == {"ticker": "BND", "value": 4.25,}
    assert result["highest_volatility"] == {"ticker": "AAPL", "value": 20.00,}
    assert result["lowest_volatility"] == {"ticker": "BND", "value": 5.00,}
    assert result["best_sharpe"] == {"ticker": "BND", "value": 0.85,}


def test_generate_web_insights_risk_ranking(full_stats):
    result = web_report.generate_web_insights(full_stats)

    assert result["risk_ranking"] == [
        {
            "rank": 1,
            "ticker": "AAPL",
            "volatility": 20.00,
        },
        {
            "rank": 2,
            "ticker": "SPY",
            "volatility": 15.00,
        },
        {
            "rank": 3,
            "ticker": "BND",
            "volatility": 5.00,
        },
    ]


def test_generate_web_insights_converts_numeric_strings():
    stats = pd.DataFrame({
        "Ticker": ["AAPL", "BND"],
        "Annualized Return (%)": ["12.5", "4.25"],
        "Annualized Volatility (%)": ["20.0", "5.0"],
        "Sharpe Ratio": ["0.62", "0.85"],
    })

    result = web_report.generate_web_insights(stats)

    assert result["best_return"]["value"] == 12.5
    assert result["worst_return"]["value"] == 4.25

    assert result["highest_volatility"]["value"] == 20.0
    assert result["lowest_volatility"]["value"] == 5.0

    assert result["best_sharpe"]["value"] == 0.85


def test_generate_web_insights_ignores_invalid_numeric_values():
    stats = pd.DataFrame({
        "Ticker": ["AAPL", "BND", "SPY"],
        "Annualized Return (%)": ["12.5", "invalid", "9.75"],
        "Annualized Volatility (%)": ["20.0", "invalid", "15.0"],
        "Sharpe Ratio": ["0.62", "invalid", "0.65"],
    })

    result = web_report.generate_web_insights(stats)

    assert result["best_return"]["ticker"] == "AAPL"
    assert result["worst_return"]["ticker"] == "SPY"

    assert result["highest_volatility"]["ticker"] == "AAPL"
    assert result["lowest_volatility"]["ticker"] == "SPY"

    assert result["best_sharpe"]["ticker"] == "SPY"


def test_generate_web_insights_missing_return_column():
    stats = pd.DataFrame({
        "Ticker": ["AAPL", "SPY"],
        "Annualized Volatility (%)": [20.0, 15.0],
        "Sharpe Ratio": [0.62, 0.65],
    })

    result = web_report.generate_web_insights(stats)

    assert result["best_return"] is None
    assert result["worst_return"] is None

    assert result["highest_volatility"]["ticker"] == "AAPL"
    assert result["lowest_volatility"]["ticker"] == "SPY"

    assert result["best_sharpe"]["ticker"] == "SPY"


def test_generate_web_insights_missing_volatility_column():
    stats = pd.DataFrame({
        "Ticker": ["AAPL", "SPY"],
        "Annualized Return (%)": [12.5, 9.75],
        "Sharpe Ratio": [0.62, 0.65],
    })

    result = web_report.generate_web_insights(stats)

    assert result["best_return"]["ticker"] == "AAPL"
    assert result["worst_return"]["ticker"] == "SPY"

    assert result["highest_volatility"] is None
    assert result["lowest_volatility"] is None
    assert result["risk_ranking"] == []

    assert result["best_sharpe"]["ticker"] == "SPY"


def test_generate_web_insights_missing_sharpe_column():
    stats = pd.DataFrame({
        "Ticker": ["AAPL", "SPY"],
        "Annualized Return (%)": [12.5, 9.75],
        "Annualized Volatility (%)": [20.0, 15.0],
    })

    result = web_report.generate_web_insights(stats)

    assert result["best_return"]["ticker"] == "AAPL"
    assert result["worst_return"]["ticker"] == "SPY"

    assert result["highest_volatility"]["ticker"] == "AAPL"
    assert result["lowest_volatility"]["ticker"] == "SPY"

    assert result["best_sharpe"] is None


def test_generate_web_insights_all_nan_metric():
    stats = pd.DataFrame({
        "Ticker": ["AAPL", "SPY"],
        "Annualized Return (%)": [float("nan"), float("nan")],
        "Annualized Volatility (%)": [20.0, 15.0],
        "Sharpe Ratio": [0.62, 0.65],
    })

    result = web_report.generate_web_insights(stats)

    assert result["best_return"] is None
    assert result["worst_return"] is None

    assert result["highest_volatility"]["ticker"] == "AAPL"
    assert result["lowest_volatility"]["ticker"] == "SPY"

    assert result["best_sharpe"]["ticker"] == "SPY"


# ============================================================
# CREATE WEB DATA
# ============================================================
def test_create_web_data_full_stats(full_stats):
    result = web_report.create_web_data(
        summary_stats=full_stats,
        tickers=["AAPL", "BND", "SPY"],
        period="1y",
    )

    assert isinstance(result, dict)

    assert set(result.keys()) == {
        "metadata",
        "statistics",
        "insights",
        "charts",
    }

    assert result["metadata"]["period"] == "1y"
    assert result["metadata"]["tickers"] == ["AAPL", "BND", "SPY"]
    assert result["metadata"]["ticker_count"] == 3
    assert "generated_at" in result["metadata"]

    assert len(result["statistics"]) == 3
    assert result["statistics"][0]["Ticker"] == "AAPL"

    assert result["insights"]["best_return"]["ticker"] == "AAPL"

    assert len(result["charts"]) == 3


def test_create_web_data_empty_stats():
    result = web_report.create_web_data(
        summary_stats=pd.DataFrame(),
        tickers=["AAPL"],
        period="5y",
    )

    assert result["statistics"] == []

    assert result["insights"]["best_return"] is None
    assert result["insights"]["risk_ranking"] == []

    assert len(result["charts"]) == 3


def test_create_web_data_none_stats():
    result = web_report.create_web_data(
        summary_stats=None,
        tickers=["AAPL"],
        period="1y",
    )

    assert result["statistics"] == []
    assert result["insights"]["best_return"] is None


def test_create_web_data_chart_metadata():
    result = web_report.create_web_data(
        summary_stats=pd.DataFrame(),
        tickers=["AAPL", "SPY"],
        period="10y",
    )

    charts = result["charts"]

    assert charts[0]["type"] == "correlation"
    assert charts[0]["title"] == "Correlation Matrix"
    assert charts[0]["period"] == "10y"
    assert charts[0]["tickers"] == ["AAPL", "SPY"]

    assert charts[1]["type"] == "volatility"
    assert charts[1]["title"] == "Rolling Volatility"

    assert charts[2]["type"] == "asset_class_volatility"
    assert charts[2]["title"] == "Asset Class Volatility"


# ============================================================
# SAVE WEB DATA
# ============================================================
def test_save_web_data(monkeypatch, tmp_path):
    monkeypatch.setattr(
        web_report.config,
        "REPORTS_DIR",
        tmp_path,
    )

    web_data = {
        "metadata": {
            "period": "1y",
            "tickers": ["AAPL"],
        },
        "statistics": [],
        "insights": {},
        "charts": [],
    }

    result = web_report.save_web_data(
        web_data=web_data,
        tickers=["AAPL"],
        period="1y",
    )

    expected_path = tmp_path / "web_data_AAPL_1Y.json"

    assert result == expected_path
    assert result.exists()

    with open(result, "r", encoding="utf-8") as file:
        saved_data = json.load(file)

    assert saved_data == web_data


def test_save_web_data_creates_reports_directory(monkeypatch, tmp_path):
    reports_dir = tmp_path / "reports"

    monkeypatch.setattr(
        web_report.config,
        "REPORTS_DIR",
        reports_dir,
    )

    web_data = {"test": "value",}

    result = web_report.save_web_data(
        web_data=web_data,
        tickers=["AAPL", "SPY"],
        period="5y",
    )

    assert reports_dir.exists()
    assert result.exists()
    assert result.name == "web_data_AAPL-SPY_5Y.json"


# ============================================================
# GENERATE WEB REPORT
# ============================================================
def test_generate_web_report_success_without_saving(monkeypatch, full_stats):
    monkeypatch.setattr(
        web_report,
        "get_tickers_for_analysis",
        lambda **kwargs: ["AAPL", "SPY"],
    )

    monkeypatch.setattr(
        web_report,
        "compute_insights",
        lambda **kwargs: full_stats,
    )

    result = web_report.generate_web_report(
        tickers=["AAPL", "SPY"],
        period="1y",
        volatility_window=30,
        save_file=False,
    )

    assert result["status"] == "success"
    assert result["json_path"] is None
    assert result["web_data"] is not None

    assert result["web_data"]["metadata"]["tickers"] == ["AAPL", "SPY"]


def test_generate_web_report_no_tickers(monkeypatch):
    monkeypatch.setattr(
        web_report,
        "get_tickers_for_analysis",
        lambda **kwargs: [],
    )

    result = web_report.generate_web_report(tickers=[], save_file=False)

    assert result == {
        "status": "failed",
        "web_data": None,
        "json_path": None,
        "error": "No tickers selected",
    }


def test_generate_web_report_saves_file(monkeypatch, tmp_path, full_stats):
    monkeypatch.setattr(
        web_report,
        "get_tickers_for_analysis",
        lambda **kwargs: ["AAPL", "SPY"],
    )

    monkeypatch.setattr(
        web_report,
        "compute_insights",
        lambda **kwargs: full_stats,
    )

    monkeypatch.setattr(
        web_report.config,
        "REPORTS_DIR",
        tmp_path,
    )

    result = web_report.generate_web_report(
        tickers=["AAPL", "SPY"],
        period="1y",
        save_file=True,
    )

    assert result["status"] == "success"
    assert result["json_path"] is not None
    assert result["json_path"].exists()

    assert result["json_path"].name == ("web_data_AAPL-SPY_1Y.json")


def test_generate_web_report_passes_arguments(monkeypatch, full_stats):
    captured = {}

    def fake_get_tickers_for_analysis(tickers, asset_classes):
        captured["tickers"] = tickers
        captured["asset_classes"] = asset_classes

        return ["AAPL"]

    def fake_compute_insights(tickers, period, volatility_window):
        captured["analysis_tickers"] = tickers
        captured["period"] = period
        captured["volatility_window"] = volatility_window

        return full_stats

    monkeypatch.setattr(
        web_report,
        "get_tickers_for_analysis",
        fake_get_tickers_for_analysis,
    )

    monkeypatch.setattr(
        web_report,
        "compute_insights",
        fake_compute_insights,
    )

    web_report.generate_web_report(
        tickers=["AAPL"],
        asset_classes=["stocks"],
        period="5y",
        volatility_window=60,
        save_file=False,
    )

    assert captured["tickers"] == ["AAPL"]
    assert captured["asset_classes"] == ["stocks"]

    assert captured["analysis_tickers"] == ["AAPL"]
    assert captured["period"] == "5y"
    assert captured["volatility_window"] == 60


def test_generate_web_report_handles_exception(monkeypatch):
    def raise_error(**kwargs):
        raise RuntimeError("analysis failed")

    monkeypatch.setattr(
        web_report,
        "get_tickers_for_analysis",
        raise_error,
    )

    result = web_report.generate_web_report(
        tickers=["AAPL"],
        save_file=False,
    )

    assert result["status"] == "failed"
    assert result["web_data"] is None
    assert result["json_path"] is None
    assert result["error"] == "analysis failed"
