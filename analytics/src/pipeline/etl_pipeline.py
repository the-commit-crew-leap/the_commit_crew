import logging

from config import config
from src.pipeline.extractor import Extractor
from src.pipeline.transformer import Transformer
from src.pipeline.loader import Loader

# Set up logging
logger = logging.getLogger(__name__)
  

class ETLPipeline:
    """Orchestrate price data extraction, transformation, and loading."""
    
    def __init__(self, extractor: Extractor, transformer: Transformer, loader: Loader):
        self.extractor = extractor
        self.transformer = transformer
        self.loader = loader


    def run_etl(self, tickers: list[str] = None):
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
    
    # Price data pipeline
    etl_pipeline = ETLPipeline(extractor, transformer, loader)
    price_result = etl_pipeline.run_etl()
    print("Price ETL:", price_result)

