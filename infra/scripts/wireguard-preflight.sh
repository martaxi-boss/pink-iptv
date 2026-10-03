#!/usr/bin/env bash
# Read-only host facts. Never output configs, keys, environments or journal bodies.
set -euo pipefail

port="${PINK_PREFLIGHT_UDP_PORT:-51820}"
if [[ ! "$port" =~ ^[0-9]{1,5}$ ]] || (( 10#$port < 1 || 10#$port > 65535 )); then
  printf 'PREFLIGHT_INPUT=INVALID_PORT\n' >&2
  exit 2
fi
printf 'PREFLIGHT_SCHEMA=1\n'
printf 'OBSERVED_AT_UTC=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
printf 'MUTATION_PERFORMED=NO\n'
printf 'ROLLBACK_CERTIFIED=NO\n'
printf 'PROVIDER_OR_TUNNEL_PROOF=NO\n'
printf 'KERNEL=%s\n' "$(uname -r)"

for unit in ssh.service nginx.service postgresql.service pink-iptv.service pink-iptv-backend.service; do
  if command -v systemctl >/dev/null 2>&1; then
    state="$(systemctl is-active "$unit" 2>/dev/null || true)"
    printf 'SERVICE[%s]=%s\n' "$unit" "${state:-UNKNOWN}"
  else
    printf 'SERVICE[%s]=UNKNOWN_NO_SYSTEMCTL\n' "$unit"
  fi
done
# Unit naming is not asserted by historical docs; discovery emits unit names/state only.
if command -v systemctl >/dev/null 2>&1; then
  systemctl list-units --all --type=service --no-legend --no-pager 'pink*' 2>/dev/null |
    awk '{print "PINK_UNIT=" $1 ":" $3 ":" $4}' || true
fi

if command -v ss >/dev/null 2>&1; then
  if listeners="$(ss -H -l -t -n 'sport = :8010' 2>/dev/null)"; then
    if [[ -z "$listeners" ]]; then
      printf 'PINK_BACKEND_PORT_8010=NO_LISTENER\n'
    elif awk '{print $4}' <<< "$listeners" | grep -Eq '^(127\.0\.0\.1|\[::1\]):8010$'; then
      if awk '{print $4}' <<< "$listeners" | grep -Evq '^(127\.0\.0\.1|\[::1\]):8010$'; then
        printf 'PINK_BACKEND_PORT_8010=MIXED_OR_NON_LOOPBACK\n'
      else
        printf 'PINK_BACKEND_PORT_8010=LOOPBACK_ONLY\n'
      fi
    else
      printf 'PINK_BACKEND_PORT_8010=NON_LOOPBACK\n'
    fi
  else
    printf 'PINK_BACKEND_PORT_8010=UNKNOWN_QUERY_FAILED\n'
  fi
  if udp="$(ss -H -l -u -n "sport = :$port" 2>/dev/null)"; then
    if [[ -z "$udp" ]]; then
      printf 'CANDIDATE_UDP_PORT_%s=NO_LISTENER_OBSERVED\n' "$port"
    else
      printf 'CANDIDATE_UDP_PORT_%s=LISTENER_PRESENT\n' "$port"
    fi
  else
    printf 'CANDIDATE_UDP_PORT_%s=UNKNOWN_QUERY_FAILED\n' "$port"
  fi
else
  printf 'PINK_BACKEND_PORT_8010=UNKNOWN_NO_SS\n'
  printf 'CANDIDATE_UDP_PORT_%s=UNKNOWN_NO_SS\n' "$port"
fi

for pair in IPV4_FORWARDING:/proc/sys/net/ipv4/ip_forward IPV6_FORWARDING:/proc/sys/net/ipv6/conf/all/forwarding; do
  label="${pair%%:*}"; source_path="${pair#*:}"
  if [[ -r "$source_path" ]]; then
    read -r value < "$source_path"
    printf '%s=%s\n' "$label" "$value"
  else
    printf '%s=UNKNOWN\n' "$label"
  fi
done
if [[ -e /dev/net/tun ]]; then printf 'TUN_DEVICE=PRESENT\n'; else printf 'TUN_DEVICE=ABSENT\n'; fi
for binary in wg nft iptables; do
  if command -v "$binary" >/dev/null 2>&1; then
    printf 'TOOL_%s=PRESENT\n' "$binary"
  else
    printf 'TOOL_%s=ABSENT\n' "$binary"
  fi
done
# Filesystems/available resources are facts, never a VPN capacity certificate.
df -Pk / | awk 'NR==2 {print "ROOT_FREE_KB=" $4}'
awk '/^MemAvailable:/ {print "MEM_AVAILABLE_KB=" $2}' /proc/meminfo
printf 'REMOTE_EXECUTION_CONTEXT_REQUIRED=AUTHORIZED_OVH_HOST\n'
printf 'SUPERVISOR_REVIEW_REQUIRED=YES\n'
