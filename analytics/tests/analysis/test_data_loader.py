import pandas as pd

from config import config
from src.analysis.data_loader import DataLoader


data_loader = DataLoader()


# LOAD PRICE DATA
def test_load_price_data_missing_file(monkeypatch):
    monkeypatch.setattr(
        config,
        "get_ticker_csv_path",
        lambda _: config.CHARTS_DIR / "missing.csv"
    )

    result = data_loader.load_price_data("AAPL")

    assert isinstance(result, pd.DataFrame)
    assert result.empty


def test_load_price_data_empty_file(monkeypatch, tmp_path):
    path = tmp_path / "AAPL.csv"

    pd.DataFrame(columns=["symbol", "date", "close", "adj_close", "volume"]).to_csv(path, index=False)

    monkeypatch.setattr(
        config,
        "get_ticker_csv_path",
        lambda _: path
    )

    result = data_loader.load_price_data("AAPL")

    assert isinstance(result, pd.DataFrame)
    assert result.empty


def test_load_price_data_removes_duplicate_dates(monkeypatch, tmp_path, price_dataframe):
    path = tmp_path / "AAPL.csv"

    df = pd.concat([price_dataframe.iloc[:3], price_dataframe.iloc[[0]].assign(close=999)])
    df.to_csv(path, index=False)

    monkeypatch.setattr(
        config,
        "get_ticker_csv_path",
        lambda _: path
    )

    result = data_loader.load_price_data("aapl")

    assert len(result) == 3

    row = result[result["date"] == pd.Timestamp("2026-01-01")]

    assert row.iloc[0]["close"] == 999


def test_load_price_data_filters_dates(monkeypatch, csv_file):
    monkeypatch.setattr(
        config,
        "get_ticker_csv_path",
        lambda _: csv_file
    )

    result = data_loader.load_price_data(
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
        config,
        "get_ticker_csv_path",
        lambda _: path
    )

    def raise_error(*args, **kwargs):
        raise Exception("CSV read error")

    monkeypatch.setattr(
        pd,
        "read_csv",
        raise_error
    )

    result = data_loader.load_price_data("AAPL")

    assert isinstance(result, pd.DataFrame)
    assert result.empty