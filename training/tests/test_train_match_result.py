from pathlib import Path
from unittest.mock import MagicMock

import numpy as np
import pandas as pd
import pytest
import xgboost as xgb

from train_match_result import (
    FEATURES,
    LABEL_MAP,
    NUM_CLASSES,
    ODDS,
    assemble_dataset,
    build_entity_df,
    load_matches,
    odds_implied_logloss,
    time_split,
    train,
)


def _make_matches_df(n: int = 10) -> pd.DataFrame:
    rng = np.random.default_rng(0)
    teams = ["flamengo", "palmeiras", "santos", "gremio", "fluminense", "corinthians"]
    results = rng.choice(["H", "D", "A"], size=n)
    base_ts = pd.Timestamp("2023-05-01", tz="UTC")
    return pd.DataFrame({
        "event_id": [f"evt-{i}" for i in range(n)],
        "event_timestamp": [(base_ts + pd.Timedelta(days=i)).value // 1_000_000 for i in range(n)],
        "home_team": rng.choice(teams, size=n),
        "away_team": rng.choice(teams, size=n),
        "home_goals": rng.integers(0, 4, size=n),
        "away_goals": rng.integers(0, 4, size=n),
        "result": results,
        "odds_pinnacle_home": rng.uniform(1.5, 4.0, size=n),
        "odds_pinnacle_draw": rng.uniform(2.5, 4.5, size=n),
        "odds_pinnacle_away": rng.uniform(1.5, 6.0, size=n),
        "odds_avg_home": rng.uniform(1.5, 4.0, size=n),
        "odds_avg_draw": rng.uniform(2.5, 4.5, size=n),
        "odds_avg_away": rng.uniform(1.5, 6.0, size=n),
    })


@pytest.fixture
def matches_parquet(tmp_path) -> Path:
    pytest.importorskip("pyarrow")
    df = _make_matches_df(12)
    # Inject one null-result row to confirm load_matches drops it.
    df.loc[0, "result"] = None
    path = tmp_path / "matches.parquet"
    df.to_parquet(path, index=False)
    return path


def _fake_store(matches: pd.DataFrame):
    store = MagicMock()

    def get_historical_features(entity_df, features):
        n = len(entity_df)
        rng = np.random.default_rng(len(features))
        feats = pd.DataFrame({f: rng.integers(0, 30, size=n) for f in FEATURES})
        out = MagicMock()
        out.to_df.return_value = pd.concat(
            [entity_df.reset_index(drop=True), feats], axis=1
        )
        return out

    store.get_historical_features.side_effect = get_historical_features
    return store


def test_load_matches_drops_null_results(matches_parquet):
    df = load_matches(matches_parquet)
    assert df["result"].isna().sum() == 0
    assert len(df) == 11


def test_load_matches_sorts_by_timestamp(matches_parquet):
    df = load_matches(matches_parquet)
    assert df["event_timestamp"].is_monotonic_increasing


def test_build_entity_df_shifts_timestamp_by_one_ms(matches_parquet):
    df = load_matches(matches_parquet)
    entity = build_entity_df(df, "home")
    diff = (df["event_timestamp"].reset_index(drop=True) - entity["event_timestamp"]).dt.total_seconds() * 1000
    assert (diff == 1.0).all()


def test_build_entity_df_picks_correct_side(matches_parquet):
    df = load_matches(matches_parquet)
    home_entity = build_entity_df(df, "home")
    away_entity = build_entity_df(df, "away")
    assert (home_entity["team_name"].values == df["home_team"].values).all()
    assert (away_entity["team_name"].values == df["away_team"].values).all()


def test_assemble_dataset_shape_and_labels(matches_parquet):
    df = load_matches(matches_parquet)
    store = _fake_store(df)
    X, y, names = assemble_dataset(df, store)

    expected_cols = (
        [f"home_{f}" for f in FEATURES]
        + [f"away_{f}" for f in FEATURES]
        + ODDS
    )
    assert names == expected_cols
    assert X.shape == (len(df), len(expected_cols))
    assert set(np.unique(y)).issubset(set(LABEL_MAP.values()))


def test_odds_implied_logloss_is_positive(matches_parquet):
    df = load_matches(matches_parquet)
    loss = odds_implied_logloss(df)
    assert loss > 0
    assert np.isfinite(loss)


def test_time_split_preserves_order():
    X = np.arange(20).reshape(-1, 1).astype(np.float32)
    y = np.arange(20)
    X_tr, X_te, y_tr, y_te = time_split(X, y, train_ratio=0.75)
    assert len(X_tr) == 15
    assert len(X_te) == 5
    assert (y_tr == np.arange(15)).all()
    assert (y_te == np.arange(15, 20)).all()


def test_train_returns_booster_with_three_classes(matches_parquet):
    df = load_matches(matches_parquet)
    store = _fake_store(df)
    X, y, names = assemble_dataset(df, store)
    model = train(X, y, names)
    assert isinstance(model, xgb.Booster)
    preds = model.predict(xgb.DMatrix(X, feature_names=names))
    assert preds.shape == (len(df), NUM_CLASSES)
    np.testing.assert_allclose(preds.sum(axis=1), 1.0, atol=1e-5)
