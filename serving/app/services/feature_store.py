import redis
from app.config import REDIS_HOST, REDIS_PORT, REDIS_MAX_CONNECTIONS

_pool = redis.Redis(
    host=REDIS_HOST,
    port=REDIS_PORT,
    decode_responses=True,
    max_connections=REDIS_MAX_CONNECTIONS,
)


def get_features(user_id: str) -> dict | None:
    raw = _pool.hgetall(f"velocity:user:{user_id}")
    if not raw:
        return None
    return {k: float(v) for k, v in raw.items()}
