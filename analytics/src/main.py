import argparse
from config import config
from src.pipeline.etl_pipeline import ETLPipeline
from src.pipeline.metadata_pipeline import MetadataPipeline
from src.pipeline.extractor import Extractor
from src.pipeline.transformer import Transformer
from src.pipeline.loader import Loader
from src.analysis.analysis_helper import AnalysisHelper
from src.analysis.data_loader import DataLoader
from src.analysis.analysis_engine import AnalysisEngine
from src.reports.dashboard_report import DashBoardReport

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('command', choices=['run'])
    parser.add_argument('--symbols', nargs='+', default=None)
    parser.add_argument('--period', default='1y')
    
    args = parser.parse_args()
    
    # Create instances
    extractor = Extractor()
    transformer = Transformer()
    etl_loader = Loader()
    
    etl_pipeline = ETLPipeline(extractor, transformer, etl_loader)
    metadata_pipeline = MetadataPipeline()
    
    helper = AnalysisHelper()
    analysis_loader = DataLoader()
    engine = AnalysisEngine(analysis_loader, helper)
    
    dashboard = DashBoardReport(helper, engine)
    
    if args.command == 'run':
        symbols = args.symbols or config.INSTRUMENTS_LIST
        metadata_result = metadata_pipeline.run(symbols=symbols)
        price_result = etl_pipeline.run(symbols=symbols)
        print("Symbols ETL:", metadata_result)
        print("Price ETL:", price_result)
        dashboard.run_dashboard(tickers=symbols, period=args.period)

        
if __name__ == '__main__':
    main()