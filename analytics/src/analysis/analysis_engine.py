import logging
from dataclasses import dataclass
from typing import Optional

import numpy as np
import pandas as pd
import plotly.graph_objects as go

from src.analysis.analysis_helper import AnalysisHelper
from src.analysis.data_loader import DataLoader
from src.analysis.statistics_calculator import StatisticsCalculator

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class AnalysisPeriod:
    """Resolved date range used by one analysis run."""

    label: str
    start_date: str
    end_date: str


class AnalysisEngine:
    
    def __init__(self, data_loader: DataLoader, helper: AnalysisHelper):
        self.loader = data_loader
        self.helper = helper
        self.statistics_calculator = StatisticsCalculator(helper)

    def _resolve_period(self, period: str, symbols: Optional[list[str]] = None) -> AnalysisPeriod:
        """Resolve a period name once for the complete analysis run."""
        latest_date = self.loader.get_latest_date(symbols or [])
        if latest_date is None:
            start_date, end_date = self.helper.get_date_range(period)
        else:
            end_date = latest_date.strftime("%Y-%m-%d")
            start_date = self.helper.get_date_range(period, end_date=end_date)[0]
        return AnalysisPeriod(period.upper(), start_date, end_date)

    def _chart_labels(self, symbols: list) -> tuple[str, str]:
        """Return filesystem and human-readable labels for symbols."""
        return (
            self.helper.get_ticker_label(symbols),
            self.helper.get_ticker_title(symbols),
        )


    # RETURN CALCULATIONS
    def get_returns_for_tickers(self, tickers: list, start_date: str, end_date: str) -> pd.DataFrame:
        """
        Calculate daily close-to-close returns for multiple instruments.
        Adjusted closing price is used when available. Otherwise, regular
        closing price is used.

        The function loads seven additional calendar days before start_date.
        This is necessary because the first requested trading day's return
        requires the previous trading day's price.

        Args:
            tickers: List of ticker symbols.

            start_date: Inclusive analysis start date.

            end_date: Inclusive analysis end date.

        Returns:
            DataFrame indexed by date with one column per ticker.
            Each value represents that ticker's daily return.

            An empty DataFrame is returned if none of the requested tickers
            have usable data.
        """
        returns_data = {}
        requested_start = pd.Timestamp(start_date)
        requested_end = pd.Timestamp(end_date)

        if requested_start > requested_end:
            raise ValueError("start_date must be before or equal to end_date")

        data_start = requested_start - pd.Timedelta(days=7)

        for symbol in tickers:
            symbol = self.helper.normalize_ticker(ticker=symbol)
            price_df = self.loader.load_price_data(symbol, data_start.strftime("%Y-%m-%d"), end_date)

            if price_df.empty:
                logger.warning(f"No price data available for {symbol}")
                continue

            # Prefer adjusted close only when it actually contains data.
            if "adj_close" in price_df.columns:
                adjusted = pd.to_numeric(price_df["adj_close"], errors="coerce")
            else:
                adjusted = pd.Series(dtype="float64")

            if adjusted.notna().any():
                price = adjusted
            else:
                price = pd.to_numeric(price_df["close"], errors="coerce")
            
            price_df = price_df.copy()
            price_df["price"] = price
            price_df = price_df.dropna(subset=["price"])
            price_df = price_df[price_df["price"] > 0]

            if price_df.empty:
                logger.warning(f"No valid price data available for {symbol}")
                continue

            price_df = price_df.set_index("date").sort_index()
            
            # Do not fill missing observations when calculating returns.
            returns = price_df["price"].pct_change(fill_method=None)
            returns.name = symbol
            returns_data[symbol] = returns

        if not returns_data:
            logger.warning("No return data could be calculated")
            return pd.DataFrame()

        returns_df = pd.concat(returns_data.values(), axis=1)
        returns_df = returns_df.loc[(returns_df.index >= requested_start) & (returns_df.index <= requested_end)]
        returns_df = returns_df.sort_index()

        return returns_df

    def get_portfolio_returns(
        self,
        symbols: list[str],
        weights: list[float],
        start_date: str,
        end_date: str,
    ) -> pd.Series:
        """Calculate weighted daily returns for a portfolio of symbols."""
        if len(symbols) != len(weights) or not symbols:
            raise ValueError("Portfolio symbols and weights must have the same non-zero length")

        returns_df = self.get_returns_for_tickers(symbols, start_date, end_date)
        if returns_df.empty:
            return pd.Series(dtype="float64", name="Portfolio")

        normalized_symbols = [self.helper.normalize_ticker(symbol) for symbol in symbols]
        weight_series = pd.Series(weights, index=normalized_symbols, dtype="float64")
        weight_series = weight_series / weight_series.sum()
        available_weights = weight_series.reindex(returns_df.columns).fillna(0)

        portfolio_returns = returns_df.mul(available_weights, axis=1).sum(axis=1, min_count=1)
        portfolio_returns.name = "Portfolio"
        return portfolio_returns.dropna()

    def _portfolio_figure_period(
        self,
        symbols: list[str],
        period: str,
    ) -> AnalysisPeriod:
        return self._resolve_period(period, symbols)

    def create_portfolio_cumulative_figure(
        self,
        symbols: list[str],
        weights: list[float],
        period: str,
    ) -> Optional[go.Figure]:
        analysis_period = self._portfolio_figure_period(symbols, period)
        returns = self.get_portfolio_returns(symbols, weights, analysis_period.start_date, analysis_period.end_date)
        if returns.empty:
            return None

        cumulative = ((1 + returns).cumprod() - 1) * 100
        figure = go.Figure(go.Scatter(
            x=cumulative.index,
            y=cumulative.values,
            mode="lines",
            name="Portfolio",
            line={"color": "#a7b8fa", "width": 3},
            hovertemplate="%{x|%Y-%m-%d}<br>Return: %{y:.2f}%<extra></extra>",
        ))
        figure.update_layout(
            title=f"Portfolio cumulative return — {period.upper()}",
            xaxis_title="Date",
            yaxis_title="Cumulative return (%)",
            template="plotly_white",
        )
        return figure

    def create_portfolio_volatility_figure(
        self,
        symbols: list[str],
        weights: list[float],
        period: str,
        window: int = 30,
    ) -> Optional[go.Figure]:
        if window < 2:
            raise ValueError("Volatility window must be at least 2 trading days")

        analysis_period = self._portfolio_figure_period(symbols, period)
        returns = self.get_portfolio_returns(symbols, weights, analysis_period.start_date, analysis_period.end_date)
        rolling = returns.rolling(window=window, min_periods=window).std() * np.sqrt(252) * 100
        rolling = rolling.dropna()
        if rolling.empty:
            return None

        figure = go.Figure(go.Scatter(
            x=rolling.index,
            y=rolling.values,
            mode="lines",
            name="Portfolio volatility",
            line={"color": "#f2bf69", "width": 3},
            hovertemplate="%{x|%Y-%m-%d}<br>Volatility: %{y:.2f}%<extra></extra>",
        ))
        figure.update_layout(
            title=f"Portfolio rolling volatility — {period.upper()}",
            xaxis_title="Date",
            yaxis_title="Annualized volatility (%)",
            template="plotly_white",
        )
        return figure

    def create_portfolio_drawdown_figure(
        self,
        symbols: list[str],
        weights: list[float],
        period: str,
    ) -> Optional[go.Figure]:
        analysis_period = self._portfolio_figure_period(symbols, period)
        returns = self.get_portfolio_returns(symbols, weights, analysis_period.start_date, analysis_period.end_date)
        if returns.empty:
            return None

        cumulative = (1 + returns).cumprod()
        drawdown = (cumulative / cumulative.cummax() - 1) * 100
        figure = go.Figure(go.Scatter(
            x=drawdown.index,
            y=drawdown.values,
            mode="lines",
            name="Portfolio drawdown",
            fill="tozeroy",
            line={"color": "#ed7181", "width": 3},
            hovertemplate="%{x|%Y-%m-%d}<br>Drawdown: %{y:.2f}%<extra></extra>",
        ))
        figure.update_layout(
            title=f"Portfolio drawdown — {period.upper()}",
            xaxis_title="Date",
            yaxis_title="Drawdown (%)",
            template="plotly_white",
        )
        return figure

    def create_portfolio_allocation_figure(
        self,
        symbols: list[str],
        weights: list[float],
    ) -> go.Figure:
        normalized_weights = np.array(weights, dtype=float)
        normalized_weights = normalized_weights / normalized_weights.sum()
        figure = go.Figure(go.Pie(
            labels=[self.helper.normalize_ticker(symbol) for symbol in symbols],
            values=normalized_weights * 100,
            hole=0.55,
            textinfo="label+percent",
            hovertemplate="%{label}<br>Weight: %{percent}<extra></extra>",
        ))
        figure.update_layout(title="Current portfolio allocation", template="plotly_white")
        return figure

    def get_portfolio_statistics(
        self,
        symbols: list[str],
        weights: list[float],
        period: str,
        volatility_window: int = 30,
    ) -> pd.DataFrame:
        analysis_period = self._portfolio_figure_period(symbols, period)
        returns = self.get_portfolio_returns(symbols, weights, analysis_period.start_date, analysis_period.end_date)
        if returns.empty:
            return pd.DataFrame()
        return self.statistics_calculator.calculate(pd.DataFrame({"Portfolio": returns}))

    def create_candlestick_figure(
        self,
        symbol: str,
        start_date: str,
        end_date: str,
        period_label: str,
    ) -> Optional[go.Figure]:
        """Generate an OHLC candlestick chart for one instrument."""
        normalized_symbol = self.helper.normalize_ticker(symbol)
        price_data = self.loader.load_price_data(normalized_symbol, start_date, end_date)
        required_columns = {"date", "open", "high", "low", "close"}
        if price_data.empty or not required_columns.issubset(price_data.columns):
            logger.warning("No OHLC data available for %s", normalized_symbol)
            return None

        ohlc = price_data.copy()
        for column in required_columns - {"date"}:
            ohlc[column] = pd.to_numeric(ohlc[column], errors="coerce")
        ohlc = ohlc.dropna(subset=list(required_columns - {"date"}))
        if ohlc.empty:
            return None

        figure = go.Figure(go.Candlestick(
            x=ohlc["date"],
            open=ohlc["open"],
            high=ohlc["high"],
            low=ohlc["low"],
            close=ohlc["close"],
            name=normalized_symbol,
            increasing_line_color="#53d5ad",
            decreasing_line_color="#ed7181",
            increasing_fillcolor="#53d5ad",
            decreasing_fillcolor="#ed7181",
            hovertemplate=(
                "<b>%{x|%Y-%m-%d}</b><br>Open: %{open:.2f}<br>High: %{high:.2f}"
                "<br>Low: %{low:.2f}<br>Close: %{close:.2f}<extra></extra>"
            ),
        ))
        figure.update_layout(
            title=f"{normalized_symbol} price action — {period_label}",
            xaxis_title="Date",
            yaxis_title="Price",
            template="plotly_dark",
            paper_bgcolor="rgba(0,0,0,0)",
            plot_bgcolor="rgba(0,0,0,0)",
            xaxis_rangeslider_visible=False,
            hovermode="x",
        )
        return figure

    def calculate_portfolio_scenario(
        self,
        symbols: list[str],
        values: list[float],
        scenario_symbol: str,
        additional_quantity: int,
        period: str,
        cash_value: float = 0.0,
        volatility_window: int = 30,
    ) -> dict:
        """Compare current portfolio metrics with a proposed additional purchase."""
        if len(symbols) != len(values) or not symbols:
            raise ValueError("Portfolio symbols and values must have the same non-zero length")
        if any(value < 0 for value in values):
            raise ValueError("Portfolio values cannot be negative")
        if additional_quantity <= 0 or not float(additional_quantity).is_integer():
            raise ValueError("Additional quantity must be a positive whole number")
        if cash_value < 0:
            raise ValueError("Cash value cannot be negative")

        normalized_symbols = [self.helper.normalize_ticker(symbol) for symbol in symbols]
        normalized_scenario_symbol = self.helper.normalize_ticker(scenario_symbol)
        purchase_price = self.loader.get_latest_price(normalized_scenario_symbol)
        if purchase_price is None or purchase_price <= 0:
            raise ValueError(f"No saved price available for {normalized_scenario_symbol}")
        purchase_value = additional_quantity * purchase_price
        if purchase_value > cash_value:
            raise ValueError("Purchase exceeds available cash")
        current_values = dict(zip(normalized_symbols, values))
        current_values["CASH"] = cash_value
        proposed_values = current_values.copy()
        proposed_values["CASH"] -= purchase_value
        proposed_values[normalized_scenario_symbol] = (
            proposed_values.get(normalized_scenario_symbol, 0.0)
            + additional_quantity * purchase_price
        )

        def portfolio_snapshot(portfolio_values: dict[str, float]) -> dict:
            snapshot_symbols = list(portfolio_values)
            total_value = sum(portfolio_values.values())
            if total_value <= 0:
                raise ValueError("Portfolio value must be positive")
            weights = [portfolio_values[symbol] / total_value for symbol in snapshot_symbols]
            statistics = self.get_portfolio_statistics(
                snapshot_symbols, weights, period, volatility_window
            )
            return {
                "total_value": total_value,
                "symbols": snapshot_symbols,
                "weights": weights,
                "statistics": statistics.to_dict(orient="records"),
            }

        return {
            "period": period,
            "scenario": {
                "symbol": normalized_scenario_symbol,
                "additional_quantity": additional_quantity,
                "purchase_price": purchase_price,
                "purchase_value": purchase_value,
            },
            "current": portfolio_snapshot(current_values),
            "proposed": portfolio_snapshot(proposed_values),
        }


    # CORRELATION ANALYSIS
    def create_correlation_figure(
        self,
        tickers: list,
        start_date: str,
        end_date: str,
        period_label: str,
    ) -> Optional[go.Figure]:
        """
        Generate an interactive correlation heatmap of daily returns.
        Pearson correlation is calculated between the daily returns of each
        pair of instruments.

        Args:
            tickers: List of ticker symbols to analyze.
            start_date: Inclusive analysis start date.
            end_date: Inclusive analysis end date.
            period_label: Human-readable period label such as "1Y" or "5Y".

        Returns:
            A Plotly figure, or None when there is insufficient data.
        """
        returns_df = self.get_returns_for_tickers(tickers, start_date, end_date)

        if returns_df.empty:
            logger.warning("No data available for correlation plot")
            return None

        if len(returns_df.columns) < 2:
            logger.warning("At least two tickers are required for a correlation heatmap")
            return None

        correlation = returns_df.corr()
        ticker_label, ticker_title = self._chart_labels(returns_df.columns.tolist())

        fig = go.Figure(data=go.Heatmap(
            z=correlation.values,
            x=correlation.columns,
            y=correlation.index,
            colorscale="RdBu",
            zmid=0,
            zmin=-1,
            zmax=1,
            text=np.round(correlation.values, 2),
            texttemplate="%{text:.2f}",
            textfont={"size": 10},
            colorbar={"title": "Correlation"}
        ))

        fig.update_layout(
            title=f"Correlation Matrix of Daily Returns — {ticker_title} — {period_label}",
            xaxis_title="Ticker",
            yaxis_title="Ticker",
            width=1000,
            height=800
        )

        return fig

    def plot_correlation_heatmap(
        self,
        tickers: list,
        start_date: str,
        end_date: str,
        period_label: str,
    ) -> None:
        """Generate and save the correlation heatmap for file-based reports."""
        figure = self.create_correlation_figure(tickers, start_date, end_date, period_label)
        if figure is None:
            return

        ticker_label, _ = self._chart_labels(figure.data[0].x)
        filename = f"correlation/correlation_{ticker_label}_{period_label}.html"
        self.helper.save_plotly_chart(figure, filename)


    # ROLLING VOLATILITY
    def create_volatility_figure(
        self,
        tickers: list,
        start_date: str,
        end_date: str,
        period_label: str,
        window: int = 30,
    ) -> Optional[go.Figure]:
        """
        Generate interactive rolling annualized volatility trends.
        The resulting values are displayed as percentages -> 0.20 annualized volatility = 20%

        Args:
            tickers: List of ticker symbols to analyze.
            start_date: Inclusive analysis start date.
            end_date: Inclusive analysis end date.
            period_label: Human-readable period label such as "1Y" or "5Y".
            window: Number of trading observations used for the rolling
                volatility calculation. Default is 30 trading days.

        Returns:
            A Plotly figure, or None when there is insufficient data.
        """
        if window < 2:
            raise ValueError("Volatility window must be at least 2 trading days")
        
        returns_df = self.get_returns_for_tickers(tickers, start_date, end_date)

        if returns_df.empty:
            logger.warning("No data available for volatility plot")
            return None

        ticker_label, ticker_title = self._chart_labels(returns_df.columns.tolist())

        fig = go.Figure()
        plotted = 0

        for symbol in returns_df.columns:
            rolling_volatility = returns_df[symbol].rolling(window=window, min_periods=window).std() * np.sqrt(252) * 100

            if rolling_volatility.notna().any():
                fig.add_trace(go.Scatter(
                    x=rolling_volatility.index,
                    y=rolling_volatility.values,
                    mode="lines",
                    name=symbol,
                    line=dict(width=2)
                ))
                plotted += 1

        if plotted == 0:
            logger.warning(f"Not enough data to calculate {window}-day rolling volatility")
            return None

        fig.update_layout(
            title=f"{window}-Day Rolling Annualized Volatility — {ticker_title} — {period_label}",
            xaxis_title="Date",
            yaxis_title="Annualized Volatility (%)",
            hovermode="x unified",
            width=1400,
            height=700,
            template="plotly_white"
        )

        return fig

    def plot_volatility_trends(
        self,
        tickers: list,
        start_date: str,
        end_date: str,
        period_label: str,
        window: int = 30,
    ) -> None:
        """Generate and save rolling volatility for file-based reports."""
        figure = self.create_volatility_figure(tickers, start_date, end_date, period_label, window)
        if figure is None:
            return

        ticker_label, _ = self._chart_labels(figure.data[0].name and [trace.name for trace in figure.data])
        filename = f"rolling_volatility/rolling_volatility_{window}d_{ticker_label}_{period_label}.html"
        self.helper.save_plotly_chart(figure, filename)


    # DRAWDOWN ANALYSIS
    def create_drawdown_figure(
        self,
        tickers: list,
        start_date: str,
        end_date: str,
        period_label: str,
    ) -> Optional[go.Figure]:
        """Generate interactive drawdown percentages from daily returns."""
        returns_df = self.get_returns_for_tickers(tickers, start_date, end_date)

        if returns_df.empty:
            logger.warning("No data available for drawdown plot")
            return None

        _, ticker_title = self._chart_labels(returns_df.columns.tolist())
        fig = go.Figure()

        for symbol in returns_df.columns:
            returns = returns_df[symbol].dropna()
            if returns.empty:
                continue

            cumulative_value = (1 + returns).cumprod()
            drawdown = (cumulative_value / cumulative_value.cummax() - 1) * 100
            fig.add_trace(go.Scatter(
                x=drawdown.index,
                y=drawdown.values,
                mode="lines",
                name=symbol,
                fill="tozeroy",
                customdata=[symbol] * len(drawdown),
                hovertemplate="<b>%{customdata}</b><br>%{x|%Y-%m-%d}<br>Drawdown: %{y:.2f}%<extra></extra>",
            ))

        if not fig.data:
            logger.warning("No valid data available for drawdown plot")
            return None

        fig.update_layout(
            title=f"Drawdown — {ticker_title} — {period_label}",
            xaxis_title="Date",
            yaxis_title="Drawdown (%)",
            hovermode="x unified",
            width=1400,
            height=700,
            template="plotly_white",
        )
        return fig


    # ASSET CLASS VOLATILITY
    def create_asset_class_volatility_figure(
        self,
        tickers: list,
        start_date: str,
        end_date: str,
        period_label: str,
    ) -> Optional[go.Figure]:
        """
        Compare annualized volatility across individual securities and
        configured asset classes using an interactive box plot with overlay.
        Each ticker is represented as an individual point while the box plot
        summarizes the distribution of volatility within each asset class.

        Args:
            tickers: List of ticker symbols to analyze.
            start_date: Inclusive analysis start date.
            end_date: Inclusive analysis end date.
            period_label: Human-readable period label such as "1Y" or "5Y".
        """
        returns_df = self.get_returns_for_tickers(tickers, start_date, end_date)

        if returns_df.empty:
            logger.warning("No data available for asset class volatility plot")
            return None

        volatility = returns_df.std() * np.sqrt(252) * 100
        data_for_plot = []

        for ticker in returns_df.columns:
            if pd.isna(volatility[ticker]):
                continue
            data_for_plot.append({"Asset Class": self.helper.get_asset_class(ticker), "Ticker": ticker, "Annualized Volatility": volatility[ticker]})

        plot_df = pd.DataFrame(data_for_plot)

        if plot_df.empty:
            logger.warning("No valid volatility data for asset class plot")
            return None

        ticker_label, ticker_title = self._chart_labels(plot_df["Ticker"].tolist())

        fig = go.Figure()

        # Keep the class summary and its individual instruments in one trace so
        # small groups remain readable instead of stacking a second scatter plot.
        for asset_class in plot_df["Asset Class"].unique():
            class_data = plot_df[plot_df["Asset Class"] == asset_class]
            fig.add_trace(go.Box(
                x=[asset_class] * len(class_data),
                y=class_data["Annualized Volatility"],
                name=asset_class,
                boxmean="sd",
                boxpoints="all",
                jitter=0.35,
                pointpos=0,
                customdata=class_data["Ticker"],
                hovertemplate="<b>%{customdata}</b><br>Volatility: %{y:.2f}%<extra>%{x}</extra>",
                showlegend=False,
            ))

        fig.update_layout(
            title=f"Annualized Volatility by Asset Class — {ticker_title} — {period_label}",
            xaxis_title="Asset Class",
            yaxis_title="Annualized Volatility (%)",
            width=1200,
            height=700,
            template="plotly_white",
            hovermode="closest"
        )

        return fig

    def plot_asset_class_volatility(
        self,
        tickers: list,
        start_date: str,
        end_date: str,
        period_label: str,
    ) -> None:
        """Generate and save asset-class volatility for file-based reports."""
        figure = self.create_asset_class_volatility_figure(tickers, start_date, end_date, period_label)
        if figure is None:
            return

        ticker_label, _ = self._chart_labels(tickers)
        filename = f"asset_class_volatility/asset_class_volatility_{ticker_label}_{period_label}.html"
        self.helper.save_plotly_chart(figure, filename)


    # STATISTICS SUMMARY
    def calculate_summary_statistics(self, tickers: list, start_date: str, end_date: str) -> pd.DataFrame:
        """
        Calculate summary statistics for each analyzed instrument.

        The following metrics are calculated:

            Observations
                Number of available daily return observations.

            Annualized Return
                Geometric annualized return based on the sequence of daily
                returns.

            Annualized Volatility
                Standard deviation of daily returns multiplied by sqrt(252).

            Sharpe Ratio
                Annualized return divided by annualized volatility.

                This is a simplified Sharpe ratio that assumes a zero
                risk-free rate.

            Best Daily Return
                Largest observed daily return.

            Worst Daily Return
                Smallest observed daily return.

        Args:
            tickers: List of ticker symbols.
            start_date: Inclusive analysis start date.
            end_date: Inclusive analysis end date.

        Returns:
            DataFrame containing one row per ticker.
            An empty DataFrame is returned if no usable return data exists.
        """
        returns_df = self.get_returns_for_tickers(tickers, start_date, end_date)
        return self.statistics_calculator.calculate(returns_df)


    # PLOTS SUMMARY
    def generate_analysis_plots(
        self,
        tickers: list,
        start_date: str,
        end_date: str,
        period_label: str,
        volatility_window: int = 30
    ) -> None:

        self.plot_correlation_heatmap(tickers, start_date, end_date, period_label)

        self.plot_volatility_trends(tickers, start_date, end_date, period_label, window=volatility_window)

        self.plot_asset_class_volatility(tickers, start_date, end_date, period_label)


    # FULL ANALYSIS FUNCTION
    def compute_insights(self, tickers: list = None, asset_classes: list = None, period: str = "1y", volatility_window: int = 30, generate_plots: bool = False) -> pd.DataFrame:
        """
        Run the complete portfolio analysis.
        The function selects the requested instruments, determines the analysis
        date range, generates all requested plots, calculates summary
        statistics, and returns those statistics as a DataFrame.

        Generated plots:
            1. Correlation heatmap.
            2. Rolling annualized volatility.
            3. Asset-class volatility comparison.

        Args:
            tickers: Optional list of specific ticker symbols.

            asset_classes: Optional list of asset classes.

            period: Analysis period.

            volatility_window: Rolling window size for the volatility chart.

        Returns:
            DataFrame containing summary statistics for each analyzed ticker.
            Returns None if the analysis fails.
        """
        if volatility_window < 2:
            raise ValueError("Volatility window must be at least 2")
        
        analysis_tickers = self.helper.get_tickers_for_analysis(tickers, asset_classes)

        if not analysis_tickers:
            logger.error("No tickers selected. Check config.INSTRUMENTS.")
            return pd.DataFrame()

        analysis_period = self._resolve_period(period, analysis_tickers)

        logger.info(
            "Analysis date range: %s to %s",
            analysis_period.start_date,
            analysis_period.end_date,
        )
        logger.info(f"Analyzing tickers: {analysis_tickers}")
        
        # plots
        if generate_plots:
            self.generate_analysis_plots(
                analysis_tickers,
                analysis_period.start_date,
                analysis_period.end_date,
                analysis_period.label,
                volatility_window,
            )
            
        # summary
        summary = self.calculate_summary_statistics(
            analysis_tickers,
            analysis_period.start_date,
            analysis_period.end_date,
        )

        if not summary.empty:
            logger.info("Summary statistics:")
            logger.info("\n" + summary.to_string(index=False))
        else:
            logger.warning("No summary statistics could be calculated.")

        return summary



if __name__ == "__main__":
    
    helper = AnalysisHelper()
    loader = DataLoader()
    analysis_engine = AnalysisEngine(data_loader=loader, helper=helper)
    
    summary = analysis_engine.compute_insights(
        tickers=["AAPL", "BND", "SPY"],
        period="1y",
        volatility_window=30,
        generate_plots=True
    )

    if summary is not None and not summary.empty:
        print("\nAnalysis Summary:")
        print(summary.to_string(index=False))