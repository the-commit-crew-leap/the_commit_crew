# Portfolio Analytics Pipeline

This directory contains the Python analytics stack for The Commit Crew trading portfolio management system.

## Quick Start

### 1. Setup

```bash
cd analytics
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
pip install -r requirements.txt
```

### 2. Run scripts

```bash
# Run ETL
python -m src.etl

# Run analysis
python -m src.analysis

# Run main
python -m src.main run

# Analyze specific tickers and period
python -m src.main run --tickers AAPL BND SPY --period 1y

# Analyze by asset class and period
python -m src.main run --asset-classes stocks bonds --period 5y

# Different periods
python -m src.main run --period 5y
python -m src.main run --period all
```


### 2. Run tests

```bash
# Run all tests
python -m pytest -v tests

# Run specific test
python -m pytest -v tests/test_etl.py

# Run tests coverage
# Needs pytest-cov installed
python -m pytest --cov=src --cov-report=term-missing tests/
```


### 3. API Endpoints

```bash
# Run server
python -m src.api_module
```

Service available on `localhost:8000`.

Six different endpoints:
- `GET /health` -> checks if service is running
- `POST /analysis` -> runs the complete analysis (statistics + plots)
```bash
curl -X POST "http://127.0.0.1:8000/analysis?tickers=AAPL&tickers=SPY&tickers=BND&period=1y&volatility_window=30"

```
- `GET /insights` -> returns web report with statistics
```bash
curl "http://localhost:8000/insights?asset_classes=stocks&asset_classes=bonds&period=5y"
```
- `GET /charts/correlation` -> returns correlation heatmap
```bash
curl "http://localhost:8000/charts/correlation?tickers=AAPL&tickers=SPY&tickers=BND&period=1y"
```
- `GET /charts/volatility` -> return rolling volatility chart
```bash
curl "http://localhost:8000/charts/volatility?tickers=AAPL&tickers=SPY&period=1y&volatility_window=30"
```
- `GET /charts/asset-class-volatility` -> returns the asset-class volatility chart
```bash
curl "http://localhost:8000/charts/asset-class-volatility?tickers=AAPL&tickers=SPY&tickers=BND&period=1y"
```