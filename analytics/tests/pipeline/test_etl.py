import pandas as pd
import pytest

from analytics.src.pipeline.etl_pipeline import extract, transform, load, run_etl


# ============================================================
# TEST DATA
# ============================================================
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


# ============================================================
# EXTRACT
# ============================================================
def test_extract_returns_dataframe():
    """Mock extraction should return a non-empty DataFrame."""
    df = extract()

    assert isinstance(df, pd.DataFrame)
    assert not df.empty


def test_extract_has_expected_columns():
    """Extraction must provide the columns expected by transform()."""
    df = extract()

    expected = {"symbol", "date", "open", "high", "low", "close", "volume", "adj_close"}

    assert expected.issubset(df.columns)


def test_extract_contains_symbols():
    df = extract()

    assert df["symbol"].notna().all()
    assert df["symbol"].nunique() > 0


# ============================================================
# TRANSFORM
# ============================================================
def test_transform_returns_clean_dataframe():
    result = transform(make_valid_dataframe())

    assert isinstance(result, pd.DataFrame)
    assert not result.empty


def test_transform_normalizes_symbols():
    df = make_valid_dataframe()
    df.loc[0, "symbol"] = " aapl "

    result = transform(df)

    assert result.loc[0, "symbol"] == "AAPL"


def test_transform_normalizes_dates():
    df = make_valid_dataframe()
    df.loc[0, "date"] = "2026-01-02 15:30:45"

    result = transform(df)

    assert result.loc[0, "date"] == pd.Timestamp("2026-01-02")


def test_transform_converts_numeric_columns():
    df = make_valid_dataframe()
    numeric_columns = ["open", "high", "low", "close", "adj_close", "volume"]

    for column in numeric_columns:
        df[column] = df[column].astype(str)

    result = transform(df)

    for column in numeric_columns:
        assert pd.api.types.is_numeric_dtype(result[column])


def test_transform_removes_missing_required_values():
    df = make_valid_dataframe()
    df.loc[0, "close"] = None

    result = transform(df)

    assert len(result) == 2
    assert not result["close"].isna().any()


def test_transform_rejects_missing_columns():
    df = make_valid_dataframe().drop(columns=["close"])

    with pytest.raises(ValueError, match="Missing required columns"):
        transform(df)


def test_transform_rejects_empty_dataframe():
    with pytest.raises(ValueError, match="Cannot transform an empty DataFrame"):
        transform(pd.DataFrame())


def test_transform_removes_invalid_prices():
    df = make_valid_dataframe()
    df.loc[0, "high"] = 90.0  # high < low

    result = transform(df)

    assert len(result) == 2


def test_transform_removes_negative_prices():
    df = make_valid_dataframe()
    df.loc[0, "close"] = -10.0

    result = transform(df)

    assert len(result) == 2


def test_transform_removes_zero_prices():
    df = make_valid_dataframe()
    df.loc[0, "close"] = 0

    result = transform(df)

    assert len(result) == 2


def test_transform_removes_negative_volume():
    df = make_valid_dataframe()
    df.loc[0, "volume"] = -100

    result = transform(df)

    assert len(result) == 2


def test_transform_removes_duplicate_symbol_date():
    df = make_valid_dataframe()
    df = pd.concat([df, df.iloc[[0]]], ignore_index=True)

    result = transform(df)

    assert not result.duplicated(subset=["symbol", "date"]).any()


def test_transform_keeps_last_duplicate():
    df = make_valid_dataframe()

    duplicate = df.iloc[[0]].copy()
    duplicate["close"] = 104.0
    df = pd.concat([df, duplicate], ignore_index=True)

    result = transform(df)

    aapl = result[
        (result["symbol"] == "AAPL") &
        (result["date"] == pd.Timestamp("2026-01-02"))
    ]

    assert len(aapl) == 1
    assert aapl.iloc[0]["close"] == 104.0


def test_transform_creates_price_change():
    result = transform(make_valid_dataframe())
    row = result.iloc[0]

    assert row["price_change"] == row["close"] - row["open"]


def test_transform_creates_pct_change():
    result = transform(make_valid_dataframe())
    row = result.iloc[0]

    expected = round((row["close"] - row["open"]) / row["open"] * 100, 2)

    assert row["pct_change"] == expected


def test_transform_adds_load_timestamp():
    result = transform(make_valid_dataframe())

    assert "load_timestamp" in result.columns
    assert result["load_timestamp"].notna().all()


def test_transform_sorts_by_symbol_and_date():
    df = make_valid_dataframe().iloc[[2, 0, 1]].reset_index(drop=True)

    result = transform(df)
    expected = result.sort_values(["symbol", "date"]).reset_index(drop=True)

    pd.testing.assert_frame_equal(result, expected)


# ============================================================
# LOAD
# ============================================================
def test_load_creates_csv_files(tmp_path):
    df = transform(make_valid_dataframe())

    def output_path(symbol):
        return tmp_path / f"{symbol}.csv"

    result = load(df, output_path_factory=output_path)

    assert result["saved"] == 2
    assert not result["errors"]
    assert (tmp_path / "AAPL.csv").exists()
    assert (tmp_path / "SPY.csv").exists()


def test_load_creates_one_file_per_symbol(tmp_path):
    df = transform(make_valid_dataframe())

    def output_path(symbol):
        return tmp_path / f"{symbol}.csv"

    load(df, output_path_factory=output_path)

    files = list(tmp_path.glob("*.csv"))

    assert len(files) == 2
    assert {file.stem for file in files} == {"AAPL", "SPY"}


def test_load_writes_correct_data(tmp_path):
    df = transform(make_valid_dataframe())

    def output_path(symbol):
        return tmp_path / f"{symbol}.csv"

    load(df, output_path_factory=output_path)

    saved = pd.read_csv(tmp_path / "AAPL.csv", parse_dates=["date"])

    assert len(saved) == 2
    assert set(saved["symbol"]) == {"AAPL"}


def test_load_empty_dataframe():
    result = load(pd.DataFrame())

    assert result["saved"] == 0
    assert "Empty dataframe" in result["errors"]


def test_load_merges_existing_data(tmp_path):
    df = transform(make_valid_dataframe())

    def output_path(symbol):
        return tmp_path / f"{symbol}.csv"

    load(df, output_path_factory=output_path)

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

    load(transform(new_data), output_path_factory=output_path)

    saved = pd.read_csv(tmp_path / "AAPL.csv", parse_dates=["date"])

    assert len(saved) == 3


def test_load_replaces_existing_date(tmp_path):
    df = transform(make_valid_dataframe())

    def output_path(symbol):
        return tmp_path / f"{symbol}.csv"

    load(df, output_path_factory=output_path)

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

    load(transform(updated), output_path_factory=output_path)

    saved = pd.read_csv(tmp_path / "AAPL.csv", parse_dates=["date"])
    aapl_date = saved[saved["date"] == pd.Timestamp("2026-01-02")]

    assert len(aapl_date) == 1
    assert aapl_date.iloc[0]["close"] == 108.0


# ============================================================
# FULL ETL
# ============================================================
def test_run_etl_success(monkeypatch, tmp_path):
    """Test ETL orchestration without real extraction or filesystem."""

    test_data = make_valid_dataframe()

    # Replace extract() with deterministic test data.
    monkeypatch.setattr("src.etl.extract", lambda: test_data)

    def output_path(symbol):
        return tmp_path / f"{symbol}.csv"

    # Keep the real load logic, but redirect its output to tmp_path.
    original_load = load

    def test_load(df):
        return original_load(df, output_path_factory=output_path)

    monkeypatch.setattr("src.etl.load", test_load)

    result = run_etl()

    assert result["status"] == "success"
    assert result["extracted"] == 3
    assert result["transformed"] == 3
    assert result["saved"] == 2
    assert result["errors"] == []

    assert (tmp_path / "AAPL.csv").exists()
    assert (tmp_path / "SPY.csv").exists()


def test_run_etl_fails_when_extraction_fails(monkeypatch):
    """A failed extraction should stop the ETL."""

    def failing_extract():
        raise RuntimeError("Extraction failed")

    monkeypatch.setattr("src.etl.extract", failing_extract)

    result = run_etl()

    assert result["status"] == "failed"
    assert result["saved"] == 0
    assert result["transformed"] == 0
    assert "Extraction failed" in result["errors"][0]


def test_run_etl_fails_when_transform_fails(monkeypatch):
    """A transformation error should prevent loading."""

    monkeypatch.setattr("src.etl.extract", lambda: make_valid_dataframe())

    def failing_transform(df):
        raise ValueError("Invalid market data")

    monkeypatch.setattr("src.etl.transform", failing_transform)

    result = run_etl()

    assert result["status"] == "failed"
    assert result["saved"] == 0
    assert "Invalid market data" in result["errors"][0]


def test_run_etl_reports_partial_load_failure(monkeypatch):
    """A partial load should result in a partial ETL status."""

    monkeypatch.setattr("src.etl.extract", lambda: make_valid_dataframe())

    def partially_failing_load(df):
        return {"saved": 1, "errors": ["SPY: simulated save failure"]}

    monkeypatch.setattr("src.etl.load", partially_failing_load)

    result = run_etl()

    assert result["status"] == "partial"
    assert result["saved"] == 1
    assert result["errors"] == ["SPY: simulated save failure"]
