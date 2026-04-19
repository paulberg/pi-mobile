# Pi Mobile Bridge as a systemd Service

This project ships a small systemd wrapper for running the bridge as a boot-time service.

## Files

- Unit template: `ops/systemd/pi-mobile-bridge@.service`
- Service env example: `ops/systemd/pi-mobile-bridge.env.example`
- Launcher script: `bridge/scripts/systemd-start.sh`

The launcher adds a few guardrails beyond a plain `pnpm start`:

- verifies `pi`, `node`, `corepack`, and `bridge/node_modules` exist
- requires `BRIDGE_AUTH_TOKEN`
- defaults `BRIDGE_SESSION_DIR` to `~/.pi/agent/sessions`
- auto-resolves `BRIDGE_HOST` from `tailscale ip -4` when it is omitted
- fails fast if neither `BRIDGE_HOST` nor a Tailscale IPv4 is available, so systemd can retry cleanly

The unit is templated by Linux username, so `pi-mobile-bridge@alice.service` runs the bridge as user `alice` and reads `/etc/pi-mobile-bridge/alice.env`.

## Install

From the repo root:

```bash
sudo install -d /etc/pi-mobile-bridge
sudo cp ops/systemd/pi-mobile-bridge@.service /etc/systemd/system/pi-mobile-bridge@.service
sudo cp ops/systemd/pi-mobile-bridge.env.example /etc/pi-mobile-bridge/<user>.env
sudoedit /etc/pi-mobile-bridge/<user>.env
sudo systemctl daemon-reload
sudo systemctl enable --now pi-mobile-bridge@<user>.service
```

At minimum, set these in `/etc/pi-mobile-bridge/<user>.env`:

- `PI_MOBILE_REPO_DIR=/absolute/path/to/pi-mobile`
- `BRIDGE_AUTH_TOKEN=...`

You can usually omit `BRIDGE_HOST` for the systemd service. The launcher will bind to the current Tailscale IPv4 automatically, which is more resilient than hardcoding a tailnet IP in the env file. If you want a local-only service without Tailscale, set `BRIDGE_HOST=127.0.0.1` explicitly.

## Verify

```bash
sudo systemctl status pi-mobile-bridge@<user>.service --no-pager -l
journalctl -u pi-mobile-bridge@<user>.service -f
curl http://$(tailscale ip -4 | head -n1):8787/health
```

If the health endpoint is disabled, skip the `curl` check and use `systemctl status` plus the journal instead.

## App connection settings

In Pi Mobile, use:

- Host: your laptop's MagicDNS name, for example `your-laptop.your-tailnet.ts.net`
- Port: `8787`
- TLS: off when connecting directly to the bridge over Tailscale
- Token: the `BRIDGE_AUTH_TOKEN` value from `/etc/pi-mobile-bridge/<user>.env`

## Updates

After pulling bridge changes:

```bash
cd /absolute/path/to/pi-mobile/bridge
corepack pnpm install
sudo systemctl restart pi-mobile-bridge@<user>.service
```

## Troubleshooting

### Service fails immediately

Check the journal first:

```bash
journalctl -u pi-mobile-bridge@<user>.service -n 100 --no-pager
```

Common causes:

- `PI_MOBILE_REPO_DIR` missing or wrong in `/etc/pi-mobile-bridge/<user>.env`
- `BRIDGE_AUTH_TOKEN` missing in `/etc/pi-mobile-bridge/<user>.env`
- `bridge/node_modules` missing after a fresh clone
- `pi` not installed or not on the service `PATH`

### Phone cannot connect

- verify both devices are on the same tailnet
- confirm the bridge is listening on the current Tailscale IPv4
- verify the token matches exactly
- prefer MagicDNS hostnames over raw IPs in the app
