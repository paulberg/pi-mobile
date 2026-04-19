#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BRIDGE_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"

require_bin() {
  local name="$1"
  if ! command -v "$name" >/dev/null 2>&1; then
    echo "pi-mobile-bridge: missing required command: $name" >&2
    exit 1
  fi
}

cd "$BRIDGE_DIR"

require_bin node
require_bin corepack
require_bin pi

if [[ ! -f package.json ]]; then
  echo "pi-mobile-bridge: missing $BRIDGE_DIR/package.json" >&2
  exit 1
fi

if [[ ! -d node_modules ]]; then
  echo "pi-mobile-bridge: missing $BRIDGE_DIR/node_modules; run 'cd $BRIDGE_DIR && corepack pnpm install'" >&2
  exit 1
fi

if [[ -z "${BRIDGE_AUTH_TOKEN:-}" ]]; then
  echo "pi-mobile-bridge: BRIDGE_AUTH_TOKEN is required; set it in /etc/pi-mobile-bridge.env" >&2
  exit 1
fi

export BRIDGE_PORT="${BRIDGE_PORT:-8787}"
export BRIDGE_LOG_LEVEL="${BRIDGE_LOG_LEVEL:-info}"
export BRIDGE_ENABLE_HEALTH_ENDPOINT="${BRIDGE_ENABLE_HEALTH_ENDPOINT:-true}"
export BRIDGE_SESSION_DIR="${BRIDGE_SESSION_DIR:-$HOME/.pi/agent/sessions}"

TAILSCALE_IPV4=""
if command -v tailscale >/dev/null 2>&1; then
  TAILSCALE_IPV4="$(tailscale ip -4 2>/dev/null | head -n1 || true)"
fi

if [[ -z "${BRIDGE_HOST:-}" ]]; then
  if [[ -n "$TAILSCALE_IPV4" ]]; then
    export BRIDGE_HOST="$TAILSCALE_IPV4"
    echo "pi-mobile-bridge: resolved BRIDGE_HOST from tailscale ip -4 -> $BRIDGE_HOST" >&2
  else
    echo "pi-mobile-bridge: BRIDGE_HOST is unset and no Tailscale IPv4 is available; set BRIDGE_HOST explicitly or wait for tailscaled" >&2
    exit 1
  fi
elif [[ -n "$TAILSCALE_IPV4" && "$BRIDGE_HOST" != "$TAILSCALE_IPV4" && "$BRIDGE_HOST" != "127.0.0.1" && "$BRIDGE_HOST" != "0.0.0.0" ]]; then
  echo "pi-mobile-bridge: warning: configured BRIDGE_HOST=$BRIDGE_HOST differs from current Tailscale IPv4 $TAILSCALE_IPV4" >&2
fi

exec /usr/bin/corepack pnpm start
