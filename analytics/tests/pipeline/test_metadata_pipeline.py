import pandas as pd
import pytest
import yfinance as yf

from config import config
from src.pipeline.metadata_pipeline import MetadataPipeline
    
metadata_pipeline = MetadataPipeline()


# METADATA
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

    monkeypatch.setattr(yf, "Ticker", FakeTicker)

    metadata = metadata_pipeline.fetch_ticker_metadata(["AAPL", "TLT"])

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

    import yfinance as yf
    monkeypatch.setattr(yf, "Ticker", FailingTicker)

    with pytest.raises(ValueError, match="Metadata lookup failed for tickers"):
        metadata_pipeline.fetch_ticker_metadata(["AAPL"])


def test_fetch_ticker_metadata_raises_when_required_fields_missing(monkeypatch):
    """Raises when required metadata values are absent."""
    class IncompleteTicker:
        def __init__(self, symbol):
            self.symbol = symbol

        def get_info(self):
            return {"sector": "Technology"}

    import yfinance as yf
    monkeypatch.setattr(yf, "Ticker", IncompleteTicker)

    with pytest.raises(ValueError, match="Metadata is missing required values"):
        metadata_pipeline.fetch_ticker_metadata(["AAPL"])
        
    
def test_save_ticker_metadata_writes_csv_without_touching_prices(tmp_path, monkeypatch):
    """Writes metadata independently from price history."""
    metadata_file = tmp_path / "ticker_metadata.csv"
    monkeypatch.setattr(config, "TICKER_METADATA_FILE", metadata_file)

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

    metadata_pipeline.save_ticker_metadata(metadata)

    written = pd.read_csv(metadata_file)
    assert written["ticker"].tolist() == ["AAPL", "MSFT"]
    assert not (tmp_path / "price_history.csv").exists()