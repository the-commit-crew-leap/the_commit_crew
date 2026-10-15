from typing import List, Optional
import logging

from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import FileResponse
import uvicorn

from src.analysis.analysis_helper import AnalysisHelper
from src.analysis.data_loader import DataLoader
from src.analysis.analysis_engine import AnalysisEngine
from src.reports.web_report import WebReport
from config import config

logger = logging.getLogger(__name__)


app = FastAPI(title="Portfolio Analytics API")
helper = AnalysisHelper()
loader = DataLoader()
analysis_engine = AnalysisEngine(data_loader=loader, helper=helper)
web_report = WebReport(helper=helper, analysis_engine=analysis_engine)

SUPPORTED_PERIODS = {"1y", "5y", "10y", "all"}


def _validate_period(period: str) -> str:
    """Validate a period before invoking the reporting layer."""
    if period not in SUPPORTED_PERIODS:
        raise HTTPException(
            status_code=400,
            detail=f"Unknown period '{period}'. Use one of: 1y, 5y, 10y, all.",
        )
    return period


def _select_tickers(
    tickers: Optional[List[str]],
    asset_classes: Optional[List[str]],
) -> List[str]:
    """Resolve and validate the symbols requested by a chart endpoint."""
    selected = helper.get_tickers_for_analysis(
        tickers=tickers,
        asset_classes=asset_classes,
    )
    if not selected:
        raise HTTPException(status_code=400, detail="No tickers selected")
    return selected


def _chart_response(
    chart_path: str,
    filename: str,
) -> FileResponse:
    """Return a generated HTML chart or a useful server error."""
    path = config.CHARTS_DIR / chart_path
    if not path.exists():
        raise HTTPException(status_code=500, detail="Chart was not generated")
    return FileResponse(path=path, media_type="text/html", filename=filename)


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
    volatility_window: int = Query(default=30, ge=2)
):
    """
    Generate portfolio statistics and structured web insights.

    Example:
        /insights?tickers=AAPL&tickers=SPY&period=1y
    """
    _validate_period(period)
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
    volatility_window: int = Query(default=30, ge=2)
):
    """
    Run the complete analysis, including statistics and plot generation.

    This endpoint generates the PNG files but does not save the JSON
    web-report file.
    """
    _validate_period(period)
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
    selected_tickers = _select_tickers(tickers, asset_classes)
    period = _validate_period(period)

    start_date, end_date = helper.get_date_range(period)
    ticker_label = helper.get_ticker_label(selected_tickers)
    filename = f"correlation_{ticker_label}_{period.upper()}.html"

    analysis_engine.plot_correlation_heatmap(selected_tickers, start_date, end_date, period.upper())

    return _chart_response(f"correlation/{filename}", filename)


@app.get("/charts/volatility")
def get_volatility_chart(
    tickers: Optional[List[str]] = Query(default=None),
    asset_classes: Optional[List[str]] = Query(default=None),
    period: str = "1y",
    volatility_window: int = Query(default=30, ge=2)
):
    """
    Generate and return the rolling volatility chart.
    """
    selected_tickers = _select_tickers(tickers, asset_classes)
    period = _validate_period(period)

    start_date, end_date = helper.get_date_range(period)
    ticker_label = helper.get_ticker_label(selected_tickers)
    filename = f"rolling_volatility_{volatility_window}d_{ticker_label}_{period.upper()}.html"

    analysis_engine.plot_volatility_trends(selected_tickers, start_date, end_date, period.upper(), window=volatility_window)

    return _chart_response(f"rolling_volatility/{filename}", filename)


@app.get("/charts/asset-class-volatility")
def get_asset_class_volatility_chart(
    tickers: Optional[List[str]] = Query(default=None),
    asset_classes: Optional[List[str]] = Query(default=None),
    period: str = "1y"
):
    """
    Generate and return the asset-class volatility chart.
    """
    selected_tickers = _select_tickers(tickers, asset_classes)
    period = _validate_period(period)

    start_date, end_date = helper.get_date_range(period)
    ticker_label = helper.get_ticker_label(selected_tickers)
    filename = f"asset_class_volatility_{ticker_label}_{period.upper()}.html"
    
    analysis_engine.plot_asset_class_volatility(selected_tickers, start_date, end_date,period.upper())

    return _chart_response(f"asset_class_volatility/{filename}", filename)

if __name__ == "__main__":
    uvicorn.run("src.api_module:app", host="0.0.0.0", port=8090)

