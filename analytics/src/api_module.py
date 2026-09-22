from typing import List, Optional
import logging

from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import FileResponse
import uvicorn

from analytics.src.analysis.analysis_helper import AnalysisHelper
from analytics.src.analysis.data_loader import DataLoader
from analytics.src.analysis.analysis_engine import AnalysisEngine
from analytics.src.reports.web_report import WebReport
from config import config

logger = logging.getLogger(__name__)


app = FastAPI(title="Portfolio Analytics API")
helper = AnalysisHelper()
loader = DataLoader()
analysis_engine = AnalysisEngine(data_loader=loader, helper=helper)
web_report = WebReport(helper=helper, analysis_engine=analysis_engine)


# HEALTH
@app.get("/health")
def health_check():
    return {
        "status": "ok"
    }


# INSIGHTS
@app.get("/insights")
def get_insights(
    tickers: Optional[List[str]] = Query(default=None),
    asset_classes: Optional[List[str]] = Query(default=None),
    period: str = "1y",
    volatility_window: int = 30
):
    """
    Generate portfolio statistics and structured web insights.

    Example:
        /insights?tickers=AAPL&tickers=SPY&period=1y
    """
    result = web_report.generate_web_report(
        tickers=tickers,
        asset_classes=asset_classes,
        period=period,
        volatility_window=volatility_window,
        save_file=False,
        generate_plots=False,
    )

    if result["status"] != "success":
        raise HTTPException(status_code=400, detail=result.get("error", "Analysis failed"))

    return result["web_data"]


# ANALYSIS
@app.post("/analysis")
def run_analysis(
    tickers: Optional[List[str]] = Query(default=None),
    asset_classes: Optional[List[str]] = Query(default=None),
    period: str = "1y",
    volatility_window: int = 30
):
    """
    Run the complete analysis, including statistics and plot generation.

    This endpoint generates the PNG files but does not save the JSON
    web-report file.
    """
    result = web_report.generate_web_report(
        tickers=tickers,
        asset_classes=asset_classes,
        period=period,
        volatility_window=volatility_window,
        save_file=False,
        generate_plots=True,
    )

    if result["status"] != "success":
        raise HTTPException(status_code=400, detail=result.get("error", "Analysis failed"))

    return result["web_data"]


# CHARTS
@app.get("/charts/correlation")
def get_correlation_chart(
    tickers: Optional[List[str]] = Query(default=None),
    asset_classes: Optional[List[str]] = Query(default=None),
    period: str = "1y"
):
    """
    Generate and return the correlation heatmap.
    """
    selected_tickers = helper.get_tickers_for_analysis(tickers=tickers, asset_classes=asset_classes)

    if not selected_tickers:
        raise HTTPException(status_code=400, detail="No tickers selected")

    start_date, end_date = helper.get_date_range(period)
    ticker_label = helper.get_ticker_label(selected_tickers)
    filename = (f"correlation_{ticker_label}_{period.upper()}.png")

    analysis_engine.plot_correlation_heatmap(selected_tickers, start_date, end_date, period.upper())

    path = config.CHARTS_DIR / filename
    if not path.exists():
        raise HTTPException(status_code=500, detail="Correlation chart was not generated")

    # Saves image to folder
    return FileResponse(path=path, media_type="image/png", filename=filename)


@app.get("/charts/volatility")
def get_volatility_chart(
    tickers: Optional[List[str]] = Query(default=None),
    asset_classes: Optional[List[str]] = Query(default=None),
    period: str = "1y",
    volatility_window: int = 30
):
    """
    Generate and return the rolling volatility chart.
    """
    if volatility_window < 2:
        raise HTTPException(status_code=400, detail="Volatility window must be at least 2")

    selected_tickers = helper.get_tickers_for_analysis(tickers=tickers,asset_classes=asset_classes)

    if not selected_tickers:
        raise HTTPException(status_code=400, detail="No tickers selected")

    start_date, end_date = helper.get_date_range(period)
    ticker_label = helper.get_ticker_label(selected_tickers)
    filename = (f"rolling_volatility_{volatility_window}d_{ticker_label}_{period.upper()}.png")

    analysis_engine.plot_volatility_trends(selected_tickers, start_date, end_date, period.upper(), window=volatility_window)

    path = config.CHARTS_DIR / filename
    if not path.exists():
        raise HTTPException(status_code=500, detail="Volatility chart was not generated")

    return FileResponse(path=path, media_type="image/png", filename=filename)


@app.get("/charts/asset-class-volatility")
def get_asset_class_volatility_chart(
    tickers: Optional[List[str]] = Query(default=None),
    asset_classes: Optional[List[str]] = Query(default=None),
    period: str = "1y"
):
    """
    Generate and return the asset-class volatility chart.
    """
    selected_tickers = helper.get_tickers_for_analysis(tickers=tickers, asset_classes=asset_classes)

    if not selected_tickers:
        raise HTTPException(status_code=400, detail="No tickers selected")

    start_date, end_date = helper.get_date_range(period)
    ticker_label = helper.get_ticker_label(selected_tickers)
    filename = (f"asset_class_volatility_{ticker_label}_{period.upper()}.png")
    
    analysis_engine.plot_asset_class_volatility(selected_tickers, start_date, end_date,period.upper())

    path = config.CHARTS_DIR / filename
    if not path.exists():
        raise HTTPException(status_code=500, detail="Asset-class volatility chart was not generated")

    return FileResponse(path=path, media_type="image/png", filename=filename)


if __name__ == "__main__":
    uvicorn.run("src.api_module:app", host="127.0.0.1", port=8000, reload=True)

