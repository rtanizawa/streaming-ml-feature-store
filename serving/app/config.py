import os

MODEL_PATH = os.getenv("MODEL_PATH", "models/model.ubj")
MATCH_MODEL_PATH = os.getenv("MATCH_MODEL_PATH", "models/match_result_model.ubj")
FEAST_FEATURE_SERVER_URL = os.getenv("FEAST_FEATURE_SERVER_URL", "http://localhost:6566")
