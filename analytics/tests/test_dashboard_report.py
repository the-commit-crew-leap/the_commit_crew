import pandas as pd
import pytest

from src import dashboard_report


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
def partial_stats():
    return pd.DataFrame({
        "Ticker": ["AAPL"],
        "Asset Class": ["Stocks"],
        "Annualized Return (%)": [12.50],
    })


# ============================================================
# FORMAT DASHBOARD
# ============================================================
def test_format_dashboard_empty_stats():
    result = dashboard_report.format_dashboard(
        stats=pd.DataFrame(),
        tickers=["AAPL", "SPY"],
        period="1y"
    )

    assert isinstance(result, str)

    assert "PORTFOLIO ANALYSIS DASHBOARD" in result
    assert "Period: 1Y" in result
    assert "Tickers: AAPL, SPY" in result
    assert "No analysis data available." in result


def test_format_dashboard_none_stats():
    result = dashboard_report.format_dashboard(
        stats=None,
        tickers=["AAPL"],
        period="5y"
    )

    assert "PORTFOLIO ANALYSIS DASHBOARD" in result
    assert "Period: 5Y" in result
    assert "Tickers: AAPL" in result
    assert "No analysis data available." in result


def test_format_dashboard_full_stats(full_stats):
    result = dashboard_report.format_dashboard(
        stats=full_stats,
        tickers=["AAPL", "BND", "SPY"],
        period="1y"
    )

    # Header
    assert "PORTFOLIO ANALYSIS DASHBOARD" in result
    assert "Period: 1Y" in result
    assert "Tickers: AAPL, BND, SPY" in result

    # Summary
    assert "SUMMARY" in result

    # Individual securities
    assert "AAPL (Stocks)" in result
    assert "BND (Bonds)" in result
    assert "SPY (Etfs)" in result

    # Statistics
    assert "Annualized Return: 12.50%" in result
    assert "Annualized Volatility: 20.00%" in result
    assert "Sharpe Ratio: 0.62" in result

    # Key insights
    assert "KEY INSIGHTS" in result
    assert "Best historical return: AAPL (12.50%)" in result
    assert "Lowest historical return: BND (4.25%)" in result
    assert "Highest historical volatility: AAPL (20.00%)" in result
    assert "Lowest historical volatility: BND (5.00%)" in result
    assert "Best historical Sharpe ratio: BND (0.85)" in result


def test_format_dashboard_formats_values_to_two_decimal_places():
    stats = pd.DataFrame({
        "Ticker": ["AAPL"],
        "Asset Class": ["Stocks"],
        "Annualized Return (%)": [12.3456],
        "Annualized Volatility (%)": [20.9876],
        "Sharpe Ratio": [0.62345],
    })

    result = dashboard_report.format_dashboard(
        stats=stats,
        tickers=["AAPL"],
        period="1y"
    )

    assert "Annualized Return: 12.35%" in result
    assert "Annualized Volatility: 20.99%" in result
    assert "Sharpe Ratio: 0.62" in result


def test_format_dashboard_missing_optional_values():
    stats = pd.DataFrame({
        "Ticker": ["AAPL"],
        "Asset Class": ["Stocks"],
        "Annualized Return (%)": [12.50],
        "Annualized Volatility (%)": [float("nan")],
        "Sharpe Ratio": [float("nan")],
    })

    result = dashboard_report.format_dashboard(
        stats=stats,
        tickers=["AAPL"],
        period="1y"
    )

    assert "AAPL (Stocks)" in result
    assert "Annualized Return: 12.50%" in result
    assert "Annualized Volatility:" not in result
    assert "Sharpe Ratio:" not in result


def test_format_dashboard_missing_asset_class():
    stats = pd.DataFrame({
        "Ticker": ["AAPL"],
        "Annualized Return (%)": [12.50],
        "Annualized Volatility (%)": [20.00],
        "Sharpe Ratio": [0.62],
    })

    result = dashboard_report.format_dashboard(
        stats=stats,
        tickers=["AAPL"],
        period="1y"
    )

    assert "AAPL (Unknown)" in result


def test_format_dashboard_without_return_column():
    stats = pd.DataFrame({
        "Ticker": ["AAPL"],
        "Asset Class": ["Stocks"],
        "Annualized Volatility (%)": [20.00],
        "Sharpe Ratio": [0.62],
    })

    result = dashboard_report.format_dashboard(
        stats=stats,
        tickers=["AAPL"],
        period="1y"
    )

    assert "AAPL (Stocks)" in result
    assert "Annualized Volatility: 20.00%" in result
    assert "Sharpe Ratio: 0.62" in result

    assert "Best historical return:" not in result
    assert "Lowest historical return:" not in result


def test_format_dashboard_without_volatility_column():
    stats = pd.DataFrame({
        "Ticker": ["AAPL"],
        "Asset Class": ["Stocks"],
        "Annualized Return (%)": [12.50],
        "Sharpe Ratio": [0.62],
    })

    result = dashboard_report.format_dashboard(
        stats=stats,
        tickers=["AAPL"],
        period="1y"
    )

    assert "Best historical return: AAPL (12.50%)" in result
    assert "Lowest historical return: AAPL (12.50%)" in result

    assert "Highest historical volatility:" not in result
    assert "Lowest historical volatility:" not in result


def test_format_dashboard_without_sharpe_column():
    stats = pd.DataFrame({
        "Ticker": ["AAPL"],
        "Asset Class": ["Stocks"],
        "Annualized Return (%)": [12.50],
        "Annualized Volatility (%)": [20.00],
    })

    result = dashboard_report.format_dashboard(
        stats=stats,
        tickers=["AAPL"],
        period="1y"
    )

    assert "Best historical return: AAPL (12.50%)" in result
    assert "Highest historical volatility: AAPL (20.00%)" in result

    assert "Best historical Sharpe ratio:" not in result


# ============================================================
# RUN DASHBOARD
# ============================================================
def test_run_dashboard_no_tickers(monkeypatch):
    monkeypatch.setattr(
        dashboard_report,
        "get_tickers_for_analysis",
        lambda *args, **kwargs: []
    )

    result = dashboard_report.run_dashboard()

    assert result == ""


def test_run_dashboard_without_saving(monkeypatch, capsys, full_stats):
    monkeypatch.setattr(
        dashboard_report,
        "get_tickers_for_analysis",
        lambda *args, **kwargs: ["AAPL", "SPY"]
    )

    monkeypatch.setattr(
        dashboard_report,
        "compute_insights",
        lambda *args, **kwargs: full_stats
    )

    result = dashboard_report.run_dashboard(
        tickers=["AAPL", "SPY"],
        period="1y",
        save_file=False
    )

    captured = capsys.readouterr()

    assert isinstance(result, str)

    assert "PORTFOLIO ANALYSIS DASHBOARD" in result
    assert "AAPL" in result

    # Verify that the dashboard was printed.
    assert "PORTFOLIO ANALYSIS DASHBOARD" in captured.out


def test_run_dashboard_saves_file(monkeypatch, tmp_path, full_stats):
    monkeypatch.setattr(
        dashboard_report,
        "get_tickers_for_analysis",
        lambda *args, **kwargs: ["AAPL", "SPY"]
    )

    monkeypatch.setattr(
        dashboard_report,
        "compute_insights",
        lambda *args, **kwargs: full_stats
    )

    monkeypatch.setattr(
        dashboard_report.config,
        "REPORTS_DIR",
        tmp_path
    )

    result = dashboard_report.run_dashboard(
        tickers=["AAPL", "SPY"],
        period="1y",
        save_file=True
    )

    expected_file = tmp_path / "dashboard_AAPL-SPY_1Y.txt"

    assert expected_file.exists()

    saved_content = expected_file.read_text(
        encoding="utf-8"
    )

    assert saved_content == result
    assert "PORTFOLIO ANALYSIS DASHBOARD" in saved_content


def test_run_dashboard_passes_arguments_to_compute_insights(monkeypatch, full_stats):
    monkeypatch.setattr(
        dashboard_report,
        "get_tickers_for_analysis",
        lambda *args, **kwargs: ["AAPL"]
    )

    captured = {}

    def fake_compute_insights(tickers, period, volatility_window):
        captured["tickers"] = tickers
        captured["period"] = period
        captured["volatility_window"] = volatility_window

        return full_stats

    monkeypatch.setattr(
        dashboard_report,
        "compute_insights",
        fake_compute_insights
    )

    dashboard_report.run_dashboard(
        tickers=["AAPL"],
        period="5y",
        volatility_window=60,
        save_file=False
    )

    assert captured["tickers"] == ["AAPL"]
    assert captured["period"] == "5y"
    assert captured["volatility_window"] == 60
