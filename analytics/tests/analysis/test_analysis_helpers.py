import pytest
from datetime import datetime

from config import config
from src.analysis.analysis_helper import AnalysisHelper


helper = AnalysisHelper()


# NORMALIZE TICKER
def test_normalize_ticker_lowercase():
    assert helper.normalize_ticker("aapl") == "AAPL"


def test_normalize_ticker_strips_whitespace():
    assert helper.normalize_ticker("  AAPL  ") == "AAPL"


def test_normalize_ticker_handles_non_string():
    assert helper.normalize_ticker(123) == "123"


# TICKER LABEL / TITLE
def test_get_ticker_label():
    result = helper.get_ticker_label(["aapl", "brk/b", "  spy  "])

    assert result == "AAPL-BRK-B-SPY"


def test_get_ticker_label_replaces_spaces():
    result = helper.get_ticker_label(["AAPL US", "SPY ETF"])

    assert result == "AAPL-US-SPY-ETF"


def test_get_ticker_title():
    result = helper.get_ticker_title(["aapl", " spy ", "bnd"])

    assert result == "AAPL, SPY, BND"


# TICKER SELECTION
def test_get_tickers_for_analysis_explicit_tickers():
    result = helper.get_tickers_for_analysis(tickers=["aapl", "SPY", "aapl", "  BND  "])

    assert result == ["AAPL", "SPY", "BND"]


def test_get_tickers_for_analysis_ignores_none_and_empty_values():
    result = helper.get_tickers_for_analysis(tickers=["AAPL", None, "", "   ", "SPY"])

    assert result == ["AAPL", "SPY"]


def test_get_tickers_for_analysis_asset_classes(monkeypatch):
    monkeypatch.setattr(
        config,
        "INSTRUMENTS", {
            "stocks": ["AAPL", "MSFT"],
            "bonds": ["BND"]
        }
    )

    result = helper.get_tickers_for_analysis(asset_classes=["stocks"])

    assert result == ["AAPL", "MSFT"]


def test_get_tickers_for_analysis_multiple_asset_classes(monkeypatch):
    monkeypatch.setattr(
        config,
        "INSTRUMENTS",
        {
            "stocks": ["AAPL", "MSFT"],
            "etfs": ["SPY", "AAPL"]
        }
    )

    result = helper.get_tickers_for_analysis(asset_classes=["stocks", "etfs"])

    assert result == ["AAPL", "MSFT", "SPY"]


def test_get_tickers_for_analysis_asset_classes_are_case_insensitive(monkeypatch):
    monkeypatch.setattr(
        config,
        "INSTRUMENTS",
        {
            "stocks": ["AAPL", "MSFT"],
        }
    )

    result = helper.get_tickers_for_analysis(asset_classes=["  STOCKS  "])

    assert result == ["AAPL", "MSFT"]


def test_get_tickers_for_analysis_unknown_asset_class(monkeypatch, caplog):
    monkeypatch.setattr(
        config,
        "INSTRUMENTS",
        {
            "stocks": ["AAPL"],
        }
    )

    result = helper.get_tickers_for_analysis(asset_classes=["crypto"])

    assert result == []
    assert "Unknown asset class" in caplog.text


def test_get_tickers_for_analysis_unknown_and_valid_asset_classes(monkeypatch, caplog):
    monkeypatch.setattr(
        config,
        "INSTRUMENTS",
        {
            "stocks": ["AAPL"],
            "bonds": ["BND"],
        }
    )

    result = helper.get_tickers_for_analysis(asset_classes=["crypto", "stocks", "unknown"])

    assert result == ["AAPL"]
    assert "Unknown asset class" in caplog.text


def test_get_tickers_for_analysis_defaults(monkeypatch):
    monkeypatch.setattr(
        config,
        "INSTRUMENTS_LIST",
        ["aapl", "SPY", "aapl", " BND "]
    )

    result = helper.get_tickers_for_analysis()

    assert result == ["AAPL", "SPY", "BND"]


def test_get_tickers_for_analysis_empty_default_list(monkeypatch):
    monkeypatch.setattr(
        config,
        "INSTRUMENTS_LIST",
        []
    )

    result = helper.get_tickers_for_analysis()

    assert result == []


def test_get_tickers_for_analysis_explicit_tickers_take_priority(
    monkeypatch
):
    monkeypatch.setattr(
        config,
        "INSTRUMENTS",
        {
            "stocks": ["AAPL", "MSFT"],
        }
    )

    result = helper.get_tickers_for_analysis(tickers=["SPY"], asset_classes=["stocks"])
    
    assert result == ["SPY"]


# DATE RANGE
@pytest.mark.parametrize(
    "period",
    ["1y", "5y", "10y", "all"]
)
def test_get_date_range_valid_periods(period):
    start_date, end_date = helper.get_date_range(period)

    assert isinstance(start_date, str)
    assert isinstance(end_date, str)

    # YYYY-MM-DD format
    assert len(start_date) == 10
    assert len(end_date) == 10
    
    assert start_date <= end_date


def test_get_date_range_1y():
    start_date, end_date = helper.get_date_range("1y")

    start = datetime.strptime(start_date, "%Y-%m-%d")
    end = datetime.strptime(end_date, "%Y-%m-%d")

    assert (end - start).days == 365


def test_get_date_range_5y():
    start_date, end_date = helper.get_date_range("5y")

    start = datetime.strptime(start_date, "%Y-%m-%d")
    end = datetime.strptime(end_date, "%Y-%m-%d")

    assert (end - start).days == 365 * 5


def test_get_date_range_10y():
    start_date, end_date = helper.get_date_range("10y")

    start = datetime.strptime(start_date, "%Y-%m-%d")
    end = datetime.strptime(end_date, "%Y-%m-%d")

    assert (end - start).days == 365 * 10


def test_get_date_range_all():
    start_date, end_date = helper.get_date_range("all")

    assert start_date == "1900-01-01"
    assert start_date < end_date


def test_get_date_range_invalid_period():
    with pytest.raises(ValueError, match="Unknown period"):
        helper.get_date_range("2y")


# ASSET CLASS
def test_get_asset_class_stocks(monkeypatch):
    monkeypatch.setattr(
        config,
        "INSTRUMENTS",
        {
            "stocks": ["AAPL", "MSFT"],
            "bonds": ["BND"],
        }
    )

    assert helper.get_asset_class("AAPL") == "Stocks"


def test_get_asset_class_bonds(monkeypatch):
    monkeypatch.setattr(
        config,
        "INSTRUMENTS",
        {
            "stocks": ["AAPL"],
            "bonds": ["BND"],
        }
    )

    assert helper.get_asset_class("BND") == "Bonds"


def test_get_asset_class_is_case_insensitive(monkeypatch):
    monkeypatch.setattr(
        config,
        "INSTRUMENTS",
        {
            "stocks": ["AAPL"],
        }
    )

    assert helper.get_asset_class(" aapl ") == "Stocks"


def test_get_asset_class_unknown_ticker(monkeypatch):
    monkeypatch.setattr(
        config,
        "INSTRUMENTS",
        {
            "stocks": ["AAPL"],
            "bonds": ["BND"],
        }
    )

    assert helper.get_asset_class("UNKNOWN") == "Unknown"
