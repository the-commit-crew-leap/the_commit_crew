import pandas as pd
import pytest

from src.pipeline.transformer import Transformer

transformer = Transformer()


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


# TRANSFORM
def test_transform_returns_clean_dataframe():
    result = transformer.transform(make_valid_dataframe())

    assert isinstance(result, pd.DataFrame)
    assert not result.empty


def test_transform_normalizes_symbols():
    df = make_valid_dataframe()
    df.loc[0, "symbol"] = " aapl "

    result = transformer.transform(df)

    assert result.loc[0, "symbol"] == "AAPL"


def test_transform_normalizes_dates():
    df = make_valid_dataframe()
    df.loc[0, "date"] = "2026-01-02 15:30:45"

    result = transformer.transform(df)

    assert result.loc[0, "date"] == pd.Timestamp("2026-01-02")


def test_transform_converts_numeric_columns():
    df = make_valid_dataframe()
    numeric_columns = ["open", "high", "low", "close", "adj_close", "volume"]

    for column in numeric_columns:
        df[column] = df[column].astype(str)

    result = transformer.transform(df)

    for column in numeric_columns:
        assert pd.api.types.is_numeric_dtype(result[column])


def test_transform_removes_missing_required_values():
    df = make_valid_dataframe()
    df.loc[0, "close"] = None

    result = transformer.transform(df)

    assert len(result) == 2
    assert not result["close"].isna().any()


def test_transform_rejects_missing_columns():
    df = make_valid_dataframe().drop(columns=["close"])

    with pytest.raises(ValueError, match="Missing required columns"):
        transformer.transform(df)


def test_transform_rejects_empty_dataframe():
    with pytest.raises(ValueError, match="Cannot transform an empty DataFrame"):
        transformer.transform(pd.DataFrame())


def test_transform_removes_invalid_prices():
    df = make_valid_dataframe()
    df.loc[0, "high"] = 90.0  # high < low

    result = transformer.transform(df)

    assert len(result) == 2


def test_transform_removes_negative_prices():
    df = make_valid_dataframe()
    df.loc[0, "close"] = -10.0

    result = transformer.transform(df)

    assert len(result) == 2


def test_transform_removes_zero_prices():
    df = make_valid_dataframe()
    df.loc[0, "close"] = 0

    result = transformer.transform(df)

    assert len(result) == 2


def test_transform_removes_negative_volume():
    df = make_valid_dataframe()
    df.loc[0, "volume"] = -100

    result = transformer.transform(df)

    assert len(result) == 2


def test_transform_removes_duplicate_symbol_date():
    df = make_valid_dataframe()
    df = pd.concat([df, df.iloc[[0]]], ignore_index=True)

    result = transformer.transform(df)

    assert not result.duplicated(subset=["symbol", "date"]).any()


def test_transform_keeps_last_duplicate():
    df = make_valid_dataframe()

    duplicate = df.iloc[[0]].copy()
    duplicate["close"] = 104.0
    df = pd.concat([df, duplicate], ignore_index=True)

    result = transformer.transform(df)

    aapl = result[
        (result["symbol"] == "AAPL") &
        (result["date"] == pd.Timestamp("2026-01-02"))
    ]

    assert len(aapl) == 1
    assert aapl.iloc[0]["close"] == 104.0


def test_transform_creates_price_change():
    result = transformer.transform(make_valid_dataframe())
    row = result.iloc[0]

    assert row["price_change"] == row["close"] - row["open"]


def test_transform_creates_pct_change():
    result = transformer.transform(make_valid_dataframe())
    row = result.iloc[0]

    expected = round((row["close"] - row["open"]) / row["open"] * 100, 2)

    assert row["pct_change"] == expected


def test_transform_adds_load_timestamp():
    result = transformer.transform(make_valid_dataframe())

    assert "load_timestamp" in result.columns
    assert result["load_timestamp"].notna().all()


def test_transform_sorts_by_symbol_and_date():
    df = make_valid_dataframe().iloc[[2, 0, 1]].reset_index(drop=True)

    result = transformer.transform(df)
    expected = result.sort_values(["symbol", "date"]).reset_index(drop=True)

    pd.testing.assert_frame_equal(result, expected)
