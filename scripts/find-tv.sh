#!/usr/bin/env bash
# Finds Android TV / Google TV sets on the local network (/24 around this computer's IP).
# Prints: <ip>  adb:open|closed  <cast name, which usually includes brand + model>
source "$(dirname "$0")/lib.sh"

probe() {
  local ip="$1" adb=closed name=""
  if [[ "$(uname)" == Darwin ]]; then NC=(nc -z -G 1 -w 1); else NC=(nc -z -w 1); fi   # macOS needs -G for a connect timeout
  "${NC[@]}" "$ip" 5555 >/dev/null 2>&1 && adb=open
  if "${NC[@]}" "$ip" 8008 >/dev/null 2>&1; then
    name="$(curl -s -m 3 "http://$ip:8008/setup/eureka_info?params=name" \
      | grep -oE '"name":"[^"]*"' | head -1 | cut -d'"' -f4)"
    [[ -n "$name" ]] || name="(cast device)"
  fi
  if [[ "$adb" == open || -n "$name" ]]; then printf '%-15s  adb:%-6s  %s\n' "$ip" "$adb" "$name"; fi
}

if [[ "${1:-}" == "--probe" ]]; then probe "$2"; exit 0; fi

my_ip=""
if command -v ipconfig >/dev/null 2>&1; then
  for ifc in en0 en1 en2; do my_ip="$(ipconfig getifaddr "$ifc" 2>/dev/null || true)"; [[ -n "$my_ip" ]] && break; done
fi
if [[ -z "$my_ip" ]] && command -v ip >/dev/null 2>&1; then
  my_ip="$(ip route get 1.1.1.1 2>/dev/null | grep -oE 'src [0-9.]+' | awk '{print $2}')"
fi
[[ -n "$my_ip" ]] || die "couldn't work out this computer's LAN IP; pass a subnet prefix, e.g. $0 192.168.1"

prefix="${1:-${my_ip%.*}}"
echo "This computer: $my_ip. Scanning $prefix.1-254 for TVs (about 10s)..." >&2
seq 1 254 | sed "s/^/$prefix./" | xargs -P 48 -I{} "$0" --probe {} | sort -t. -k4 -n
