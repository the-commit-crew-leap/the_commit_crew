import json
import logging
import os
from typing import List, Optional

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
import uvicorn

from src.analysis.analysis_helper import AnalysisHelper
from src.analysis.data_loader import DataLoader
from src.analysis.analysis_engine import AnalysisEngine
from src.reports.web_report import WebReport

logger = logging.getLogger(__name__)


app = FastAPI(title="Portfolio Analytics API")
cors_origins = [
    origin.strip()
    for origin in os.getenv(
        "ANALYTICS_CORS_ORIGINS",
        "http://localhost:8086,http://127.0.0.1:8086",
    ).split(",")
    if origin.strip()
]
cors_origin_regex = os.getenv(
    "ANALYTICS_CORS_ORIGIN_REGEX",
    r"https?://10\.9\.\d{1,3}\.\d{1,3}:8086",
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=cors_origins,
    allow_origin_regex=cors_origin_regex,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

helper = AnalysisHelper()
loader = DataLoader()
analysis_engine = AnalysisEngine(data_loader=loader, helper=helper)
web_report = WebReport(helper=helper, analysis_engine=analysis_engine)

SUPPORTED_PERIODS = {"1w", "1m", "6m", "1y", "5y", "10y", "all"}


def _validate_period(period: str) -> str:
    """Validate a period before invoking the reporting layer."""
    if period not in SUPPORTED_PERIODS:
        raise HTTPException(
            status_code=400,
            detail=f"Unknown period '{period}'. Use one of: 1w, 1m, 6m, 1y, 5y, 10y.",
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


def _figure_response(figure) -> JSONResponse:
    """Serialize a Plotly figure for browser-side rendering."""
    if figure is None:
        raise HTTPException(status_code=404, detail="No chart data available")
    return JSONResponse(content=json.loads(figure.to_json()))


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

    This endpoint generates chart data for the UI but does not save files.
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

    analysis_period = analysis_engine._resolve_period(period, selected_tickers)
    start_date, end_date = analysis_period.start_date, analysis_period.end_date
    figure = analysis_engine.create_correlation_figure(
        selected_tickers,
        start_date,
        end_date,
        period.upper(),
    )
    return _figure_response(figure)


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

    analysis_period = analysis_engine._resolve_period(period, selected_tickers)
    start_date, end_date = analysis_period.start_date, analysis_period.end_date
    figure = analysis_engine.create_volatility_figure(
        selected_tickers,
        start_date,
        end_date,
        period.upper(),
        window=volatility_window,
    )
    return _figure_response(figure)


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

    analysis_period = analysis_engine._resolve_period(period, selected_tickers)
    start_date, end_date = analysis_period.start_date, analysis_period.end_date
    figure = analysis_engine.create_asset_class_volatility_figure(
        selected_tickers,
        start_date,
        end_date,
        period.upper(),
    )
    return _figure_response(figure)


@app.get("/charts/drawdown")
def get_drawdown_chart(
    tickers: Optional[List[str]] = Query(default=None),
    asset_classes: Optional[List[str]] = Query(default=None),
    period: str = "1y"
):
    """Generate and return the drawdown chart."""
    selected_tickers = _select_tickers(tickers, asset_classes)
    period = _validate_period(period)

    analysis_period = analysis_engine._resolve_period(period, selected_tickers)
    start_date, end_date = analysis_period.start_date, analysis_period.end_date
    figure = analysis_engine.create_drawdown_figure(
        selected_tickers,
        start_date,
        end_date,
        period.upper(),
    )
    return _figure_response(figure)

if __name__ == "__main__":
    uvicorn.run("src.api_module:app", host="0.0.0.0", port=8090)

