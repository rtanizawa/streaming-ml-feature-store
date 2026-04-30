from fastapi import APIRouter, Request

router = APIRouter()


@router.get("/predict/{team_name}")
def predict(team_name: str, request: Request):
    features = request.app.state.feature_store.get_features(team_name)
    if features is None:
        return {"team_name": team_name, "score": 0.0, "cold_start": True}

    score = request.app.state.scorer.predict(features)
    return {"team_name": team_name, "score": score}
