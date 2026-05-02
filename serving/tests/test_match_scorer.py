from pathlib import Path

import numpy as np
import pytest
import xgboost as xgb

from app.services.match_scorer import FEATURE_ORDER, LABELS, MatchScorer


@pytest.fixture
def model_path(tmp_path: Path) -> str:
    rng = np.random.default_rng(0)
    X = rng.uniform(0, 30, size=(30, len(FEATURE_ORDER))).astype(np.float32)
    y = rng.integers(0, 3, size=30)
    dtrain = xgb.DMatrix(X, label=y, feature_names=FEATURE_ORDER)
    model = xgb.train(
        {"objective": "multi:softprob", "num_class": 3, "seed": 42},
        dtrain,
        num_boost_round=5,
    )
    path = str(tmp_path / "match_model.ubj")
    model.save_model(path)
    return path


def _features() -> dict:
    return {f: 1.0 for f in FEATURE_ORDER}


def test_predict_returns_probabilities_for_three_labels(model_path):
    scorer = MatchScorer(model_path)
    probs = scorer.predict(_features())
    assert set(probs.keys()) == set(LABELS)
    for v in probs.values():
        assert 0.0 <= v <= 1.0
    assert abs(sum(probs.values()) - 1.0) < 1e-5


def test_predict_is_order_independent(model_path):
    scorer = MatchScorer(model_path)
    a = _features()
    b = dict(reversed(list(a.items())))
    assert scorer.predict(a) == scorer.predict(b)


def test_predict_raises_on_missing_feature(model_path):
    scorer = MatchScorer(model_path)
    feats = _features()
    feats.pop("home_wins")
    with pytest.raises(KeyError):
        scorer.predict(feats)
