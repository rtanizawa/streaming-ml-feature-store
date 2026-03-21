#!/usr/bin/env bash
# Usage: ./scripts/kafka-create-topic.sh <topic-name> [partitions] [replication-factor]

set -euo pipefail

TOPIC="${1:?Usage: $0 <topic-name> [partitions] [replication-factor]}"
PARTITIONS="${2:-1}"
REPLICATION_FACTOR="${3:-1}"
CONTAINER="decision-tree-kafka"

echo "Creating topic '${TOPIC}' (partitions=${PARTITIONS}, replication-factor=${REPLICATION_FACTOR})..."

docker exec "${CONTAINER}" kafka-topics \
  --bootstrap-server localhost:9092 \
  --create \
  --if-not-exists \
  --topic "${TOPIC}" \
  --partitions "${PARTITIONS}" \
  --replication-factor "${REPLICATION_FACTOR}"

echo "Done."
