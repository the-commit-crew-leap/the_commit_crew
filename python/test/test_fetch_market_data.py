import pandas as pd
import pytest
from pathlib import Path

import sys

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "src"))

import fetch_market_data as mod


def test_fetch_price_history_single_ticker(monkeypatch):
    """Returns normalized columns for a single ticker response."""
    raw = pd.DataFrame(
        {
            "Open": [10.0],
            "High": [11.0],
            "Low": [9.5],
            "Close": [10.5],
            "Volume": [1000],
        },
        index=pd.Index(pd.to_datetime(["2024-01-02"]), name="Date"),
    )

    monkeypatch.setattr(mod.yf, "download", lambda **_: raw)

    prices = mod.fetch_price_history(["AAPL"])

    assert set(mod.REQUIRED_PRICE_COLUMNS).issubset(prices.columns)
    assert prices.loc[0, "ticker"] == "AAPL"


def test_fetch_price_history_groups_rows_by_ticker_then_date(monkeypatch):
    """Returns price history ordered by ticker and date."""
    columns = pd.MultiIndex.from_tuples(
        [
            ("Open", "MSFT"),
            ("High", "MSFT"),
            ("Low", "MSFT"),
            ("Close", "MSFT"),
            ("Volume", "MSFT"),
            ("Open", "AAPL"),
            ("High", "AAPL"),
            ("Low", "AAPL"),
            ("Close", "AAPL"),
            ("Volume", "AAPL"),
        ]
    )
    raw = pd.DataFrame(
        [
            [20.0, 21.0, 19.0, 20.5, 2000, 10.0, 11.0, 9.0, 10.5, 1000],
            [22.0, 23.0, 21.0, 22.5, 2200, 12.0, 13.0, 11.0, 12.5, 1200],
        ],
        columns=columns,
        index=pd.Index(pd.to_datetime(["2024-01-03", "2024-01-02"]), name="Date"),
    )

    monkeypatch.setattr(mod.yf, "download", lambda **_: raw)

    prices = mod.fetch_price_history(["AAPL", "MSFT"])

    assert prices[["ticker", "date"]].to_dict("records") == [
        {"ticker": "AAPL", "date": pd.Timestamp("2024-01-02")},
        {"ticker": "AAPL", "date": pd.Timestamp("2024-01-03")},
        {"ticker": "MSFT", "date": pd.Timestamp("2024-01-02")},
        {"ticker": "MSFT", "date": pd.Timestamp("2024-01-03")},
    ]


def test_fetch_price_history_raises_when_ticker_is_missing(monkeypatch):
    """Raises when the download result omits a requested ticker."""
    columns = pd.MultiIndex.from_tuples(
        [("Open", "AAPL"), ("High", "AAPL"), ("Low", "AAPL"), ("Close", "AAPL"), ("Volume", "AAPL")]
    )
    raw = pd.DataFrame(
        [[10.0, 11.0, 9.5, 10.5, 1000]],
        columns=columns,
        index=pd.Index(pd.to_datetime(["2024-01-02"]), name="Date"),
    )

    monkeypatch.setattr(mod.yf, "download", lambda **_: raw)

    with pytest.raises(ValueError, match="Price history is missing tickers"):
        mod.fetch_price_history(["AAPL", "MSFT"])


def test_fetch_ticker_metadata_returns_expected_columns(monkeypatch):
    """Includes all required metadata fields with asset class mapping."""
    class FakeTicker:
        def __init__(self, symbol):
            self.symbol = symbol

        def get_info(self):
            return {
                "longName": f"{self.symbol} Name",
                "sector": "Technology",
                "currency": "USD",
            }

    monkeypatch.setattr(mod.yf, "Ticker", FakeTicker)

    metadata = mod.fetch_ticker_metadata(["AAPL", "TLT"])

    assert list(metadata.columns) == ["ticker", "asset_class", "long_name", "sector", "currency", "tradable"]
    assert metadata.loc[metadata["ticker"] == "TLT", "asset_class"].item() == "bond"
    assert metadata["tradable"].tolist() == [True, True]


def test_fetch_ticker_metadata_raises_when_lookup_fails(monkeypatch):
    """Raises when upstream metadata lookup fails."""
    class FailingTicker:
        def __init__(self, symbol):
            self.symbol = symbol

        def get_info(self):
            raise RuntimeError("upstream failure")

    monkeypatch.setattr(mod.yf, "Ticker", FailingTicker)

    with pytest.raises(ValueError, match="Metadata lookup failed for tickers"):
        mod.fetch_ticker_metadata(["AAPL"])


def test_fetch_ticker_metadata_raises_when_required_fields_are_missing(monkeypatch):
    """Raises when required metadata values are absent."""
    class IncompleteTicker:
        def __init__(self, symbol):
            self.symbol = symbol

        def get_info(self):
            return {"sector": "Technology"}

    monkeypatch.setattr(mod.yf, "Ticker", IncompleteTicker)

    with pytest.raises(ValueError, match="Metadata is missing required values"):
        mod.fetch_ticker_metadata(["AAPL"])


def test_save_price_history_writes_grouped_csv(tmp_path, monkeypatch):
    """Writes price history grouped by ticker and date."""
    monkeypatch.setattr(mod, "DATA_DIR", tmp_path)

    prices = pd.DataFrame(
        [
            {
                "date": pd.Timestamp("2024-01-03"),
                "ticker": "MSFT",
                "open": 20.0,
                "high": 21.0,
                "low": 19.5,
                "close": 20.5,
                "volume": 2000,
            },
            {
                "date": pd.Timestamp("2024-01-02"),
                "ticker": "AAPL",
                "open": 10.0,
                "high": 11.0,
                "low": 9.5,
                "close": 10.5,
                "volume": 1000,
            },
        ]
    )

    mod.save_price_history(prices)

    written = pd.read_csv(tmp_path / "price_history.csv")
    assert written[["ticker", "date"]].to_dict("records") == [
        {"ticker": "AAPL", "date": "2024-01-02"},
        {"ticker": "MSFT", "date": "2024-01-03"},
    ]


def test_save_ticker_metadata_writes_csv_without_touching_prices(tmp_path, monkeypatch):
    """Writes metadata independently from price history."""
    monkeypatch.setattr(mod, "DATA_DIR", tmp_path)

    metadata = pd.DataFrame(
        [
            {
                "ticker": "MSFT",
                "asset_class": "equity",
                "long_name": "Microsoft Corp.",
                "sector": "Technology",
                "currency": "USD",
                "tradable": True,
            },
            {
                "ticker": "AAPL",
                "asset_class": "equity",
                "long_name": "Apple Inc.",
                "sector": "Technology",
                "currency": "USD",
                "tradable": True,
            },
        ]
    )

    mod.save_ticker_metadata(metadata)

    written = pd.read_csv(tmp_path / "ticker_metadata.csv")
    assert written["ticker"].tolist() == ["AAPL", "MSFT"]
    assert not (tmp_path / "price_history.csv").exists()