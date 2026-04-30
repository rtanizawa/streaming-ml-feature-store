import numpy as np
import xgboost as xgb

FEATURES = ["matches_played", "wins", "draws", "losses", "goals_for", "goals_against"]


class Scorer:
    def __init__(self, model_path: str):
        self.model = xgb.Booster()
        self.model.load_model(model_path)

    def predict(self, features: dict) -> float:
        values = np.array([[features.get(f, 0.0) for f in FEATURES]], dtype=np.float32)
        dmatrix = xgb.DMatrix(values, feature_names=FEATURES)
        return float(self.model.predict(dmatrix)[0])
