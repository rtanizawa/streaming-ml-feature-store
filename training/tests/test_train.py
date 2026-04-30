import tempfile
from pathlib import Path

import pandas as pd
import pytest

from train import load_latest_stats, make_labels, train, FEATURES


@pytest.fixture
def sample_df() -> pd.DataFrame:
    return pd.DataFrame({
        "team_name": ["flamengo", "palmeiras", "santos", "gremio"],
        "matches_played": [20, 18, 15, 10],
        "wins":           [15,  8,  4,  3],
        "draws":          [ 3,  5,  5,  2],
        "losses":         [ 2,  5,  6,  5],
        "goals_for":      [40, 25, 18, 12],
        "goals_against":  [15, 22, 24, 20],
        "event_timestamp": pd.to_datetime([
            "2023-12-01", "2023-12-01", "2023-12-01", "2023-12-01"
        ]),
    })


def test_make_labels_high_performer(sample_df):
    labels = make_labels(sample_df)
    assert labels["flamengo" == sample_df["team_name"]].iloc[0] == 1
    assert labels["santos" == sample_df["team_name"]].iloc[0] == 0


def test_make_labels_boundary():
    df = pd.DataFrame({
        "team_name": ["team_a", "team_b"],
        "matches_played": [10, 10],
        "wins": [4, 3],
        "draws": [0, 0],
        "losses": [6, 7],
    })
    labels = make_labels(df)
    assert labels.tolist() == [1, 0]


def test_train_produces_booster(sample_df):
    import xgboost as xgb
    model = train(sample_df)
    assert isinstance(model, xgb.Booster)


def test_train_saves_and_loads(sample_df, tmp_path):
    import xgboost as xgb
    import numpy as np

    model = train(sample_df)
    model_path = tmp_path / "model.ubj"
    model.save_model(str(model_path))

    loaded = xgb.Booster()
    loaded.load_model(str(model_path))
    X = sample_df[FEATURES].values.astype("float32")
    preds = loaded.predict(xgb.DMatrix(X, feature_names=FEATURES))
    assert len(preds) == len(sample_df)
    assert all(0.0 <= p <= 1.0 for p in preds)


def test_load_latest_stats_deduplicates_teams(tmp_path):
    pytest.importorskip("pyarrow")

    data = {
        "team_name": ["flamengo", "flamengo"],
        "matches_played": [10, 20],
        "wins": [6, 14],
        "draws": [2, 3],
        "losses": [2, 3],
        "goals_for": [18, 38],
        "goals_against": [10, 15],
        "event_timestamp": pd.to_datetime(["2023-06-01", "2023-12-01"]),
    }
    df = pd.DataFrame(data)
    pq_path = tmp_path / "team_stats.parquet"
    df.to_parquet(str(pq_path), index=False)

    result = load_latest_stats(pq_path)
    assert len(result) == 1
    assert result.iloc[0]["matches_played"] == 20
