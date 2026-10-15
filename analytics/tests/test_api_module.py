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
