"""Train an XGBoost 3-class classifier (H / D / A) for Brazilian Série A matches.

Features per match: 6 cumulative team_stats columns for each side (joined point-in-time
via Feast) plus the 3 closing Pinnacle odds. Label: result in {H, D, A}.

Run: python training/train_match_result.py [matches_path] [model_output_path]
"""

import sys
from pathlib import Path
from typing import Tuple

import numpy as np
import pandas as pd
import xgboost as xgb

FEATURES = ["matches_played", "wins", "draws", "losses", "goals_for", "goals_against"]
ODDS = ["odds_pinnacle_home", "odds_pinnacle_draw", "odds_pinnacle_away"]
LABEL_MAP = {"H": 0, "D": 1, "A": 2}
NUM_CLASSES = 3

_ROOT = Path(__file__).parent.parent
DEFAULT_MATCHES = _ROOT / "data/offline_store/matches/"
DEFAULT_MODEL_OUT = _ROOT / "serving/models/match_result_model.ubj"
DEFAULT_FEAST_REPO = _ROOT / "feast"


def load_matches(matches_path: Path) -> pd.DataFrame:
    df = pd.read_parquet(matches_path)
    df = df.dropna(subset=["result"]).copy()
    df["event_timestamp"] = pd.to_datetime(df["event_timestamp"], unit="ms", utc=True)
    return df.sort_values("event_timestamp").reset_index(drop=True)


def build_entity_df(matches: pd.DataFrame, side: str) -> pd.DataFrame:
    # Subtract 1ms to avoid point-in-time leakage: team_stats.event_timestamp is set
    # *after* a match's outcome is folded into the running counters, so reading at
    # exactly match_timestamp would expose the label. -1ms picks the prior state.
    return pd.DataFrame({
        "team_name": matches[f"{side}_team"].values,
        "event_timestamp": matches["event_timestamp"] - pd.Timedelta(milliseconds=1),
    })


def join_team_features(store, matches: pd.DataFrame, side: str) -> pd.DataFrame:
    entity_df = build_entity_df(matches, side)
    feats = store.get_historical_features(
        entity_df=entity_df,
        features=[f"team_stats:{f}" for f in FEATURES],
    ).to_df()
    return feats[FEATURES].add_prefix(f"{side}_").reset_index(drop=True)


def assemble_dataset(matches: pd.DataFrame, store) -> Tuple[np.ndarray, np.ndarray, list]:
    home = join_team_features(store, matches, "home")
    away = join_team_features(store, matches, "away")
    odds = matches[ODDS].reset_index(drop=True)
    X_df = pd.concat([home, away, odds], axis=1)
    feature_names = list(X_df.columns)
    X = X_df.values.astype(np.float32)
    y = matches["result"].map(LABEL_MAP).values
    return X, y, feature_names


def time_split(X: np.ndarray, y: np.ndarray, train_ratio: float = 0.8) -> Tuple:
    cutoff = int(len(X) * train_ratio)
    return X[:cutoff], X[cutoff:], y[:cutoff], y[cutoff:]


def odds_implied_logloss(matches: pd.DataFrame) -> float:
    odds = matches[ODDS].values.astype(np.float64)
    inv = 1.0 / odds
    probs = inv / inv.sum(axis=1, keepdims=True)
    y = matches["result"].map(LABEL_MAP).values
    eps = 1e-15
    picked = probs[np.arange(len(y)), y]
    return float(-np.log(np.clip(picked, eps, 1.0)).mean())


def train(X: np.ndarray, y: np.ndarray, feature_names: list) -> xgb.Booster:
    X_train, X_test, y_train, y_test = time_split(X, y)

    dtrain = xgb.DMatrix(X_train, label=y_train, feature_names=feature_names)
    dtest = xgb.DMatrix(X_test, label=y_test, feature_names=feature_names)

    params = {
        "objective": "multi:softprob",
        "num_class": NUM_CLASSES,
        "eval_metric": "mlogloss",
        "max_depth": 4,
        "eta": 0.1,
        "seed": 42,
    }
    return xgb.train(
        params,
        dtrain,
        num_boost_round=100,
        evals=[(dtrain, "train"), (dtest, "test")],
        verbose_eval=20,
    )


def main() -> None:
    matches_path = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_MATCHES
    model_path = Path(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_MODEL_OUT

    from feast import FeatureStore

    print(f"Reading matches: {matches_path}")
    matches = load_matches(matches_path)
    print(f"Matches with results: {len(matches)}")

    baseline = odds_implied_logloss(matches)
    print(f"Baseline (Pinnacle implied) log-loss: {baseline:.4f}")

    store = FeatureStore(repo_path=str(DEFAULT_FEAST_REPO))
    X, y, feature_names = assemble_dataset(matches, store)

    model = train(X, y, feature_names)

    model_path.parent.mkdir(parents=True, exist_ok=True)
    model.save_model(str(model_path))
    print(f"Model saved -> {model_path}")


if __name__ == "__main__":
    main()
