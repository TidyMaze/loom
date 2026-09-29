#!/usr/bin/env bash
# Pull the app's usage log from the phone over adb and merge it into an append-only archive.
# The app keeps at most 20,000 events (UsageStore.MAX_EVENTS), so every pull is kept as an
# immutable file and master.json is rebuilt from all of them.
#
# Usage: scripts/pull_log.sh [archive_dir]      default: ~/work/analyses/loom/usage_log
# Device: $LOOM_ADB_SERIAL, else the wireless-debugging endpoint advertised over mDNS.
set -euo pipefail

PKG=com.yrolland.loom
ARCHIVE=${1:-$HOME/work/analyses/loom/usage_log}
ADB=${ADB:-/opt/homebrew/bin/adb}

serial=${LOOM_ADB_SERIAL:-}
if [ -z "$serial" ]; then
  serial=$("$ADB" mdns services | awk '/_adb-tls-connect/ {print $3; exit}')
  [ -n "$serial" ] || { echo "no device: set LOOM_ADB_SERIAL or enable wireless debugging" >&2; exit 1; }
  "$ADB" connect "$serial" >/dev/null
fi
[ "$("$ADB" -s "$serial" get-state 2>/dev/null)" = device ] || { echo "device $serial not ready" >&2; exit 1; }

mkdir -p "$ARCHIVE/pulls"
chmod 700 "$ARCHIVE" "$ARCHIVE/pulls"
out="$ARCHIVE/pulls/usage_log_$(date -u +%Y%m%dT%H%MZ).json"
tmp=$(mktemp "$ARCHIVE/pulls/.pull.XXXXXX")
trap 'rm -f "$tmp"' EXIT

"$ADB" -s "$serial" exec-out run-as "$PKG" cat files/usage_log.json > "$tmp"
jq -e 'type == "array" and all(.[]; has("packageName") and has("timestampMillis"))' "$tmp" >/dev/null \
  || { echo "pulled file is not a valid usage log" >&2; exit 1; }
mv "$tmp" "$out"
chmod 400 "$out"

jq -s 'add | group_by([.packageName, .timestampMillis]) | map(max_by(keys | length)) | sort_by(.timestampMillis)' \
  "$ARCHIVE"/pulls/usage_log_*.json > "$ARCHIVE/master.new"
new=$(jq length "$ARCHIVE/master.new")
old=0
[ -f "$ARCHIVE/master.json" ] && old=$(jq length "$ARCHIVE/master.json")
if [ "$new" -lt "$old" ]; then
  rm "$ARCHIVE/master.new"
  echo "merge shrank master ($old -> $new), kept the old one" >&2
  exit 1
fi
mv "$ARCHIVE/master.new" "$ARCHIVE/master.json"

echo "pulled $(jq length "$out") events -> $out"
echo "master: $new events, $(jq -r '[.[0].timestampMillis, .[-1].timestampMillis] | map(. / 1000 | floor | todate) | join(" .. ")' "$ARCHIVE/master.json")"
