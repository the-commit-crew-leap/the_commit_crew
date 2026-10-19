import json

import plotly.graph_objects as go
import pytest

from src import api_module


def test_figure_response_serializes_plotly_json():
    figure = go.Figure(data=[go.Scatter(x=[1, 2], y=[3, 4])])

    response = api_module._figure_response(figure)

    assert response.status_code == 200
    payload = json.loads(response.body)
    assert payload["data"][0]["type"] == "scatter"


def test_figure_response_returns_not_found_without_data():
    with pytest.raises(api_module.HTTPException) as error:
        api_module._figure_response(None)

    assert error.value.status_code == 404


def test_period_validation_rejects_unknown_period():
    with pytest.raises(api_module.HTTPException) as error:
        api_module._validate_period("2y")

    assert error.value.status_code == 400


def test_cors_allows_angular_development_server():
    cors_middleware = next(
        middleware
        for middleware in api_module.app.user_middleware
        if middleware.cls.__name__ == "CORSMiddleware"
    )

    assert "http://localhost:8086" in cors_middleware.kwargs["allow_origins"]
    assert cors_middleware.kwargs["allow_origin_regex"] == r"https?://10\.9\.\d{1,3}\.\d{1,3}:8086"


def test_portfolio_transactions_are_normalized():
    result = api_module._portfolio_transactions(
        ["AAPL"], ["BUY"], [2.0], ["2026-01-02T10:00:00"]
    )

    assert result == [{
        "symbol": "AAPL",
        "side": "BUY",
        "quantity": 2.0,
        "created_on": "2026-01-02T10:00:00",
    }]


def test_portfolio_transactions_reject_incomplete_arrays():
    with pytest.raises(api_module.HTTPException) as error:
        api_module._portfolio_transactions(
            ["AAPL"], ["BUY"], [2.0], None
        )

    assert error.value.status_code == 400


def test_portfolio_transactions_reject_invalid_side_and_quantity():
    with pytest.raises(api_module.HTTPException) as error:
        api_module._portfolio_transactions(
            ["AAPL"], ["HOLD"], [0], ["2026-01-02"]
        )

    assert error.value.status_code == 400
