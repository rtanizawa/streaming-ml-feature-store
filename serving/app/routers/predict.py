from fastapi import APIRouter, HTTPException, Request

router = APIRouter()


@router.get("/predict/{user_id}")
def predict(user_id: str, request: Request):
    features = request.app.state.feature_store.get_features(user_id)
    if features is None:
        return {"user_id": user_id, "score": 0.0, "cold_start": True}

    score = request.app.state.scorer.predict(features)
    return {"user_id": user_id, "score": score}
