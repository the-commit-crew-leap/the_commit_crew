import logging
from datetime import datetime
from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger

from config import config
from src.pipeline.etl_pipeline import ETLPipeline, Extractor, Transformer, Loader

logger = logging.getLogger(__name__)


class ETLScheduler:
    """Manages scheduled ETL pipeline execution."""
    
    def __init__(self):
        """Initialize the scheduler with background execution."""
        self.scheduler = BackgroundScheduler()
        self.pipeline = None
        

    def _create_pipeline(self):
        """Instantiate the ETL pipeline with components."""
        if self.pipeline is None:
            extractor = Extractor()
            transformer = Transformer()
            loader = Loader()
            self.pipeline = ETLPipeline(extractor, transformer, loader)
        return self.pipeline
    
    
    def _run_etl_job(self):
        """Execute the ETL pipeline as a scheduled job."""
        try:
            logger.info("Starting scheduled ETL run at %s", datetime.now())
            pipeline = self._create_pipeline()
            result = pipeline.run_etl(tickers=config.INSTRUMENTS_LIST)
            
            status = result.get("status", "unknown")
            extracted = result.get("extracted", 0)
            saved = result.get("saved", 0)
            errors = result.get("errors", [])
            
            logger.info(
                "ETL job complete: status=%s, extracted=%d, saved=%d, errors=%s",
                status, extracted, saved, errors
            )
            
        except Exception as e:
            logger.error("ETL job failed: %s", str(e), exc_info=True)
    
    
    def start(self):
        """Start the scheduler if enabled in configuration."""
        if not config.ENABLE_SCHEDULER:
            logger.info("Scheduler is disabled (ENABLE_SCHEDULER=False)")
            return
        
        try:
            # Parse schedule time (format: "HH:MM")
            hour, minute = map(int, config.SCHEDULE_TIME.split(":"))
            
            # Add job: run daily at specified time
            self.scheduler.add_job(
                self._run_etl_job,
                trigger=CronTrigger(hour=hour, minute=minute),
                id="etl_daily",
                name="Daily ETL Pipeline",
                replace_existing=True,
            )
            
            self.scheduler.start()
            logger.info(
                "ETL scheduler started. Will run daily at %s",
                config.SCHEDULE_TIME,
            )
            
        except Exception as e:
            logger.error("Failed to start scheduler: %s", str(e), exc_info=True)
            raise
    
    
    def stop(self):
        """Stop the scheduler gracefully."""
        if self.scheduler.running:
            self.scheduler.shutdown(wait=True)
            logger.info("ETL scheduler stopped")


# Global scheduler instance
_scheduler_instance = None


def get_scheduler() -> ETLScheduler:
    """Get or create the global scheduler instance."""
    global _scheduler_instance
    if _scheduler_instance is None:
        _scheduler_instance = ETLScheduler()
    return _scheduler_instance


def start_scheduler():
    """Convenience function to start the global scheduler."""
    scheduler = get_scheduler()
    scheduler.start()


def stop_scheduler():
    """Convenience function to stop the global scheduler."""
    scheduler = get_scheduler()
    scheduler.stop()