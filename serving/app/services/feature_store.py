import requests
from app.config import FEAST_FEATURE_SERVER_URL

_FEATURE_REFS = [
    "team_stats:matches_played",
    "team_stats:wins",
    "team_stats:draws",
    "team_stats:losses",
    "team_stats:goals_for",
    "team_stats:goals_against",
]


def get_features(team_name: str) -> dict | None:
    payload = {
        "features": _FEATURE_REFS,
        "entities": {"team_name": [team_name]},
    }
    response = requests.post(
        f"{FEAST_FEATURE_SERVER_URL}/get-online-features",
        json=payload,
        timeout=5,
    )
    response.raise_for_status()
    data = response.json()

    feature_names = data["metadata"]["feature_names"]
    results = data["results"]

    features: dict[str, float] = {}
    for i, name in enumerate(feature_names):
        if name == "team_name":
            continue
        value = results[i]["values"][0]
        if results[i]["statuses"][0] != "PRESENT" or value is None:
            return None
        clean_name = name.split("__")[-1] if "__" in name else name
        features[clean_name] = float(value)

    return features or None
