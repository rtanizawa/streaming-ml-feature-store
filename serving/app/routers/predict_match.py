from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel

from app.services.match_scorer import TEAM_FEATURES

router = APIRouter()


class MatchPredictRequest(BaseModel):
    home_team: str
    away_team: str
    odds_pinnacle_home: float
    odds_pinnacle_draw: float
    odds_pinnacle_away: float


@router.post("/predict-match")
def predict_match(body: MatchPredictRequest, request: Request):
    fs = request.app.state.feature_store
    home_feats = fs.get_features(body.home_team)
    away_feats = fs.get_features(body.away_team)

    missing = [name for name, f in (("home", home_feats), ("away", away_feats)) if f is None]
    if missing:
        raise HTTPException(status_code=404, detail=f"team_stats not found for: {missing}")

    flat = {f"home_{k}": home_feats[k] for k in TEAM_FEATURES}
    flat.update({f"away_{k}": away_feats[k] for k in TEAM_FEATURES})
    flat["odds_pinnacle_home"] = body.odds_pinnacle_home
    flat["odds_pinnacle_draw"] = body.odds_pinnacle_draw
    flat["odds_pinnacle_away"] = body.odds_pinnacle_away

    probs = request.app.state.match_scorer.predict(flat)
    return {
        "home_team": body.home_team,
        "away_team": body.away_team,
        "probabilities": probs,
        "prediction": max(probs, key=probs.get),
    }
