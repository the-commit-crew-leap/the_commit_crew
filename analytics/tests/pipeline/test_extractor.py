import pandas as pd
import pytest

from src.pipeline.extractor import Extractor

extractor = Extractor()


# TEST DATA
def make_valid_dataframe() -> pd.DataFrame:
    """Create a small deterministic dataset for testing."""
    
    return pd.DataFrame({
        "symbol": ["AAPL", "AAPL", "SPY"],
        "date": ["2026-01-02", "2026-01-05", "2026-01-02"],
        "open": [100.0, 102.0, 200.0],
        "high": [105.0, 106.0, 205.0],
        "low": [99.0, 101.0, 198.0],
        "close": [103.0, 105.0, 203.0],
        "volume": [1_000_000, 1_100_000, 2_000_000],
        "adj_close": [103.0, 105.0, 203.0],
    })


# EXTRACT
def test_extract_returns_dataframe(monkeypatch):
    """Mock extraction should return a non-empty DataFrame."""
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
    
    import yfinance as yf
    monkeypatch.setattr(yf, "download", lambda **_: raw)
    
    df = extractor.extract(["AAPL"])

    assert isinstance(df, pd.DataFrame)
    assert not df.empty


def test_extract_has_expected_columns(monkeypatch):
    """Extraction must provide the columns expected by transform()."""
    raw = pd.DataFrame(
        {
            "Open": [10.0],
            "High": [11.0],
            "Low": [9.5],
            "Close": [10.5],
            "Volume": [1000],
            "Adj Close": [10.5],
        },
        index=pd.Index(pd.to_datetime(["2024-01-02"]), name="Date"),
    )
    
    import yfinance as yf
    monkeypatch.setattr(yf, "download", lambda **_: raw)
    
    df = extractor.extract(["AAPL"])

    expected = {"symbol", "date", "open", "high", "low", "close", "volume", "adj_close"}
    assert expected.issubset(df.columns)


def test_extract_normalizes_columns(monkeypatch):
    """Column names should be lowercase with underscores."""
    raw = pd.DataFrame(
        {
            "Open": [10.0],
            "High": [11.0],
            "Low": [9.5],
            "Close": [10.5],
            "Volume": [1000],
            "Adj Close": [10.5],
        },
        index=pd.Index(pd.to_datetime(["2024-01-02"]), name="Date"),
    )
    
    import yfinance as yf
    monkeypatch.setattr(yf, "download", lambda **_: raw)
    
    df = extractor.extract(["AAPL"])
    
    assert "open" in df.columns
    assert "adj_close" in df.columns
    assert "Open" not in df.columns


def test_extract_renames_ticker_to_symbol(monkeypatch):
    """Ticker column should be renamed to symbol."""
    raw = pd.DataFrame(
        {
            "Open": [10.0],
            "High": [11.0],
            "Low": [9.5],
            "Close": [10.5],
            "Volume": [1000],
            "Adj Close": [10.5],
        },
        index=pd.Index(pd.to_datetime(["2024-01-02"]), name="Date"),
    )
    
    import yfinance as yf
    monkeypatch.setattr(yf, "download", lambda **_: raw)
    
    df = extractor.extract(["AAPL"])
    
    assert "symbol" in df.columns
    assert df["symbol"].iloc[0] == "AAPL"


def test_extract_single_ticker(monkeypatch):
    """Returns normalized columns for a single ticker response."""
    raw = pd.DataFrame(
        {
            "Open": [10.0],
            "High": [11.0],
            "Low": [9.5],
            "Close": [10.5],
            "Volume": [1000],
            "Adj Close": [10.5],
        },
        index=pd.Index(pd.to_datetime(["2024-01-02"]), name="Date"),
    )

    import yfinance as yf
    monkeypatch.setattr(yf, "download", lambda **_: raw)

    prices = extractor.extract(["AAPL"])

    assert prices.loc[0, "symbol"] == "AAPL"


def test_extract_groups_rows_by_symbol_then_date(monkeypatch):
    """Returns price history ordered by symbol and date."""
    columns = pd.MultiIndex.from_tuples(
        [
            ("Open", "MSFT"),
            ("High", "MSFT"),
            ("Low", "MSFT"),
            ("Close", "MSFT"),
            ("Volume", "MSFT"),
            ("Adj Close", "MSFT"),
            ("Open", "AAPL"),
            ("High", "AAPL"),
            ("Low", "AAPL"),
            ("Close", "AAPL"),
            ("Volume", "AAPL"),
            ("Adj Close", "AAPL"),
        ]
    )
    raw = pd.DataFrame(
        [
            [20.0, 21.0, 19.0, 20.5, 2000, 20.5, 10.0, 11.0, 9.0, 10.5, 1000, 10.5],
            [22.0, 23.0, 21.0, 22.5, 2200, 22.5, 12.0, 13.0, 11.0, 12.5, 1200, 12.5],
        ],
        columns=columns,
        index=pd.Index(pd.to_datetime(["2024-01-03", "2024-01-02"]), name="Date"),
    )

    import yfinance as yf
    monkeypatch.setattr(yf, "download", lambda **_: raw)

    prices = extractor.extract(["AAPL", "MSFT"])

    assert prices[["symbol", "date"]].to_dict("records") == [
        {"symbol": "AAPL", "date": pd.Timestamp("2024-01-02")},
        {"symbol": "AAPL", "date": pd.Timestamp("2024-01-03")},
        {"symbol": "MSFT", "date": pd.Timestamp("2024-01-02")},
        {"symbol": "MSFT", "date": pd.Timestamp("2024-01-03")},
    ]


def test_extract_raises_when_ticker_missing(monkeypatch):
    """Raises when the download result omits a requested ticker."""
    columns = pd.MultiIndex.from_tuples(
        [("Open", "AAPL"), ("High", "AAPL"), ("Low", "AAPL"), ("Close", "AAPL"), ("Volume", "AAPL"), ("Adj Close", "AAPL")]
    )
    raw = pd.DataFrame(
        [[10.0, 11.0, 9.5, 10.5, 1000, 10.5]],
        columns=columns,
        index=pd.Index(pd.to_datetime(["2024-01-02"]), name="Date"),
    )

    import yfinance as yf
    monkeypatch.setattr(yf, "download", lambda **_: raw)

    with pytest.raises(ValueError, match="Price history is missing tickers"):
        extractor.extract(["AAPL", "MSFT"])