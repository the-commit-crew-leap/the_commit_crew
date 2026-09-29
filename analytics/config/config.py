import os
from dotenv import load_dotenv
from pathlib import Path

# Project root
BASE_DIR = Path(__file__).resolve().parent.parent

# Load environment variables
load_dotenv(BASE_DIR / ".env")

# Data Paths
OUTPUT_DIR = BASE_DIR / "outputs"
CHARTS_DIR = OUTPUT_DIR / "charts"
REPORTS_DIR = OUTPUT_DIR / "reports"

# Create directories if they don't exist
CHARTS_DIR.mkdir(parents=True, exist_ok=True)
REPORTS_DIR.mkdir(parents=True, exist_ok=True)

# Ticker lists by asset class
EQUITY_TICKERS = ["AAPL", "MSFT", "GOOGL", "AMZN", "TSLA", "META", "NVDA", "NFLX", "TTWO", "BABA"]
ETF_TICKERS = ["XLF", "QQQ", "SPY", "VTI", "DIA", "IWM", "XLV", "XLK", "XLE", "VNQ"]
BOND_TICKERS = ["TLT", "SHY", "IEI", "IEF", "TIP", "VGIT", "TLH", "MUB", "LQD", "HYG"]

# Build ticker class mapping
TICKER_CLASS = {ticker: "equity" for ticker in EQUITY_TICKERS}
TICKER_CLASS.update({ticker: "etf" for ticker in ETF_TICKERS})
TICKER_CLASS.update({ticker: "bond" for ticker in BOND_TICKERS})

# Instruments to fetch from Yahoo Finance
INSTRUMENTS = {
    'stocks': EQUITY_TICKERS,
    'bonds': BOND_TICKERS,
    'etfs': ETF_TICKERS,
}

# Flatten for compatibility
INSTRUMENTS_LIST = (
    INSTRUMENTS['stocks'] + 
    INSTRUMENTS['bonds'] + 
    INSTRUMENTS['etfs']
)

# Required output columns
REQUIRED_PRICE_COLUMNS = {"date", "ticker", "open", "high", "low", "close", "volume"}
REQUIRED_METADATA_FIELDS = ("ticker", "asset_class", "long_name", "currency", "tradable")

# CSV Output Configuration
CSV_DIR = OUTPUT_DIR / "csv"
CSV_DIR.mkdir(parents=True, exist_ok=True)

# Price history and metadata files
PRICE_HISTORY_FILE = CSV_DIR / "price_history.csv"
TICKER_METADATA_FILE = CSV_DIR / "ticker_metadata.csv"

def get_ticker_csv_dir(symbol: str) -> Path:
    """Get directory for a specific ticker's CSV."""
    ticker_dir = CSV_DIR / symbol
    ticker_dir.mkdir(parents=True, exist_ok=True)
    return ticker_dir

def get_ticker_csv_path(symbol: str) -> Path:
    """Get full path to a ticker's CSV file."""
    return get_ticker_csv_dir(symbol) / f"{symbol}.csv"

# Scheduling Configuration
SCHEDULE_INTERVAL = 24  # hours
ENABLE_SCHEDULER = True
SCHEDULE_TIME = "16:00"  # "HH:MM" format — team member handles actual market close logic in extract()

# Data Configuration
HISTORICAL_YEARS = 10

# Logging Configuration
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO")
LOG_FILE = REPORTS_DIR / "analytics.log"