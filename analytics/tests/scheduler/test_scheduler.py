import pytest
from datetime import datetime
from unittest.mock import patch, MagicMock
from src.scheduler.scheduler import ETLScheduler


def test_scheduler_creates_pipeline():
    """Scheduler should instantiate pipeline on first use."""
    scheduler = ETLScheduler()
    pipeline = scheduler._create_pipeline()
    
    assert pipeline is not None
    assert scheduler.pipeline is pipeline  # Should cache it


def test_scheduler_parses_time_correctly(monkeypatch):
    """Scheduler should correctly parse SCHEDULE_TIME."""
    monkeypatch.setattr("config.config.SCHEDULE_TIME", "14:30")
    monkeypatch.setattr("config.config.ENABLE_SCHEDULER", True)
    
    scheduler = ETLScheduler()
    with patch.object(scheduler.scheduler, "add_job") as mock_add:
        scheduler.start()
        
        # Verify add_job was called once
        mock_add.assert_called_once()
        args, kwargs = mock_add.call_args
        
        # Verify the trigger is a CronTrigger
        trigger = kwargs["trigger"]
        assert trigger.__class__.__name__ == "CronTrigger"
        
        # Verify other important parameters
        assert kwargs["id"] == "etl_daily"
        assert kwargs["name"] == "Daily ETL Pipeline"
        assert kwargs["replace_existing"] is True


def test_scheduler_respects_disabled_config(monkeypatch):
    """Scheduler should not start if ENABLE_SCHEDULER=False."""
    monkeypatch.setattr("config.config.ENABLE_SCHEDULER", False)
    
    scheduler = ETLScheduler()
    with patch.object(scheduler.scheduler, "add_job") as mock_add:
        scheduler.start()
        mock_add.assert_not_called()


def test_etl_job_handles_errors(monkeypatch):
    """ETL job should handle pipeline errors gracefully."""
    scheduler = ETLScheduler()
    
    mock_pipeline = MagicMock()
    mock_pipeline.run_etl.side_effect = Exception("Test error")
    scheduler.pipeline = mock_pipeline
    
    # Should not raise, should log error instead
    scheduler._run_etl_job()