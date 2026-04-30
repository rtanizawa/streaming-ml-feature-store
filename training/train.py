"""Train an XGBoost classifier on team stats from the offline Parquet store.

Label: is_high_performer = 1 if win_rate >= 0.4, else 0.
Run: python training/train.py [parquet_path] [model_output_path]
"""

import sys
from pathlib import Path

import numpy as np
import pandas as pd
import xgboost as xgb
from sklearn.model_selection import train_test_split

FEATURES = ["matches_played", "wins", "draws", "losses", "goals_for", "goals_against"]

_ROOT = Path(__file__).parent.parent
DEFAULT_PARQUET = _ROOT / "data/offline_store/team_stats/team_stats.parquet"
DEFAULT_MODEL_OUT = _ROOT / "serving/models/model.ubj"


def load_latest_stats(parquet_path: Path) -> pd.DataFrame:
    df = pd.read_parquet(parquet_path)
    df = df.sort_values("event_timestamp").groupby("team_name").last().reset_index()
    return df[df["matches_played"] > 0].copy()


def make_labels(df: pd.DataFrame) -> pd.Series:
    return ((df["wins"] / df["matches_played"]) >= 0.4).astype(int)


def train(df: pd.DataFrame) -> xgb.Booster:
    X = df[FEATURES].values.astype(np.float32)
    y = make_labels(df).values

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42
    )

    dtrain = xgb.DMatrix(X_train, label=y_train, feature_names=FEATURES)
    dtest = xgb.DMatrix(X_test, label=y_test, feature_names=FEATURES)

    params = {
        "objective": "binary:logistic",
        "eval_metric": "logloss",
        "max_depth": 3,
        "eta": 0.1,
        "seed": 42,
    }
    model = xgb.train(
        params,
        dtrain,
        num_boost_round=50,
        evals=[(dtest, "test")],
        verbose_eval=10,
    )
    return model


def main() -> None:
    parquet_path = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_PARQUET
    model_path = Path(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_MODEL_OUT

    print(f"Reading offline store: {parquet_path}")
    df = load_latest_stats(parquet_path)
    print(f"Teams loaded: {len(df)}  |  high-performers: {make_labels(df).sum()}")

    model = train(df)

    model_path.parent.mkdir(parents=True, exist_ok=True)
    model.save_model(str(model_path))
    print(f"Model saved → {model_path}")


if __name__ == "__main__":
    main()
