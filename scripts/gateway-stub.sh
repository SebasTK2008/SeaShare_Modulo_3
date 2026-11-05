#!/usr/bin/env bash
# Control del simulador de la pasarela (gateway-stub, WireMock). Modo A de QA.
#
#   scripts/gateway-stub.sh set <escenario>   activa wiremock-gateway/scenarios/<escenario>.json
#   scripts/gateway-stub.sh reset             vuelve al estado por defecto (pagos y reembolsos aprobados);
#                                              TAMBIEN vacia el request journal: consultelo antes
#   scripts/gateway-stub.sh list              lista los escenarios disponibles
#   scripts/gateway-stub.sh journal [METODO] [RUTA]
#                                              request journal (todos, o filtrado por metodo y ruta)
#   scripts/gateway-stub.sh clear-journal     vacia el request journal
#
# Variables: GATEWAY_STUB_URL (por defecto http://localhost:${GATEWAY_STUB_PORT:-8082}).
# Los escenarios tienen prioridad 1 y ganan sobre el estado por defecto (prioridad 10) hasta el reset.
set -euo pipefail

BASE_URL="${GATEWAY_STUB_URL:-http://localhost:${GATEWAY_STUB_PORT:-8082}}"
SCENARIOS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/wiremock-gateway/scenarios"

usage() { sed -n "2,13p" "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 1; }

command="${1:-}"
case "$command" in
  set)
    scenario="${2:-}"
    [ -n "$scenario" ] || usage
    file="$SCENARIOS_DIR/$scenario.json"
    [ -f "$file" ] || { echo "Escenario desconocido: $scenario (use 'list')" >&2; exit 1; }
    curl -fsS -X POST "$BASE_URL/__admin/mappings" -H 'Content-Type: application/json' --data-binary "@$file" > /dev/null
    echo "Escenario activo: $scenario"
    ;;
  reset)
    curl -fsS -X POST "$BASE_URL/__admin/mappings/reset" > /dev/null
    echo "Estado por defecto restaurado"
    ;;
  list)
    for f in "$SCENARIOS_DIR"/*.json; do basename "$f" .json; done
    ;;
  journal)
    if [ -n "${2:-}" ] && [ -n "${3:-}" ]; then
      curl -fsS -X POST "$BASE_URL/__admin/requests/find" -H 'Content-Type: application/json' \
        -d "{\"method\":\"$2\",\"urlPath\":\"$3\"}"
    else
      curl -fsS "$BASE_URL/__admin/requests?limit=50"
    fi
    echo
    ;;
  clear-journal)
    curl -fsS -X DELETE "$BASE_URL/__admin/requests" > /dev/null
    echo "Request journal vacio"
    ;;
  *)
    usage
    ;;
esac
