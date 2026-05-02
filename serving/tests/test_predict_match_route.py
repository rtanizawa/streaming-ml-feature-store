from pathlib import Path
from unittest.mock import MagicMock

import numpy as np
import pytest
import xgboost as xgb
from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.routers.predict_match import router
from app.services.match_scorer import FEATURE_ORDER, MatchScorer, TEAM_FEATURES


@pytest.fixture
def app(tmp_path: Path) -> FastAPI:
    rng = np.random.default_rng(0)
    X = rng.uniform(0, 30, size=(30, len(FEATURE_ORDER))).astype(np.float32)
    y = rng.integers(0, 3, size=30)
    dtrain = xgb.DMatrix(X, label=y, feature_names=FEATURE_ORDER)
    model = xgb.train(
        {"objective": "multi:softprob", "num_class": 3, "seed": 42},
        dtrain,
        num_boost_round=3,
    )
    model_path = str(tmp_path / "match_model.ubj")
    model.save_model(model_path)

    fs = MagicMock()
    fs.get_features.return_value = {f: 1.0 for f in TEAM_FEATURES}

    app = FastAPI()
    app.state.match_scorer = MatchScorer(model_path)
    app.state.feature_store = fs
    app.include_router(router)
    return app


def _payload() -> dict:
    return {
        "home_team": "flamengo",
        "away_team": "santos",
        "odds_pinnacle_home": 1.5,
        "odds_pinnacle_draw": 4.0,
        "odds_pinnacle_away": 6.0,
    }


def test_predict_match_returns_probabilities(app):
    client = TestClient(app)
    resp = client.post("/predict-match", json=_payload())
    assert resp.status_code == 200
    body = resp.json()
    assert body["home_team"] == "flamengo"
    assert body["away_team"] == "santos"
    assert set(body["probabilities"].keys()) == {"H", "D", "A"}
    assert abs(sum(body["probabilities"].values()) - 1.0) < 1e-5
    assert body["prediction"] in {"H", "D", "A"}


def test_predict_match_returns_404_when_team_missing(app):
    app.state.feature_store.get_features.side_effect = lambda team: (
        None if team == "santos" else {f: 1.0 for f in TEAM_FEATURES}
    )
    client = TestClient(app)
    resp = client.post("/predict-match", json=_payload())
    assert resp.status_code == 404
    assert "away" in resp.json()["detail"]


def test_predict_match_validates_required_fields(app):
    client = TestClient(app)
    resp = client.post("/predict-match", json={"home_team": "flamengo"})
    assert resp.status_code == 422
