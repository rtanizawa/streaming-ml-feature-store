import numpy as np
import xgboost as xgb


class Scorer:
    def __init__(self, model_path: str):
        self.model = xgb.Booster()
        self.model.load_model(model_path)

    def predict(self, features: dict) -> float:
        # TODO: align feature order with training
        values = np.array([[
            features.get("txn_count_1h", 0.0),
            features.get("txn_sum_1h", 0.0),
            features.get("txn_count_24h", 0.0),
            features.get("txn_sum_24h", 0.0),
        ]])
        dmatrix = xgb.DMatrix(values)
        return float(self.model.predict(dmatrix)[0])
