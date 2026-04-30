import tempfile
from pathlib import Path

import numpy as np
import pytest
import xgboost as xgb

from app.services.scorer import Scorer, FEATURES


@pytest.fixture
def model_path(tmp_path: Path) -> str:
    X = np.array([[10, 5, 2, 3, 15, 10], [20, 15, 3, 2, 40, 20]], dtype=np.float32)
    y = np.array([0, 1], dtype=np.float32)
    dtrain = xgb.DMatrix(X, label=y, feature_names=FEATURES)
    model = xgb.train({"objective": "binary:logistic", "seed": 42}, dtrain, num_boost_round=5)
    path = str(tmp_path / "model.ubj")
    model.save_model(path)
    return path


def test_predict_returns_float(model_path):
    scorer = Scorer(model_path)
    features = dict(zip(FEATURES, [10, 5, 2, 3, 15, 10]))
    score = scorer.predict(features)
    assert isinstance(score, float)
    assert 0.0 <= score <= 1.0


def test_predict_missing_features_default_to_zero(model_path):
    scorer = Scorer(model_path)
    score = scorer.predict({})
    assert isinstance(score, float)


def test_feature_order_is_stable(model_path):
    scorer = Scorer(model_path)
    features_a = {"matches_played": 20, "wins": 15, "draws": 3, "losses": 2, "goals_for": 40, "goals_against": 20}
    features_b = {"goals_against": 20, "losses": 2, "wins": 15, "goals_for": 40, "draws": 3, "matches_played": 20}
    assert scorer.predict(features_a) == scorer.predict(features_b)
