# Portfolio Analytics Pipeline

This directory contains the Python analytics stack for The Commit Crew trading portfolio management system.

## Quick Start

### 1. Setup

```bash
cd analytics
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env
# Edit .env with your PostgreSQL credentials

```

### 2. Run scripts

```bash
# Run ETL
python -m src.etl

# Run analysis
python -m src.analysis

# Run main
python -m src.main run

# Run Tests
python -m pytest -v tests
python -m pytest -v tests/test_etl.py

```