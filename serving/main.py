from contextlib import asynccontextmanager
from fastapi import FastAPI
from app.config import MODEL_PATH
from app.services.feature_store import get_features
from app.services.scorer import Scorer
from app.routers.predict import router


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.scorer = Scorer(MODEL_PATH)
    app.state.feature_store = type("FeatureStore", (), {"get_features": staticmethod(get_features)})()
    yield


app = FastAPI(lifespan=lifespan)
app.include_router(router)
