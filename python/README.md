# Market Data Fetcher

Fetch historical market data from yfinance and persist to CSV files, organized by ticker and date.

## Overview

This module provides functions to:
- **Fetch price history** for equities, ETFs, and bonds over 10 years of daily data
- **Fetch ticker metadata** including asset class, long name, sector, and currency
- **Save data to CSV** grouped by ticker

## Setup

### 1. Create a virtual environment

```bash
cd /path/to/the_commit_crew/python
python3 -m venv venv
```

### 2. Activate the virtual environment

**On Linux/macOS:**
```bash
source venv/bin/activate
```

**On Windows:**
```bash
venv\Scripts\activate
```

### 3. Install dependencies

```bash
pip install -r requirements.txt
```

## Running the Script

### Fetch all data (price history + metadata)

```bash
python src/fetch_market_data.py
```

This will:
1. Fetch price history for all 30 tickers (10 equities, 10 ETFs, 10 bonds)
2. Fetch metadata for all tickers
3. Write two CSV files to `market_data/`:
   - `price_history.csv` — grouped by ticker, then date
   - `ticker_metadata.csv` — sorted by ticker

## Output

### price_history.csv

Columns: `date`, `ticker`, `open`, `high`, `low`, `close`, `volume`

Ordered by ticker (AAPL, AMZN, BABA, ...) then by date within each ticker.

Example:
```
date,ticker,open,high,low,close,volume
2024-01-02,AAPL,150.25,151.50,150.10,150.75,50000000
2024-01-02,AMZN,180.50,181.00,180.00,180.50,30000000
```

### ticker_metadata.csv

Columns: `ticker`, `asset_class`, `long_name`, `sector`, `currency`, `tradable`

Ordered alphabetically by ticker.

Example:
```
ticker,asset_class,long_name,sector,currency,tradable
AAPL,equity,Apple Inc.,Technology,USD,True
AMZN,equity,Amazon.com Inc.,Consumer Cyclical,USD,True
```

## Testing

Run the test suite:

```bash
pytest test/test_fetch_market_data.py -v
```

## Tickers

### Equities (10)
AAPL, MSFT, GOOGL, AMZN, TSLA, META, NVDA, NFLX, TTWO, BABA

### ETFs (10)
XLF, QQQ, SPY, VTI, DIA, IWM, XLV, XLK, XLE, VNQ

### Bonds (10)
TLT, SHY, IEI, IEF, TIP, VGIT, TLH, MUB, LQD, HYG

## Deactivate the virtual environment

```bash
deactivate
```

## Data Retention

CSV files are written to `market_data/` and persist across runs.
