from contextlib import asynccontextmanager
from fastapi import FastAPI
from app.config import MATCH_MODEL_PATH, MODEL_PATH
from app.services.feature_store import get_features
from app.services.match_scorer import MatchScorer
from app.services.scorer import Scorer
from app.routers.predict import router as predict_router
from app.routers.predict_match import router as predict_match_router


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.scorer = Scorer(MODEL_PATH)
    app.state.match_scorer = MatchScorer(MATCH_MODEL_PATH)
    app.state.feature_store = type("FeatureStore", (), {"get_features": staticmethod(get_features)})()
    yield


app = FastAPI(lifespan=lifespan)
app.include_router(predict_router)
app.include_router(predict_match_router)
