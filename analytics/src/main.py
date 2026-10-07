import argparse
from src.pipeline.etl_pipeline import ETLPipeline
from src.pipeline.metadata_pipeline import MetadataPipeline
from src.pipeline.extractor import Extractor
from src.pipeline.transformer import Transformer
from src.pipeline.loader import Loader
from src.pipeline.database_loader import DatabaseLoader
from src.analysis.analysis_helper import AnalysisHelper
from src.analysis.data_loader import DataLoader
from src.analysis.analysis_engine import AnalysisEngine
from src.reports.dashboard_report import DashBoardReport

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('command', choices=['run'])
    parser.add_argument('--tickers', nargs='+', default=None)
    parser.add_argument('--period', default='1y')
    
    args = parser.parse_args()
    
    # Create instances
    extractor = Extractor()
    transformer = Transformer()
    etl_loader = Loader()
    db_loader = DatabaseLoader()
    
    etl_pipeline = ETLPipeline(extractor, transformer, etl_loader, db_loader)
    metadata_pipeline = MetadataPipeline(db_loader)
    
    helper = AnalysisHelper()
    analysis_loader = DataLoader()
    engine = AnalysisEngine(analysis_loader, helper)
    
    dashboard = DashBoardReport(helper, engine)
    
    if args.command == 'run':
        tickers_result = metadata_pipeline.run()
        price_result = etl_pipeline.run()
        print("Tickers ETL:", tickers_result)
        print("Price ETL:", price_result)
        dashboard.run_dashboard(tickers=args.tickers, period=args.period)

        
if __name__ == '__main__':
    main()