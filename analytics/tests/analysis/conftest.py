import pandas as pd
import pytest


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