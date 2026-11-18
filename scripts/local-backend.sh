#!/usr/bin/env bash
# Runs every backend service natively (no Docker) against local PostgreSQL, Redis and Kafka.
# Usage: scripts/local-backend.sh build|start|stop|status
#   build -> compiles all services in "thin" mode (no fat jars, saves ~600 MB of disk)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOGS="$ROOT/.local/logs"; PIDS="$ROOT/.local/pids"
mkdir -p "$LOGS" "$PIDS"
SERVICES=(auth-service flight-service passenger-service booking-service checkin-service notification-service reporting-service gateway)

start() {
  set -a; source "$ROOT/.env"; set +a
  for s in "${SERVICES[@]}"; do
    if [ -f "$PIDS/$s.pid" ] && kill -0 "$(cat "$PIDS/$s.pid")" 2>/dev/null; then echo "$s already running"; continue; fi
    dir="$ROOT/backend/$s/target"
    if [ -f "$dir/classpath.txt" ]; then   # thin mode: classes + dependency classpath from ~/.m2 (no fat jar on disk)
      main=$(grep -rl "SpringApplication.run" "$ROOT/backend/$s/src/main/java" | head -1 | sed -E 's#.*/src/main/java/##; s#\.java$##; s#/#.#g')
      launch=(-cp "$dir/classes:$(cat "$dir/classpath.txt")" "$main")
    else
      launch=(-jar "$dir/app.jar")
    fi
    nohup java -Xms64m -Xmx256m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 "${launch[@]}" > "$LOGS/$s.log" 2>&1 &
    echo $! > "$PIDS/$s.pid"; echo "started $s (pid $!)"
  done
}
stop() {
  for s in "${SERVICES[@]}"; do
    [ -f "$PIDS/$s.pid" ] && kill "$(cat "$PIDS/$s.pid")" 2>/dev/null && echo "stopped $s"; rm -f "$PIDS/$s.pid"
  done
}
status() {
  for p in 8080 8081 8082 8083 8084 8085 8086 8087; do
    printf "%s " "$p"; curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:$p/actuator/health" || echo down
  done
}
build() {
  (cd "$ROOT/backend" && ./mvnw -q -DskipTests -Dspring-boot.repackage.skip=true package \
     dependency:build-classpath -Dmdep.outputFile=target/classpath.txt -Dmdep.includeScope=runtime)
}
"${1:-status}"
