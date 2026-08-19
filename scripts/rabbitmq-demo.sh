#!/usr/bin/env bash
#
# Demo and test tool for the SwiftTrack message broker.
#
# Everything the middleware does between services happens as a RabbitMQ
# message, but a healthy broker looks like nothing at all: queues sit empty
# because consumers drain them the instant a message lands. This script makes
# that invisible traffic visible.
#
#   ./scripts/rabbitmq-demo.sh topology   what exists: exchanges, queues, bindings
#   ./scripts/rabbitmq-demo.sh tap-on     start capturing a copy of every message
#   ./scripts/rabbitmq-demo.sh messages   print the captured messages
#   ./scripts/rabbitmq-demo.sh tap-off    stop capturing and delete the tap
#   ./scripts/rabbitmq-demo.sh test       place an order, prove each hop carried it
#   ./scripts/rabbitmq-demo.sh watch      live queue depths, refreshing
#
set -euo pipefail

API="http://localhost:15672/api"
AUTH="guest:guest"
GATEWAY="http://localhost:8080"

# Bound to # on both exchanges, so it receives a copy of every message without
# taking any away from the real consumers. Capped and time-limited: a queue
# nobody reads would otherwise grow until the broker runs out of memory.
TAP_QUEUE="swifttrack.demo-tap.q"
TAP_MAX_MESSAGES=200
TAP_TTL_MS=1800000   # 30 minutes

bold() { printf '\033[1m%s\033[0m\n' "$1"; }
dim()  { printf '\033[2m%s\033[0m\n' "$1"; }

require_broker() {
  if ! curl -s -m 3 -u "$AUTH" "$API/overview" > /dev/null; then
    echo "Cannot reach the RabbitMQ management API at $API"
    echo "Is the stack up?  docker compose up -d"
    exit 1
  fi
}

# --------------------------------------------------------------------------
# topology: what the middleware declared on startup
# --------------------------------------------------------------------------
cmd_topology() {
  require_broker

  bold "EXCHANGES"
  dim "Two topic exchanges. Commands are aimed at one adapter; events are"
  dim "announcements anyone may listen to."
  curl -s -u "$AUTH" "$API/exchanges" | python3 -c '
import sys, json
for e in json.load(sys.stdin):
    if e["name"].startswith("swifttrack"):
        print("  %-22s %s" % (e["name"], e["type"]))
'

  echo
  bold "QUEUES"
  dim "consumers=1 means a service is attached and listening. messages=0 on a"
  dim "healthy system: work is consumed as fast as it arrives."
  curl -s -u "$AUTH" "$API/queues" | python3 -c '
import sys, json
rows = sorted(json.load(sys.stdin), key=lambda q: q["name"])
print("  %-32s %10s %10s" % ("queue", "messages", "consumers"))
for q in rows:
    print("  %-32s %10d %10d" % (q["name"], q.get("messages", 0), q.get("consumers", 0)))
'

  echo
  bold "BINDINGS"
  dim "The routing rules: which routing key on which exchange reaches which queue."
  curl -s -u "$AUTH" "$API/bindings" | python3 -c '
import sys, json
rows = [b for b in json.load(sys.stdin) if b.get("source", "").startswith("swifttrack")]
for b in sorted(rows, key=lambda b: (b["source"], b["routing_key"])):
    print("  %-22s --[ %-26s ]--> %s" % (b["source"], b["routing_key"], b["destination"]))
'
}

# --------------------------------------------------------------------------
# tap: capture a copy of every message so the payloads can be shown
# --------------------------------------------------------------------------
cmd_tap_on() {
  require_broker

  curl -s -u "$AUTH" -X PUT "$API/queues/%2F/$TAP_QUEUE" \
    -H "content-type: application/json" \
    -d "{\"durable\":false,\"arguments\":{\"x-max-length\":$TAP_MAX_MESSAGES,\"x-message-ttl\":$TAP_TTL_MS}}"

  for exchange in swifttrack.events swifttrack.commands; do
    curl -s -u "$AUTH" -X POST "$API/bindings/%2F/e/$exchange/q/$TAP_QUEUE" \
      -H "content-type: application/json" -d '{"routing_key":"#"}'
  done

  bold "Tap on."
  dim "$TAP_QUEUE now receives a copy of every message on both exchanges."
  dim "It holds at most $TAP_MAX_MESSAGES messages and drops them after 30 minutes,"
  dim "and it takes nothing away from the real consumers."
  echo
  dim "Place some orders, then run:  ./scripts/rabbitmq-demo.sh messages"
  dim "Remember to run tap-off afterwards."
}

cmd_tap_off() {
  require_broker
  curl -s -u "$AUTH" -X DELETE "$API/queues/%2F/$TAP_QUEUE" > /dev/null
  bold "Tap off. $TAP_QUEUE deleted."
}

cmd_messages() {
  require_broker

  # ackmode=reject_requeue_true puts every message straight back, so this can
  # be run repeatedly and shown on screen without draining the capture.
  curl -s -u "$AUTH" -X POST "$API/queues/%2F/$TAP_QUEUE/get" \
    -H "content-type: application/json" \
    -d "{\"count\":$TAP_MAX_MESSAGES,\"ackmode\":\"reject_requeue_true\",\"encoding\":\"auto\"}" \
    | python3 -c '
import sys, json

try:
    msgs = json.load(sys.stdin)
except json.JSONDecodeError:
    print("No tap queue. Run:  ./scripts/rabbitmq-demo.sh tap-on")
    sys.exit(1)

if isinstance(msgs, dict) and msgs.get("error"):
    print("No tap queue. Run:  ./scripts/rabbitmq-demo.sh tap-on")
    sys.exit(1)

if not msgs:
    print("Nothing captured yet. Submit an order, then run this again.")
    sys.exit(0)

print("%d message(s) captured, oldest first:\n" % len(msgs))
for m in msgs:
    print("  \033[1m%-28s\033[0m via %s" % (m["routing_key"], m["exchange"]))
    try:
        body = json.dumps(json.loads(m["payload"]), indent=6)
        print("      " + body.strip()[1:-1].strip())
    except json.JSONDecodeError:
        print("      " + m["payload"])
    print()
'
}

# --------------------------------------------------------------------------
# test: place one order and prove every hop carried a message
# --------------------------------------------------------------------------
cmd_test() {
  require_broker

  bold "Signing in as client"
  local token
  token=$(curl -s -X POST "$GATEWAY/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"client","password":"swift2026"}' \
    | python3 -c 'import sys, json; print(json.load(sys.stdin)["token"])')
  dim "  got a token"

  echo
  bold "Message counts BEFORE"
  local before
  before=$(curl -s -u "$AUTH" "$API/queues")
  echo "$before" | python3 -c '
import sys, json
for q in sorted(json.load(sys.stdin), key=lambda q: q["name"]):
    print("  %-32s %6d delivered" % (q["name"], q.get("message_stats", {}).get("deliver_get", 0)))
'

  echo
  bold "Submitting an order through the gateway"
  local order_id
  order_id=$(curl -s -X POST "$GATEWAY/api/orders" \
    -H "Authorization: Bearer $token" \
    -H 'Content-Type: application/json' \
    -d '{"recipientName":"Broker Test","deliveryAddress":"1 Message Queue Lane, Colombo","packageDescription":"RabbitMQ demo parcel"}' \
    | python3 -c 'import sys, json; print(json.load(sys.stdin)["id"])')
  dim "  order #$order_id created"

  dim "  waiting for the saga to finish..."
  for _ in $(seq 1 40); do
    local status
    status=$(curl -s -H "Authorization: Bearer $token" "$GATEWAY/api/orders/$order_id" \
      | python3 -c 'import sys, json; print(json.load(sys.stdin)["status"])')
    if [ "$status" = "COMPLETED" ] || [ "$status" = "FAILED" ]; then break; fi
    sleep 0.5
  done
  dim "  order #$order_id finished as $status"

  # The management API samples its counters on a timer rather than updating
  # them per message, so reading them the instant the saga finishes shows a
  # half-counted picture. Give the sampler time to catch up.
  dim "  letting the broker's statistics catch up..."
  sleep 8

  echo
  bold "Message counts AFTER"
  curl -s -u "$AUTH" "$API/queues" > /tmp/swifttrack-after.json
  python3 - "$before" <<'PY'
import sys, json

before = {q["name"]: q.get("message_stats", {}).get("deliver_get", 0)
          for q in json.loads(sys.argv[1])}
after = {q["name"]: q.get("message_stats", {}).get("deliver_get", 0)
         for q in json.load(open("/tmp/swifttrack-after.json"))}

print("  %-32s %9s %9s %8s" % ("queue", "before", "after", "new"))
total = 0
for name in sorted(after):
    b, a = before.get(name, 0), after[name]
    delta = a - b
    total += delta
    mark = "  <--" if delta else ""
    print("  %-32s %9d %9d %8d%s" % (name, b, a, delta, mark))

print()
print("  %d messages carried the order end to end." % total)
print()
print("  Read the arrows down the list and you have the whole SAGA:")
print("    saga.order-created.q       the orchestrator was told an order exists")
print("    cms.commands.q             CMS was told to bill it")
print("    saga.step-results.q        CMS replied, and later WMS and ROS did too")
print("    wms.commands.q             WMS was told to reserve stock")
print("    ros.commands.q             ROS was told to plan a route")
print("    order.status.q             the Order Service was told, at every step")
PY
  rm -f /tmp/swifttrack-after.json
}

# --------------------------------------------------------------------------
# watch: live queue depths
# --------------------------------------------------------------------------
cmd_watch() {
  require_broker
  dim "Live queue depths. Ctrl-C to stop."
  while true; do
    clear
    bold "SwiftTrack queues — $(date '+%H:%M:%S')"
    echo
    curl -s -u "$AUTH" "$API/queues" | python3 -c '
import sys, json
rows = sorted(json.load(sys.stdin), key=lambda q: q["name"])
print("  %-32s %9s %10s %12s" % ("queue", "waiting", "consumers", "delivered"))
for q in rows:
    stats = q.get("message_stats", {})
    print("  %-32s %9d %10d %12d" % (
        q["name"], q.get("messages", 0), q.get("consumers", 0), stats.get("deliver_get", 0)))
'
    sleep 1
  done
}

case "${1:-}" in
  topology) cmd_topology ;;
  tap-on)   cmd_tap_on ;;
  tap-off)  cmd_tap_off ;;
  messages) cmd_messages ;;
  test)     cmd_test ;;
  watch)    cmd_watch ;;
  *)
    echo "Usage: $0 {topology|tap-on|messages|tap-off|test|watch}"
    echo
    echo "  topology   what exists: exchanges, queues, bindings"
    echo "  tap-on     start capturing a copy of every message"
    echo "  messages   print the captured messages with their payloads"
    echo "  tap-off    stop capturing and delete the tap"
    echo "  test       place an order and prove each hop carried it"
    echo "  watch      live queue depths, refreshing every second"
    exit 1
    ;;
esac
