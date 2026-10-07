import pandas as pd

from src.pipeline.etl_pipeline import Extractor, Transformer, Loader, DatabaseLoader, ETLPipeline


extractor = Extractor()
transformer = Transformer()
loader = Loader()
db_loader = DatabaseLoader()
pipeline = ETLPipeline(extractor, transformer, loader, db_loader)


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



# FULL ETL
def test_run_etl_success(monkeypatch, tmp_path):
    """Test ETL orchestration without real extraction or filesystem."""

    test_data = make_valid_dataframe()

    # Replace extract() with deterministic test data.
    monkeypatch.setattr(extractor, "extract", lambda tickers: test_data)

    def output_path(symbol):
        return tmp_path / f"{symbol}.csv"

    # Keep the real load logic, but redirect its output to tmp_path.
    original_load = loader.load

    def test_load(df, output_path_factory=None):
        return original_load(df, output_path_factory=output_path)

    monkeypatch.setattr(loader, "load", test_load)

    result = pipeline.run(tickers=["AAPL", "SPY"])

    assert result["status"] == "success"
    assert result["extracted"] == 3
    assert result["transformed"] == 3
    assert result["saved"] == 2
    assert result["errors"] == []

    assert (tmp_path / "AAPL.csv").exists()
    assert (tmp_path / "SPY.csv").exists()


def test_run_etl_fails_when_extraction_fails(monkeypatch):
    """A failed extraction should stop the ETL."""

    def failing_extract(tickers):
        raise RuntimeError("Extraction failed")

    monkeypatch.setattr(extractor, "extract", failing_extract)

    result = pipeline.run(tickers=["AAPL"])

    assert result["status"] == "failed"
    assert result["saved"] == 0
    assert result["transformed"] == 0
    assert "Extraction failed" in result["errors"][0]


def test_run_etl_fails_when_transform_fails(monkeypatch):
    """A transformation error should prevent loading."""

    test_data = make_valid_dataframe()
    monkeypatch.setattr(extractor, "extract", lambda tickers: test_data)

    def failing_transform(df):
        raise ValueError("Invalid market data")

    monkeypatch.setattr(transformer, "transform", failing_transform)

    result = pipeline.run(tickers=["AAPL"])

    assert result["status"] == "failed"
    assert result["saved"] == 0
    assert "Invalid market data" in result["errors"][0]


def test_run_etl_reports_partial_load_failure(monkeypatch):
    """A partial load should result in a partial ETL status."""

    test_data = make_valid_dataframe()
    monkeypatch.setattr(extractor, "extract", lambda tickers: test_data)

    def partially_failing_load(df, output_path_factory=None):
        return {"saved": 1, "errors": ["SPY: simulated save failure"]}

    monkeypatch.setattr(loader, "load", partially_failing_load)

    result = pipeline.run(tickers=["AAPL", "SPY"])

    assert result["status"] == "partial"
    assert result["saved"] == 1
    assert result["errors"] == ["SPY: simulated save failure"]
