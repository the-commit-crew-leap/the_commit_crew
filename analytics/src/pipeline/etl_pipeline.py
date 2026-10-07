import logging

from config import config
from src.pipeline.extractor import Extractor
from src.pipeline.transformer import Transformer
from src.pipeline.loader import Loader
from src.pipeline.database_loader import DatabaseLoader

# Set up logging
logger = logging.getLogger(__name__)
  

class ETLPipeline:
    """Orchestrate price data extraction, transformation, and loading."""
    
    def __init__(self, extractor: Extractor, transformer: Transformer, loader: Loader, db_loader: DatabaseLoader):
        self.extractor = extractor
        self.transformer = transformer
        self.loader = loader
        self.db_loader = db_loader


    def run(self, tickers: list[str] = None, sync_to_db: bool = True):
        """
        Execute the complete ETL workflow.
        
        Args:
            tickers: List of tickers to process. Defaults to config.INSTRUMENTS_LIST.
            
        Returns:
            Dictionary with ETL status and results.
        """
        if tickers is None:
            tickers = config.INSTRUMENTS_LIST
        
        logger.info("Starting ETL pipeline")
        
        try:
            raw = self.extractor.extract(tickers)
            
            if raw.empty:
                raise ValueError("Extraction returned no data")
            
            clean = self.transformer.transform(raw)
            result = self.loader.load(clean)
            
            if sync_to_db:
                self.db_loader.load_price_history_from_csv(str(config.PRICE_HISTORY_FILE))

            summary = {
                "status": ("success" if not result["errors"] else "partial"),
                "extracted": len(raw),
                "transformed": len(clean),
                "saved": result["saved"],
                "errors": result["errors"],
            }
            
            logger.info(f"ETL Summary: {summary}")
            
            return summary
            
        except Exception as e:
            logger.error("ETL pipeline failed", exc_info=True)
            
            return {
                "status": "failed",
                "extracted": 0,
                "transformed": 0,
                "saved": 0,
                "errors": [str(e)],
            }


if __name__ == "__main__":
    extractor = Extractor()
    transformer = Transformer()
    loader = Loader()
    db_loader = DatabaseLoader()
    
    # Price data pipeline
    etl_pipeline = ETLPipeline(extractor, transformer, loader, db_loader)
    price_result = etl_pipeline.run()
    print("Price ETL:", price_result)

