#!/usr/bin/env bash
# Starts a single-node Kafka (KRaft, no ZooKeeper) for native local development without Docker.
# Usage: KAFKA_HOME=~/.aeroflow-dev/kafka_2.13-3.8.1 scripts/local-kafka.sh
set -euo pipefail
KAFKA_HOME="${KAFKA_HOME:-$HOME/.aeroflow-dev/kafka_2.13-3.8.1}"
DATA="${KAFKA_DATA:-$HOME/.aeroflow-dev/kafka-data}"
CFG="$HOME/.aeroflow-dev/kraft.properties"
cp "$KAFKA_HOME/config/kraft/server.properties" "$CFG"
sed -i.bak "s#^log.dirs=.*#log.dirs=$DATA#" "$CFG"
if [ ! -f "$DATA/meta.properties" ]; then
  "$KAFKA_HOME/bin/kafka-storage.sh" format -t "$("$KAFKA_HOME/bin/kafka-storage.sh" random-uuid)" -c "$CFG"
fi
export KAFKA_HEAP_OPTS="-Xmx384m -Xms128m"
exec "$KAFKA_HOME/bin/kafka-server-start.sh" "$CFG"
