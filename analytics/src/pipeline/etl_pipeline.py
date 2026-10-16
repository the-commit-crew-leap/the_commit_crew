import csv
import logging
from pathlib import Path

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


    def generate_price_history_seed(self, price_history_path: Path = None) -> None:
        """Generate the database seed from the merged price-history CSV."""
        price_history_path = price_history_path or config.PRICE_HISTORY_FILE
        rows = []

        with price_history_path.open(newline="") as price_file:
            for row in csv.DictReader(price_file):
                rows.append(
                    (
                        row["symbol"].upper(),
                        row["date"],
                        row["open"],
                        row["high"],
                        row["low"],
                        row["close"],
                        row["volume"],
                    )
                )

        rows.sort(key=lambda row: (row[0], row[1]))
        sql_lines = [
            "-- Auto-generated from price_history.csv",
            "-- Do not edit manually; regenerate by running the analytics pipeline.",
            "",
            "INSERT INTO price_history (symbol, price_date, open_price, high_price, low_price, close_price, volume) VALUES",
        ]

        for index, row in enumerate(rows):
            comma = "," if index < len(rows) - 1 else ""
            symbol, price_date, open_price, high_price, low_price, close_price, volume = row
            sql_lines.append(
                f"    ('{symbol}', '{price_date}', {open_price}, {high_price}, {low_price}, {close_price}, {volume}){comma}"
            )

        sql_lines.extend(["ON CONFLICT (symbol, price_date) DO NOTHING;", ""])
        config.PRICE_HISTORY_SEED_FILE.parent.mkdir(parents=True, exist_ok=True)
        config.PRICE_HISTORY_SEED_FILE.write_text("\n".join(sql_lines))
        logger.info("Generated price-history seed at %s", config.PRICE_HISTORY_SEED_FILE)

    def run(self, symbols: list[str] = None):
        """
        Execute the complete ETL workflow.
        
        Args:
            symbols: List of instrument symbols to process. Defaults to config.INSTRUMENTS_LIST.
            
        Returns:
            Dictionary with ETL status and results.
        """
        if symbols is None:
            symbols = config.INSTRUMENTS_LIST
        
        logger.info("Starting ETL pipeline")
        
        try:
            raw = self.extractor.extract(symbols)
            
            if raw.empty:
                raise ValueError("Extraction returned no data")
            
            clean = self.transformer.transform(raw)
            result = self.loader.load(clean)
            if not result["errors"]:
                self.generate_price_history_seed(config.ANALYTICS_PRICE_HISTORY_FILE)

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
    price_result = etl_pipeline.run()
    print("Price ETL:", price_result)

