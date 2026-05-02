import numpy as np
import xgboost as xgb

TEAM_FEATURES = ["matches_played", "wins", "draws", "losses", "goals_for", "goals_against"]
ODDS_FEATURES = ["odds_pinnacle_home", "odds_pinnacle_draw", "odds_pinnacle_away"]
FEATURE_ORDER = (
    [f"home_{f}" for f in TEAM_FEATURES]
    + [f"away_{f}" for f in TEAM_FEATURES]
    + ODDS_FEATURES
)
LABELS = ["H", "D", "A"]


class MatchScorer:
    def __init__(self, model_path: str):
        self.model = xgb.Booster()
        self.model.load_model(model_path)

    def predict(self, features: dict) -> dict:
        values = np.array([[float(features[f]) for f in FEATURE_ORDER]], dtype=np.float32)
        dmatrix = xgb.DMatrix(values, feature_names=FEATURE_ORDER)
        probs = self.model.predict(dmatrix)[0]
        return {label: float(p) for label, p in zip(LABELS, probs)}
