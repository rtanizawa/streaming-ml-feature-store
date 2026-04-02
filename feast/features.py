from datetime import timedelta

from feast import Entity, FeatureView, Field, FileSource, PushSource
from feast.types import Int32, String

team = Entity(
    name="team_name",
    join_keys=["team_name"],
    description="Brazilian Série A team identifier (lowercase, underscores)",
)

team_stats_file_source = FileSource(
    name="team_stats_file_source",
    path="../data/offline_store/team_stats/",
    timestamp_field="event_timestamp",
)

team_stats_push_source = PushSource(
    name="team_stats_push_source",
    batch_source=team_stats_file_source,
)

team_stats_fv = FeatureView(
    name="team_stats",
    entities=[team],
    ttl=timedelta(days=0),
    schema=[
        Field(name="matches_played", dtype=Int32),
        Field(name="wins",           dtype=Int32),
        Field(name="draws",          dtype=Int32),
        Field(name="losses",         dtype=Int32),
        Field(name="goals_for",      dtype=Int32),
        Field(name="goals_against",  dtype=Int32),
    ],
    source=team_stats_push_source,
    online=True,
)
