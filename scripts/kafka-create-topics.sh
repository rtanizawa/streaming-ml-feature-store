#!/usr/bin/env bash
# Creates all project Kafka topics.
# Add new topics to the TOPICS array below.

set -euo pipefail

CONTAINER="streaming-ml-feature-store"

declare -A TOPICS
# TOPICS["topic-name"]="partitions:replication-factor"
TOPICS["decisions"]="1:1"
TOPICS["events"]="1:1"

for TOPIC in "${!TOPICS[@]}"; do
  IFS=":" read -r PARTITIONS REPLICATION_FACTOR <<< "${TOPICS[$TOPIC]}"
  echo "Creating topic '${TOPIC}' (partitions=${PARTITIONS}, replication-factor=${REPLICATION_FACTOR})..."
  docker exec "${CONTAINER}" kafka-topics \
    --bootstrap-server localhost:9092 \
    --create \
    --if-not-exists \
    --topic "${TOPIC}" \
    --partitions "${PARTITIONS}" \
    --replication-factor "${REPLICATION_FACTOR}"
done

echo "All topics created."
