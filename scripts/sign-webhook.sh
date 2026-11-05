#!/usr/bin/env bash
# Envia a la app una notificacion de la pasarela con x-signature valida (Modo A de QA).
#
#   scripts/sign-webhook.sh <payment_id> [type] [action]
#
#   payment_id  id del pago o reembolso en la pasarela (data.id), p. ej. 1234567890
#   type        por defecto "payment"
#   action      por defecto "payment.updated"
#
# La firma es la de Mercado Pago: HMAC-SHA256 de "id:<data.id>;request-id:<x-request-id>;ts:<ts>;"
# con el secreto del webhook (mismo formato que MercadoPagoSignatureVerifier).
# Variables: GATEWAY_WEBHOOK_SECRET (por defecto dev-webhook-secret), APP_URL
# (por defecto http://localhost:${APP_PORT:-8080}). Requiere curl y openssl.
set -euo pipefail

data_id="${1:-}"
[ -n "$data_id" ] || { sed -n '2,13p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 1; }
type="${2:-payment}"
action="${3:-payment.updated}"
secret="${GATEWAY_WEBHOOK_SECRET:-dev-webhook-secret}"
app_url="${APP_URL:-http://localhost:${APP_PORT:-8080}}"

request_id="$(openssl rand -hex 16)"
ts="$(date +%s)"
message="id:${data_id};request-id:${request_id};ts:${ts};"
hash="$(printf '%s' "$message" | openssl dgst -sha256 -hmac "$secret" | sed 's/^.* //')"

curl -sS -w '\nHTTP %{http_code}\n' -X POST "${app_url}/api/v1/webhook/gateway?data.id=${data_id}&type=${type}" \
  -H 'Content-Type: application/json' \
  -H "x-request-id: ${request_id}" \
  -H "x-signature: ts=${ts},v1=${hash}" \
  -d "{\"action\":\"${action}\",\"type\":\"${type}\",\"data\":{\"id\":\"${data_id}\"}}"
