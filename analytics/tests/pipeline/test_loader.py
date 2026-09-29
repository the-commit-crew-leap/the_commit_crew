import pandas as pd

from src.pipeline.transformer import Transformer
from src.pipeline.loader import Loader

transformer = Transformer()
loader = Loader()


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


# LOAD
def test_load_creates_csv_files(tmp_path):
    df = transformer.transform(make_valid_dataframe())

    def output_path(symbol):
        return tmp_path / f"{symbol}.csv"

    result = loader.load(df, output_path_factory=output_path)

    assert result["saved"] == 2
    assert not result["errors"]
    assert (tmp_path / "AAPL.csv").exists()
    assert (tmp_path / "SPY.csv").exists()


def test_load_creates_one_file_per_symbol(tmp_path):
    df = transformer.transform(make_valid_dataframe())

    def output_path(symbol):
        return tmp_path / f"{symbol}.csv"

    loader.load(df, output_path_factory=output_path)

    files = list(tmp_path.glob("*.csv"))

    assert len(files) == 2
    assert {file.stem for file in files} == {"AAPL", "SPY"}


def test_load_writes_correct_data(tmp_path):
    df = transformer.transform(make_valid_dataframe())

    def output_path(symbol):
        return tmp_path / f"{symbol}.csv"

    loader.load(df, output_path_factory=output_path)

    saved = pd.read_csv(tmp_path / "AAPL.csv", parse_dates=["date"])

    assert len(saved) == 2
    assert set(saved["symbol"]) == {"AAPL"}


def test_load_empty_dataframe():
    result = loader.load(pd.DataFrame())

    assert result["saved"] == 0
    assert "Empty dataframe" in result["errors"]


def test_load_merges_existing_data(tmp_path):
    df = transformer.transform(make_valid_dataframe())

    def output_path(symbol):
        return tmp_path / f"{symbol}.csv"

    loader.load(df, output_path_factory=output_path)

    new_data = pd.DataFrame({
        "symbol": ["AAPL"],
        "date": ["2026-01-06"],
        "open": [105.0],
        "high": [108.0],
        "low": [104.0],
        "close": [107.0],
        "volume": [1_200_000],
        "adj_close": [107.0],
    })

    loader.load(transformer.transform(new_data), output_path_factory=output_path)

    saved = pd.read_csv(tmp_path / "AAPL.csv", parse_dates=["date"])

    assert len(saved) == 3


def test_load_replaces_existing_date(tmp_path):
    df = transformer.transform(make_valid_dataframe())

    def output_path(symbol):
        return tmp_path / f"{symbol}.csv"

    loader.load(df, output_path_factory=output_path)

    updated = pd.DataFrame({
        "symbol": ["AAPL"],
        "date": ["2026-01-02"],
        "open": [100.0],
        "high": [110.0],
        "low": [99.0],
        "close": [108.0],
        "volume": [9_999_999],
        "adj_close": [108.0],
    })

    loader.load(transformer.transform(updated), output_path_factory=output_path)

    saved = pd.read_csv(tmp_path / "AAPL.csv", parse_dates=["date"])
    aapl_date = saved[saved["date"] == pd.Timestamp("2026-01-02")]

    assert len(aapl_date) == 1
    assert aapl_date.iloc[0]["close"] == 108.0
