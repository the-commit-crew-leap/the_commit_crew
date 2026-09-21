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

# Instruments to fetch from Yahoo Finance
INSTRUMENTS = {
    'stocks': [
        # 'AAPL', 'MSFT', 'GOOGL', 'TSLA', 'AMZN',
        # 'NVDA', 'META', 'NFLX', 'SPDY', 'JPM',
    ],
    'bonds': [
        # 'BND', 'AGG', 'LQD', 'HYG', 'SCHZ',
        # 'VCIT', 'MUB', 'IGOV', 'ANGL', 'PFF',
    ],
    'etfs': [
        # 'SPY', 'QQQ', 'IWM', 'EEM', 'GLD',
        # 'USO', 'TLT', 'AGG', 'VTI', 'VXUS',
    ]
}

# Flatten for compatibility
INSTRUMENTS_LIST = (
    INSTRUMENTS['stocks'] + 
    INSTRUMENTS['bonds'] + 
    INSTRUMENTS['etfs']
)

# CSV Output Configuration
CSV_DIR = OUTPUT_DIR / "csv"
CSV_DIR.mkdir(parents=True, exist_ok=True)

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