from unittest.mock import MagicMock, patch

import pytest

import app.services.feature_store as fs_module
from app.services.feature_store import get_features


def _feast_response(statuses: list[str], values_per_feature: list) -> dict:
    names = ["team_name"] + [f"team_stats__{f}" for f in
                              ["matches_played", "wins", "draws", "losses", "goals_for", "goals_against"]]
    results = [{"values": ["flamengo"], "statuses": ["PRESENT"]}]
    for val, status in zip(values_per_feature, statuses):
        results.append({"values": [val], "statuses": [status]})
    return {"metadata": {"feature_names": names}, "results": results}


@patch("app.services.feature_store.requests.post")
def test_get_features_returns_dict_on_present(mock_post):
    mock_post.return_value.json.return_value = _feast_response(
        statuses=["PRESENT"] * 6,
        values_per_feature=[20, 15, 3, 2, 40, 20],
    )
    mock_post.return_value.raise_for_status = MagicMock()

    result = get_features("flamengo")

    assert result == {
        "matches_played": 20.0,
        "wins": 15.0,
        "draws": 3.0,
        "losses": 2.0,
        "goals_for": 40.0,
        "goals_against": 20.0,
    }


@patch("app.services.feature_store.requests.post")
def test_get_features_returns_none_on_not_found(mock_post):
    mock_post.return_value.json.return_value = _feast_response(
        statuses=["NOT_FOUND"] * 6,
        values_per_feature=[None] * 6,
    )
    mock_post.return_value.raise_for_status = MagicMock()

    assert get_features("unknown_team") is None


@patch("app.services.feature_store.requests.post")
def test_get_features_returns_none_when_value_is_null(mock_post):
    mock_post.return_value.json.return_value = _feast_response(
        statuses=["PRESENT"] * 6,
        values_per_feature=[None] * 6,
    )
    mock_post.return_value.raise_for_status = MagicMock()

    assert get_features("flamengo") is None


@patch("app.services.feature_store.requests.post")
def test_get_features_strips_view_prefix(mock_post):
    mock_post.return_value.json.return_value = _feast_response(
        statuses=["PRESENT"] * 6,
        values_per_feature=[5, 3, 1, 1, 8, 5],
    )
    mock_post.return_value.raise_for_status = MagicMock()

    result = get_features("atletico_mg")
    assert "matches_played" in result
    assert "team_stats__matches_played" not in result
