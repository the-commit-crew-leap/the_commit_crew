import csv
import logging
from pathlib import Path
import pandas as pd
import yfinance as yf

from config import config

# Set up logging
logger = logging.getLogger(__name__)


class MetadataPipeline:
    """Extract instrument metadata and generate its CSV and SQL outputs."""

    SEED_FILE = Path(__file__).resolve().parents[3] / "db" / "seeds" / "instruments-data.sql"
    
    def fetch_symbol_metadata(self, symbols: list[str]) -> pd.DataFrame:
        """
        Fetch ticker metadata from yfinance for given symbols.
        
        Args:
            symbols: List of instrument symbols to fetch.
            
        Returns:
            DataFrame with ticker metadata including asset class and currency.
    
        Raises:
            ValueError: If metadata lookups fail or required fields are missing.
        """
        logger.info("Fetching metadata for %s symbols", len(symbols))
        
        rows = []
        failed_symbols = {}
        
        for symbol in symbols:
            instrument = yf.Ticker(symbol)
            info = {}
            
            try:
                info = instrument.get_info()
            except Exception as error:
                failed_symbols[symbol] = str(error)
                logger.warning("Metadata lookup failed for %s: %s", symbol, error)
            
            rows.append(
                {
                    "symbol": symbol,
                    "asset_class": config.SYMBOL_CLASS.get(symbol),
                    "long_name": info.get("longName"),
                    "sector": info.get("sector"),
                    "currency": info.get("currency"),
                    "tradable": True,
                }
            )
        
        metadata = pd.DataFrame(rows)
        
        # Validate output
        if len(metadata) != len(symbols):
            raise ValueError("Metadata row count does not match the number of requested tickers")
        
        missing_columns = set(config.REQUIRED_METADATA_FIELDS) - set(metadata.columns)
        if missing_columns:
            raise ValueError(f"Metadata is missing columns: {sorted(missing_columns)}")
        
        found_symbols = set(metadata["symbol"].dropna())
        missing_symbols = [symbol for symbol in symbols if symbol not in found_symbols]
        if missing_symbols:
            raise ValueError(f"Metadata is missing symbols: {missing_symbols}")
        
        missing_required_values = {}
        for field in config.REQUIRED_METADATA_FIELDS:
            missing_for_field = metadata.loc[metadata[field].isna(), "symbol"].tolist()
            if missing_for_field:
                missing_required_values[field] = missing_for_field
        
        validation_errors = []
        
        if failed_symbols:
            validation_errors.append(
            "Metadata lookup failed for symbols: "
            + ", ".join(f"{symbol} ({message})" for symbol, message in failed_symbols.items())
            )
            
        if missing_required_values:
            validation_errors.append(
                "Metadata is missing required values: "
                + ", ".join(f"{field}={symbols}" for field, symbols in missing_required_values.items())
            )
            
        if validation_errors:
            raise ValueError("; ".join(validation_errors))
        
        logger.info("Fetched metadata for %s tickers", len(metadata))
        return metadata
    
    
    def save_symbol_metadata(self, metadata: pd.DataFrame) -> dict:
        """
        Save ticker metadata to CSV.
        
        Args:
            metadata: Ticker metadata DataFrame.
            
        Returns:
            Dictionary with save status ("saved" count and "errors" list).
        """
        try:
            sorted_metadata = metadata.sort_values("symbol").reset_index(drop=True)
            sorted_metadata.to_csv(config.SYMBOL_METADATA_FILE, index=False)
            
            logger.info(f"Saved symbol metadata to {config.SYMBOL_METADATA_FILE}")
            
            return {"saved": 1, "errors": []}
        
        except Exception as e:
            msg = f"Failed to save ticker metadata: {str(e)}"
            logger.error(msg)
            return {"saved": 0, "errors": [msg]}

    def generate_instruments_seed(self, metadata_path: Path = None) -> None:
        """Generate the database seed from the symbol metadata CSV."""
        metadata_path = metadata_path or config.SYMBOL_METADATA_FILE
        rows = []

        with metadata_path.open(newline="") as metadata_file:
            for row in csv.DictReader(metadata_file):
                name = row["long_name"].replace("'", "''")
                asset_class = config.ASSET_CLASS_MAP.get(row["asset_class"].lower(), "FUND")
                rows.append(
                    (
                        row["symbol"].upper(),
                        name,
                        asset_class,
                        row["currency"],
                        str(row["tradable"]).lower() == "true",
                    )
                )

        sql_lines = [
            "-- Auto-generated from symbol_metadata.csv",
            "-- Do not edit manually; regenerate by running the analytics pipeline.",
            "",
            "INSERT INTO instruments (symbol, name, asset_class, currency, tradable) VALUES",
        ]

        for index, (symbol, name, asset_class, currency, tradable) in enumerate(rows):
            comma = "," if index < len(rows) - 1 else ""
            sql_lines.append(
                f"    ('{symbol}', '{name}', '{asset_class}', '{currency}', {str(tradable).lower()}){comma}"
            )

        sql_lines.extend(["ON CONFLICT (symbol) DO NOTHING;", ""])
        self.SEED_FILE.parent.mkdir(parents=True, exist_ok=True)
        self.SEED_FILE.write_text("\n".join(sql_lines))
        logger.info("Generated instruments seed at %s", self.SEED_FILE)
    
    
    def run(self, symbols: list[str] = None) -> dict:
        """
        Execute metadata extraction and saving.
        
        Args:
            tickers: List of tickers to process. Defaults to config.INSTRUMENTS_LIST.
            
        Returns:
            Dictionary with metadata pipeline status.
        """
        if symbols is None:
            symbols = config.INSTRUMENTS_LIST
        
        logger.info("Starting metadata pipeline")
        
        try:
            metadata = self.fetch_symbol_metadata(symbols)
            result = self.save_symbol_metadata(metadata)
            if not result["errors"]:
                self.generate_instruments_seed()
            
            logger.info("Metadata pipeline complete")
            
            return {
                "status": ("success" if not result["errors"] else "partial"),
                "saved": result["saved"],
                "errors": result["errors"],
                "count": len(metadata),
            }
            
        except Exception as e:
            logger.error("Metadata pipeline failed", exc_info=True)
            
            return {
                "status": "failed",
                "saved": 0,
                "errors": [str(e)],
            }


if __name__ == "__main__":
    metadata_pipeline = MetadataPipeline()
    metadata_result = metadata_pipeline.run()
    print("Metadata:", metadata_result)