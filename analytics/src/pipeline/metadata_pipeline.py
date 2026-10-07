import pandas as pd
import yfinance as yf
import logging

from config import config
from src.pipeline.database_loader import DatabaseLoader

# Set up logging
logger = logging.getLogger(__name__)


class MetadataPipeline:
    """Self-contained pipeline for extracting and saving ticker metadata."""
    
    def __init__(self, db_loader: DatabaseLoader):
        self.db_loader = db_loader
        
    
    def fetch_ticker_metadata(self, tickers: list[str]) -> pd.DataFrame:
        """
        Fetch ticker metadata from yfinance for given symbols.
        
        Args:
            tickers: List of ticker symbols to fetch.
            
        Returns:
            DataFrame with ticker metadata including asset class and currency.
    
        Raises:
            ValueError: If metadata lookups fail or required fields are missing.
        """
        logger.info("Fetching metadata for %s tickers", len(tickers))
        
        rows = []
        failed_tickers = {}
        
        for symbol in tickers:
            ticker = yf.Ticker(symbol)
            info = {}
            
            try:
                info = ticker.get_info()
            except Exception as error:
                failed_tickers[symbol] = str(error)
                logger.warning("Metadata lookup failed for %s: %s", symbol, error)
            
            rows.append(
                {
                    "ticker": symbol,
                    "asset_class": config.TICKER_CLASS.get(symbol),
                    "long_name": info.get("longName"),
                    "sector": info.get("sector"),
                    "currency": info.get("currency"),
                    "tradable": True,
                }
            )
        
        metadata = pd.DataFrame(rows)
        
        # Validate output
        if len(metadata) != len(tickers):
            raise ValueError("Metadata row count does not match the number of requested tickers")
        
        missing_columns = set(config.REQUIRED_METADATA_FIELDS) - set(metadata.columns)
        if missing_columns:
            raise ValueError(f"Metadata is missing columns: {sorted(missing_columns)}")
        
        found_tickers = set(metadata["ticker"].dropna())
        missing_tickers = [ticker for ticker in tickers if ticker not in found_tickers]
        if missing_tickers:
            raise ValueError(f"Metadata is missing tickers: {missing_tickers}")
        
        missing_required_values = {}
        for field in config.REQUIRED_METADATA_FIELDS:
            missing_for_field = metadata.loc[metadata[field].isna(), "ticker"].tolist()
            if missing_for_field:
                missing_required_values[field] = missing_for_field
        
        validation_errors = []
        
        if failed_tickers:
            validation_errors.append(
                "Metadata lookup failed for tickers: "
                + ", ".join(f"{ticker} ({message})" for ticker, message in failed_tickers.items())
            )
            
        if missing_required_values:
            validation_errors.append(
                "Metadata is missing required values: "
                + ", ".join(f"{field}={tickers}" for field, tickers in missing_required_values.items())
            )
            
        if validation_errors:
            raise ValueError("; ".join(validation_errors))
        
        logger.info("Fetched metadata for %s tickers", len(metadata))
        return metadata
    
    
    def save_ticker_metadata(self, metadata: pd.DataFrame) -> dict:
        """
        Save ticker metadata to CSV.
        
        Args:
            metadata: Ticker metadata DataFrame.
            
        Returns:
            Dictionary with save status ("saved" count and "errors" list).
        """
        try:
            sorted_metadata = metadata.sort_values("ticker").reset_index(drop=True)
            sorted_metadata.to_csv(config.TICKER_METADATA_FILE, index=False)
            
            logger.info(f"Saved ticker metadata to {config.TICKER_METADATA_FILE}")
            
            return {"saved": 1, "errors": []}
        
        except Exception as e:
            msg = f"Failed to save ticker metadata: {str(e)}"
            logger.error(msg)
            return {"saved": 0, "errors": [msg]}
    
    
    def run(self, tickers: list[str] = None, sync_to_db: bool = True) -> dict:
        """
        Execute metadata extraction and saving.
        
        Args:
            tickers: List of tickers to process. Defaults to config.INSTRUMENTS_LIST.
            
        Returns:
            Dictionary with metadata pipeline status.
        """
        if tickers is None:
            tickers = config.INSTRUMENTS_LIST
        
        logger.info("Starting metadata pipeline")
        
        try:
            metadata = self.fetch_ticker_metadata(tickers)
            result = self.save_ticker_metadata(metadata)
            
            if sync_to_db:
                self.db_loader.load_instruments_from_csv(str(config.TICKER_METADATA_FILE))
            
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
    db_loader = DatabaseLoader()
    metadata_pipeline = MetadataPipeline(db_loader)
    metadata_result = metadata_pipeline.run()
    print("Metadata:", metadata_result)