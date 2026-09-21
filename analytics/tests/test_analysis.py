import pandas as pd
import numpy as np
import pytest

from src import analysis


# ============================================================
# FIXTURES
# ============================================================
@pytest.fixture
def price_dataframe():
    return pd.DataFrame({
        "symbol": [
            "AAPL",
            "AAPL",
            "AAPL",
            "SPY",
            "SPY",
            "SPY",
        ],
        "date": pd.to_datetime([
            "2026-01-01",
            "2026-01-02",
            "2026-01-03",
            "2026-01-01",
            "2026-01-02",
            "2026-01-03",
        ]),
        "close": [
            100,
            102,
            101,
            200,
            204,
            208,
        ],
        "adj_close": [
            100,
            102,
            101,
            200,
            204,
            208,
        ],
        "volume": [
            1000,
            1200,
            1100,
            5000,
            5100,
            5200,
        ],
    })


@pytest.fixture
def csv_file(tmp_path, price_dataframe):
    path = tmp_path / "AAPL.csv"

    price_dataframe[price_dataframe["symbol"] == "AAPL"].to_csv(path, index=False)

    return path


# ============================================================
# LOAD PRICE DATA
# ============================================================
def test_load_price_data_missing_file(monkeypatch):
    monkeypatch.setattr(
        analysis.config,
        "get_ticker_csv_path",
        lambda _: analysis.config.CHARTS_DIR / "missing.csv"
    )

    result = analysis.load_price_data("AAPL")

    assert isinstance(result, pd.DataFrame)
    assert result.empty


def test_load_price_data_empty_file(monkeypatch, tmp_path):
    path = tmp_path / "AAPL.csv"

    pd.DataFrame(columns=["symbol", "date", "close", "adj_close", "volume"]).to_csv(path, index=False)

    monkeypatch.setattr(
        analysis.config,
        "get_ticker_csv_path",
        lambda _: path
    )

    result = analysis.load_price_data("AAPL")

    assert isinstance(result, pd.DataFrame)
    assert result.empty


def test_load_price_data_removes_duplicate_dates(monkeypatch, tmp_path, price_dataframe):
    path = tmp_path / "AAPL.csv"

    df = pd.concat([price_dataframe.iloc[:3], price_dataframe.iloc[[0]].assign(close=999)])
    df.to_csv(path, index=False)

    monkeypatch.setattr(
        analysis.config,
        "get_ticker_csv_path",
        lambda _: path
    )

    result = analysis.load_price_data("aapl")

    assert len(result) == 3

    row = result[result["date"] == pd.Timestamp("2026-01-01")]

    assert row.iloc[0]["close"] == 999


def test_load_price_data_filters_dates(monkeypatch, csv_file):
    monkeypatch.setattr(
        analysis.config,
        "get_ticker_csv_path",
        lambda _: csv_file
    )

    result = analysis.load_price_data(
        "AAPL",
        start_date="2026-01-02",
        end_date="2026-01-03"
    )

    assert len(result) == 2
    assert result["date"].min() == pd.Timestamp("2026-01-02")


def test_load_price_data_read_error(monkeypatch, tmp_path):
    path = tmp_path / "AAPL.csv"
    path.write_text("not a valid csv")

    monkeypatch.setattr(
        analysis.config,
        "get_ticker_csv_path",
        lambda _: path
    )

    def raise_error(*args, **kwargs):
        raise Exception("CSV read error")

    monkeypatch.setattr(
        analysis.pd,
        "read_csv",
        raise_error
    )

    result = analysis.load_price_data("AAPL")

    assert isinstance(result, pd.DataFrame)
    assert result.empty


# ============================================================
# RETURNS
# ============================================================
def test_get_returns_for_tickers(monkeypatch, csv_file):
    monkeypatch.setattr(
        analysis.config,
        "get_ticker_csv_path",
        lambda _: csv_file
    )

    result = analysis.get_returns_for_tickers(
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
        analysis.get_returns_for_tickers(
            ["AAPL"],
            "2026-01-03",
            "2026-01-01"
        )


def test_get_returns_for_tickers_missing_data(monkeypatch):
    monkeypatch.setattr(
        analysis,
        "load_price_data",
        lambda *args, **kwargs: pd.DataFrame()
    )

    result = analysis.get_returns_for_tickers(
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
        analysis,
        "load_price_data",
        lambda *args, **kwargs: price_data.copy()
    )

    result = analysis.get_returns_for_tickers(
        ["AAPL"],
        "2026-01-01",
        "2026-01-03"
    )

    assert "AAPL" in result.columns
    assert result.loc[pd.Timestamp("2026-01-02"), "AAPL"] == pytest.approx(0.02)


# ============================================================
# SUMMARY STATISTICS
# ============================================================
def test_calculate_summary_statistics(monkeypatch, csv_file):
    monkeypatch.setattr(
        analysis.config,
        "get_ticker_csv_path",
        lambda _: csv_file
    )

    result = analysis.calculate_summary_statistics(
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
        analysis,
        "get_returns_for_tickers",
        lambda *args: pd.DataFrame()
    )

    result = analysis.calculate_summary_statistics(
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
        analysis,
        "get_returns_for_tickers",
        lambda *args: returns
    )

    result = analysis.calculate_summary_statistics(
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
        analysis,
        "plot_correlation_heatmap",
        lambda *args: None
    )

    monkeypatch.setattr(
        analysis,
        "plot_volatility_trends",
        lambda *args, **kwargs: None
    )

    monkeypatch.setattr(
        analysis,
        "plot_asset_class_volatility",
        lambda *args: None
    )

    monkeypatch.setattr(
        analysis,
        "calculate_summary_statistics",
        lambda *args: fake_summary
    )

    result = analysis.compute_insights(
        tickers=["AAPL"]
    )

    assert isinstance(result, pd.DataFrame)
    assert result.iloc[0]["Ticker"] == "AAPL"


def test_compute_insights_invalid_volatility_window():
    with pytest.raises(ValueError):

        analysis.compute_insights(
            tickers=["AAPL"],
            volatility_window=1
        )


def test_compute_insights_no_tickers(monkeypatch):
    monkeypatch.setattr(
        analysis,
        "get_tickers_for_analysis",
        lambda *args, **kwargs: []
    )

    result = analysis.compute_insights()

    assert isinstance(result, pd.DataFrame)
    assert result.empty


def test_compute_insights_empty_summary(monkeypatch):
    monkeypatch.setattr(
        analysis,
        "get_tickers_for_analysis",
        lambda *args, **kwargs: ["AAPL"]
    )

    monkeypatch.setattr(
        analysis,
        "get_date_range",
        lambda *args, **kwargs: ("2026-01-01", "2026-01-03")
    )

    monkeypatch.setattr(
        analysis,
        "plot_correlation_heatmap",
        lambda *args, **kwargs: None
    )

    monkeypatch.setattr(
        analysis,
        "plot_volatility_trends",
        lambda *args, **kwargs: None
    )

    monkeypatch.setattr(
        analysis,
        "plot_asset_class_volatility",
        lambda *args, **kwargs: None
    )

    monkeypatch.setattr(
        analysis,
        "calculate_summary_statistics",
        lambda *args, **kwargs: pd.DataFrame()
    )

    result = analysis.compute_insights(tickers=["AAPL"])

    assert isinstance(result, pd.DataFrame)
    assert result.empty
