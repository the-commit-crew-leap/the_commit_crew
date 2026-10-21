import base64
import json
import logging
import math
import os
from datetime import date
from typing import List, Optional

import numpy as np
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
    payload = _expand_binary_arrays(json.loads(figure.to_json()))
    return JSONResponse(content=payload)


def _empty_figure_response() -> JSONResponse:
    """Return an empty chart when a portfolio has no points in the requested range."""
    return JSONResponse(content={"data": [], "layout": {}})


def _expand_binary_arrays(value):
    """Convert Plotly's binary NumPy arrays into lists supported by Plotly.js."""
    if isinstance(value, dict):
        if set(value) == {"dtype", "bdata"}:
            decoded = base64.b64decode(value["bdata"])
            return np.frombuffer(decoded, dtype=np.dtype(value["dtype"])).tolist()
        return {key: _expand_binary_arrays(item) for key, item in value.items()}
    if isinstance(value, list):
        return [_expand_binary_arrays(item) for item in value]
    if isinstance(value, float) and not math.isfinite(value):
        return None
    return value


def _portfolio_inputs(
    symbols: Optional[List[str]],
    weights: Optional[List[float]],
) -> tuple[list[str], list[float]]:
    """Validate the holdings snapshot supplied by the authenticated UI."""
    if not symbols or not weights or len(symbols) != len(weights):
        raise HTTPException(status_code=400, detail="Portfolio symbols and weights are required")
    if any(weight <= 0 for weight in weights) or sum(weights) <= 0:
        raise HTTPException(status_code=400, detail="Portfolio weights must be positive")
    return symbols, weights


def _portfolio_period(period: str, symbols: list[str]) -> None:
    _validate_period(period)
    if not symbols:
        raise HTTPException(status_code=400, detail="Portfolio has no holdings")


def _portfolio_acquisition_dates(
    symbols: list[str],
    acquisition_dates: Optional[List[str]],
) -> dict[str, str]:
    if acquisition_dates is None:
        return {}
    if len(acquisition_dates) != len(symbols):
        raise HTTPException(status_code=400, detail="Portfolio acquisition dates must match symbols")
    try:
        parsed_dates = [date.fromisoformat(value) for value in acquisition_dates]
    except ValueError as error:
        raise HTTPException(status_code=400, detail="Portfolio acquisition dates must use YYYY-MM-DD") from error
    return {
        helper.normalize_ticker(symbol): parsed_date.isoformat()
        for symbol, parsed_date in zip(symbols, parsed_dates)
    }


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


@app.get("/charts/candlestick")
def get_candlestick_chart(
    ticker: str,
    period: str = "1y",
):
    """Generate a candlestick chart for exactly one instrument."""
    period = _validate_period(period)
    selected_ticker = _select_tickers([ticker], None)
    analysis_period = analysis_engine._resolve_period(period, selected_ticker)
    figure = analysis_engine.create_candlestick_figure(
        selected_ticker[0],
        analysis_period.start_date,
        analysis_period.end_date,
        period.upper(),
    )
    return _figure_response(figure)


@app.get("/portfolio/insights")
def get_portfolio_insights(
    symbols: Optional[List[str]] = Query(default=None),
    weights: Optional[List[float]] = Query(default=None),
    period: str = "1y",
    volatility_window: int = Query(default=30, ge=2),
    acquisition_dates: Optional[List[str]] = Query(default=None),
):
    """Return weighted statistics for the user's current holdings snapshot."""
    symbols, weights = _portfolio_inputs(symbols, weights)
    _portfolio_period(period, symbols)
    dates = _portfolio_acquisition_dates(symbols, acquisition_dates)
    statistics = analysis_engine.get_portfolio_statistics(symbols, weights, period, volatility_window, dates)
    return {
        "period": period,
        "symbols": symbols,
        "weights": weights,
        "statistics": statistics.to_dict(orient="records"),
    }


@app.get("/portfolio/charts/cumulative")
def get_portfolio_cumulative_chart(
    symbols: Optional[List[str]] = Query(default=None),
    weights: Optional[List[float]] = Query(default=None),
    period: str = "1y",
    acquisition_dates: Optional[List[str]] = Query(default=None),
):
    symbols, weights = _portfolio_inputs(symbols, weights)
    _portfolio_period(period, symbols)
    dates = _portfolio_acquisition_dates(symbols, acquisition_dates)
    figure = analysis_engine.create_portfolio_cumulative_figure(symbols, weights, period, dates)
    return _figure_response(figure) if figure is not None else _empty_figure_response()


@app.get("/portfolio/charts/volatility")
def get_portfolio_volatility_chart(
    symbols: Optional[List[str]] = Query(default=None),
    weights: Optional[List[float]] = Query(default=None),
    period: str = "1y",
    volatility_window: int = Query(default=30, ge=2),
    acquisition_dates: Optional[List[str]] = Query(default=None),
):
    symbols, weights = _portfolio_inputs(symbols, weights)
    _portfolio_period(period, symbols)
    dates = _portfolio_acquisition_dates(symbols, acquisition_dates)
    figure = analysis_engine.create_portfolio_volatility_figure(
        symbols, weights, period, volatility_window, dates
    )
    return _figure_response(figure) if figure is not None else _empty_figure_response()


@app.get("/portfolio/charts/drawdown")
def get_portfolio_drawdown_chart(
    symbols: Optional[List[str]] = Query(default=None),
    weights: Optional[List[float]] = Query(default=None),
    period: str = "1y",
    acquisition_dates: Optional[List[str]] = Query(default=None),
):
    symbols, weights = _portfolio_inputs(symbols, weights)
    _portfolio_period(period, symbols)
    dates = _portfolio_acquisition_dates(symbols, acquisition_dates)
    figure = analysis_engine.create_portfolio_drawdown_figure(symbols, weights, period, dates)
    return _figure_response(figure) if figure is not None else _empty_figure_response()


@app.get("/portfolio/charts/allocation")
def get_portfolio_allocation_chart(
    symbols: Optional[List[str]] = Query(default=None),
    weights: Optional[List[float]] = Query(default=None),
):
    symbols, weights = _portfolio_inputs(symbols, weights)
    return _figure_response(analysis_engine.create_portfolio_allocation_figure(symbols, weights))


@app.get("/portfolio/scenario")
def get_portfolio_scenario(
    symbols: Optional[List[str]] = Query(default=None),
    values: Optional[List[float]] = Query(default=None),
    scenario_symbol: str = Query(...),
    additional_quantity: int = Query(..., gt=0),
    cash_value: float = Query(default=0.0, ge=0),
    period: str = "1y",
    volatility_window: int = Query(default=30, ge=2),
    acquisition_dates: Optional[List[str]] = Query(default=None),
):
    """Compare the current portfolio with a proposed additional purchase."""
    if not symbols or not values or len(symbols) != len(values):
        raise HTTPException(status_code=400, detail="Portfolio symbols and values are required")
    _portfolio_period(period, symbols)
    dates = _portfolio_acquisition_dates(symbols, acquisition_dates)
    try:
        return analysis_engine.calculate_portfolio_scenario(
            symbols,
            values,
            scenario_symbol,
            additional_quantity,
            period,
            cash_value,
            volatility_window,
            dates,
        )
    except ValueError as error:
        raise HTTPException(status_code=400, detail=str(error)) from error

if __name__ == "__main__":
    uvicorn.run("src.api_module:app", host="0.0.0.0", port=8090)

