import json
import logging
from datetime import datetime
from pathlib import Path
import pandas as pd

from config import config
from src.analysis import compute_insights, get_tickers_for_analysis

logger = logging.getLogger(__name__)


# ============================================================
# FORMATTING
# ============================================================
def generate_web_insights(summary_stats: pd.DataFrame) -> dict:
    """
    Generate structured statistical insights for use by a web application.

    Unlike the dashboard reporting module, this function does not generate
    human-readable prose. It returns structured values that can be rendered
    by any frontend.

    Args:
        summary_stats: DataFrame returned by compute_insights().

    Returns:
        Dictionary containing structured historical insights:
        - best_return
        - worst_return
        - highest_volatility
        - lowest_volatility
        - best_sharpe
        - risk_ranking
    """
    insights = {
        'best_return': None,
        'worst_return': None,
        'highest_volatility': None,
        'lowest_volatility': None,
        'best_sharpe': None,
        'risk_ranking': []
    }

    if summary_stats is None or summary_stats.empty:
        return insights

    stats = summary_stats.copy()

    for column in [
        'Annualized Return (%)',
        'Annualized Volatility (%)',
        'Sharpe Ratio'
    ]:
        if column in stats.columns:
            stats[column] = pd.to_numeric(stats[column], errors='coerce')

    if 'Annualized Return (%)' in stats.columns:
        valid = stats.dropna(subset=['Annualized Return (%)'])

        if not valid.empty:
            best_idx = valid['Annualized Return (%)'].idxmax()
            worst_idx = valid['Annualized Return (%)'].idxmin()

            insights['best_return'] = {
                'ticker': valid.loc[best_idx, 'Ticker'],
                'value': float(valid.loc[best_idx, 'Annualized Return (%)'])
            }

            insights['worst_return'] = {
                'ticker': valid.loc[worst_idx, 'Ticker'],
                'value': float(valid.loc[worst_idx, 'Annualized Return (%)'])
            }

    if 'Annualized Volatility (%)' in stats.columns:
        valid = stats.dropna(subset=['Annualized Volatility (%)'])

        if not valid.empty:
            highest_idx = valid['Annualized Volatility (%)'].idxmax()
            lowest_idx = valid['Annualized Volatility (%)'].idxmin()

            insights['highest_volatility'] = {
                'ticker': valid.loc[highest_idx, 'Ticker'],
                'value': float(valid.loc[highest_idx, 'Annualized Volatility (%)'])
            }

            insights['lowest_volatility'] = {
                'ticker': valid.loc[lowest_idx, 'Ticker'],
                'value': float(valid.loc[lowest_idx, 'Annualized Volatility (%)'])
            }

            ranking = valid[['Ticker', 'Annualized Volatility (%)']
                            ].sort_values(
                                'Annualized Volatility (%)',
                                ascending=False
                            )

            insights['risk_ranking'] = [
                {
                    'rank': rank,
                    'ticker': row['Ticker'],
                    'volatility': float(row['Annualized Volatility (%)'])
                }
                for rank, (_, row) in enumerate(
                    ranking.iterrows(),
                    start=1
                )
            ]

    if 'Sharpe Ratio' in stats.columns:
        valid = stats.dropna(subset=['Sharpe Ratio'])

        if not valid.empty:
            best_idx = valid['Sharpe Ratio'].idxmax()

            insights['best_sharpe'] = {
                'ticker': valid.loc[best_idx, 'Ticker'],
                'value': float(valid.loc[best_idx, 'Sharpe Ratio'])
            }

    return insights


# ============================================================
# WEB REPORT CREATION
# ============================================================
def create_web_data(summary_stats: pd.DataFrame, tickers: list, period: str) -> dict:
    """
    Create the complete structured data payload for the future web page.

    The returned dictionary is intentionally presentation-neutral. A web
    frontend can use the same data to create cards, tables, charts, filters,
    or other UI components.

    Args:
        summary_stats: DataFrame returned by compute_insights().
        tickers: List of analyzed ticker symbols.
        period: Analysis period.

    Returns:
        Dictionary containing:
        - metadata
        - statistics
        - insights
        - chart information

    Example structure:
        {
            "metadata": {...},
            "statistics": [...],
            "insights": {...},
            "charts": [...]
        }
    """
    statistics = []

    if summary_stats is not None and not summary_stats.empty:
        stats = summary_stats.copy()

        statistics = stats.to_dict(orient='records')

    chart_data = [
        {
            'type': 'correlation',
            'title': 'Correlation Matrix',
            'description': 'Historical correlation between asset returns.',
            'period': period,
            'tickers': tickers
        },
        {
            'type': 'volatility',
            'title': 'Rolling Volatility',
            'description': 'Historical rolling volatility by asset.',
            'period': period,
            'tickers': tickers
        },
        {
            'type': 'asset_class_volatility',
            'title': 'Asset Class Volatility',
            'description': 'Historical volatility comparison by asset class.',
            'period': period,
            'tickers': tickers
        }
    ]

    return {
        'metadata': {
            'generated_at': datetime.now().isoformat(),
            'period': period,
            'tickers': tickers,
            'ticker_count': len(tickers)
        },
        'statistics': statistics,
        'insights': generate_web_insights(summary_stats),
        'charts': chart_data
    }


# ============================================================
# WEB REPORT SAVE
# ============================================================
def save_web_data(web_data: dict, tickers: list, period: str) -> Path:
    """
    Save structured web dashboard data as a JSON file.

    The JSON file is intended to act as a data source for a future web
    application. It contains no terminal formatting and no presentation-
    specific text.

    Args:
        web_data: Dictionary returned by create_web_data().
        tickers: List of analyzed tickers.
        period: Analysis period.

    Returns:
        Path to the saved JSON file.
    """
    config.REPORTS_DIR.mkdir(parents=True, exist_ok=True)

    ticker_label = "-".join(tickers)
    filename = f"web_data_{ticker_label}_{period.upper()}.json"

    output_path = config.REPORTS_DIR / filename

    with open(output_path, 'w', encoding='utf-8') as file:
        json.dump(
            web_data,
            file,
            indent=4,
            default=str
        )

    logger.info(f"Web data saved to: {output_path}")

    return output_path


def generate_web_report(
    tickers: list = None,
    asset_classes: list = None,
    period: str = '1y',
    volatility_window: int = 30,
    save_file: bool = True
) -> dict:
    """
    Run analysis and generate structured data for a web application.

    This is the main entry point for the web reporting layer.
    The function:
    1. Determines the tickers to analyze.
    2. Runs compute_insights().
    3. Converts the results into structured web data.
    4. Optionally saves the data as JSON.

    It does not print a human-readable report and does not generate
    presentation-specific text.

    Args:
        tickers: Optional list of ticker symbols.
        asset_classes: Optional list of asset classes.
        period: Analysis period such as '1y', '5y', '10y', or 'all'.
        volatility_window: Rolling volatility window in trading days.
        save_file: Whether to save the JSON output.

    Returns:
        Dictionary containing:
        - status
        - web_data
        - json_path
    """
    logger.info("=" * 80)
    logger.info("STARTING WEB DATA GENERATION")
    logger.info("=" * 80)

    try:
        analysis_tickers = get_tickers_for_analysis(tickers=tickers, asset_classes=asset_classes)

        if not analysis_tickers:
            logger.error("No tickers selected")

            return {
                'status': 'failed',
                'web_data': None,
                'json_path': None,
                'error': 'No tickers selected'
            }

        logger.info(f"Generating web data for: {', '.join(analysis_tickers)}")

        summary_stats = compute_insights(
            tickers=analysis_tickers,
            period=period,
            volatility_window=volatility_window
        )

        web_data = create_web_data(
            summary_stats=summary_stats,
            tickers=analysis_tickers,
            period=period
        )

        json_path = None

        if save_file:
            json_path = save_web_data(
                web_data=web_data,
                tickers=analysis_tickers,
                period=period
            )

        logger.info("WEB DATA GENERATION COMPLETE")

        return {
            'status': 'success',
            'web_data': web_data,
            'json_path': json_path
        }

    except Exception as e:
        logger.error(f"Web data generation failed: {str(e)}", exc_info=True)

        return {
            'status': 'failed',
            'web_data': None,
            'json_path': None,
            'error': str(e)
        }


if __name__ == "__main__":
    result = generate_web_report(
        tickers=['AAPL', 'BND', 'SPY'],
        period='1y',
        volatility_window=30,
        save_file=True
    )

    if result['status'] == 'success':
        print(f"Web data saved to: {result['json_path']}")
