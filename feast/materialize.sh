#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
feast apply
feast materialize 2012-01-01T00:00:00 "$(date -u +"%Y-%m-%dT%H:%M:%S")"
echo "Materialization complete."
