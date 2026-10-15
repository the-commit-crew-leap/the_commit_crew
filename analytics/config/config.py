import os
from dotenv import load_dotenv
from pathlib import Path

# Project root
BASE_DIR = Path(__file__).resolve().parent.parent
REPO_ROOT = BASE_DIR.parent

# Load environment variables
load_dotenv(BASE_DIR / ".env")

# Data Paths
OUTPUT_DIR = BASE_DIR / "outputs"
CHARTS_DIR = OUTPUT_DIR / "charts"
REPORTS_DIR = OUTPUT_DIR / "reports"

# Create directories if they don't exist
CHARTS_DIR.mkdir(parents=True, exist_ok=True)
REPORTS_DIR.mkdir(parents=True, exist_ok=True)

# Instrument symbols by asset class
EQUITY_SYMBOLS = ["AAPL", "MSFT", "GOOGL", "AMZN", "TSLA", "META", "NVDA", "NFLX", "TTWO", "BABA"]
ETF_SYMBOLS = ["XLF", "QQQ", "SPY", "VTI", "DIA", "IWM", "XLV", "XLK", "XLE", "VNQ"]
BOND_SYMBOLS = ["TLT", "SHY", "IEI", "IEF", "TIP", "VGIT", "TLH", "MUB", "LQD", "HYG"]

# Build symbol class mapping
SYMBOL_CLASS = {symbol: "equity" for symbol in EQUITY_SYMBOLS}
SYMBOL_CLASS.update({symbol: "etf" for symbol in ETF_SYMBOLS})
SYMBOL_CLASS.update({symbol: "bond" for symbol in BOND_SYMBOLS})

# Instruments to fetch from Yahoo Finance
INSTRUMENTS = {
    'stocks': EQUITY_SYMBOLS,
    'bonds': BOND_SYMBOLS,
    'etfs': ETF_SYMBOLS,
}

# Flatten for compatibility
INSTRUMENTS_LIST = (
    INSTRUMENTS['stocks'] + 
    INSTRUMENTS['bonds'] + 
    INSTRUMENTS['etfs']
)

# Required output columns
REQUIRED_PRICE_COLUMNS = {"date", "symbol", "open", "high", "low", "close", "volume"}
REQUIRED_METADATA_FIELDS = ("symbol", "asset_class", "long_name", "currency", "tradable")

# CSV Output Configuration
CSV_DIR = OUTPUT_DIR / "csv"
CSV_DIR.mkdir(parents=True, exist_ok=True)

# Seed file
INSTRUMENTS_SEED_FILE = REPO_ROOT / "db" / "seeds" / "instruments-data.sql"
PRICE_HISTORY_SEED_FILE = REPO_ROOT / "db" / "seeds" / "price_history-data.sql"

# Price history and metadata files
PRICE_HISTORY_FILE = CSV_DIR / "price_history.csv"
SYMBOL_METADATA_FILE = CSV_DIR / "symbol_metadata.csv"

def get_symbol_csv_dir(symbol: str) -> Path:
    """Get directory for a specific ticker's CSV."""
    ticker_dir = CSV_DIR / symbol
    ticker_dir.mkdir(parents=True, exist_ok=True)
    return ticker_dir

def get_symbol_csv_path(symbol: str) -> Path:
    """Get full path to a ticker's CSV file."""
    return get_symbol_csv_dir(symbol) / f"{symbol}.csv"

# Data Configuration
HISTORICAL_YEARS = 10

# Logging Configuration
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO")
LOG_FILE = REPORTS_DIR / "analytics.log"

# For database population
ASSET_CLASS_MAP = {
    "equity": "EQUITY",
    "etf": "FUND",
    "bond": "BOND"
}