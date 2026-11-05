#!/usr/bin/env bash
# Starts the working loop every minute with a systemd user timer on this machine.
# A round with no work makes no call to Claude. The timer runs while the user is logged in.
# To stop it: systemctl --user disable --now makemytaxes-agent.timer
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
units=${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user
mkdir -p "$units"

cat >"$units/makemytaxes-agent.service" <<UNIT
[Unit]
Description=makeMyTaxes: one round of the working loop

[Service]
Type=oneshot
WorkingDirectory=$root
# A timer has no login shell, so the unit keeps the tools from the shell that installed it.
Environment=PATH=$PATH
# This private file holds CLAUDE_CODE_OAUTH_TOKEN for unattended runs. It stays outside the repository.
EnvironmentFile=-%h/.config/makemytaxes/agent.env
ExecStart=$root/scripts/agent-loop.sh
UNIT

cat >"$units/makemytaxes-agent.timer" <<UNIT
[Unit]
Description=makeMyTaxes: start the working loop every minute

[Timer]
OnBootSec=1min
OnUnitInactiveSec=1min
AccuracySec=5s

[Install]
WantedBy=timers.target
UNIT

systemctl --user daemon-reload
systemctl --user enable --now makemytaxes-agent.timer
echo "The working loop now starts every minute. Logs: journalctl --user -u makemytaxes-agent"
