#!/bin/sh
# Повний набір браузерних доказів через Caddy: sh run-evidence.sh [origin]
ORIGIN="${1:-http://localhost:8088}"; export ORIGIN
E="$(cd .. && pwd)/docs/evidence"
size() { case "$1" in 390) echo 844;; 834) echo 1112;; *) echo 900;; esac; }
echo "### deals-shots"; node deals-shots.mjs "$ORIGIN" "$E/deals" 1440,834,390 2>&1 | tail -3
echo "### deals-flows"; node deals-flows.mjs "$ORIGIN" "$E/deals/flows" 2>&1 | tail -4
for w in 1440 834 390; do
  h=$(size $w)
  echo "### clients-flow $w"; node clients-flow.mjs "$E/clients" $w $h 2>&1 | tail -2
  echo "### tasks-flow $w"; node tasks-flow.mjs "$E/tasks" $w $h 2>&1 | tail -2
  echo "### auth-states $w"; node auth-states.mjs "$ORIGIN" "$E/auth" $w $h 2>&1 | tail -1
done
echo "### clients-states"; node clients-states.mjs "$E/clients" 2>&1 | tail -2
echo "### tasks-states"; node tasks-states.mjs "$E/tasks" 2>&1 | tail -2
echo "### demo-expiry"; node demo-expiry.mjs "$ORIGIN" "$E/demo" docker compose -f ../docker-compose.yml exec -T postgres psql -U minicrm -d minicrm
