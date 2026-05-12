#!/usr/bin/env python3
"""Inspect collected Phase-1 context features from device events."""
import json, subprocess, sys, collections
from datetime import datetime

def pull():
    r = subprocess.run(
        ["adb","shell","run-as","com.yrolland.loom","cat","files/usage_log.json"],
        capture_output=True, text=True
    )
    if r.returncode != 0 or not r.stdout.strip():
        sys.exit(f"adb pull failed: {r.stderr}")
    return json.loads(r.stdout)

def main():
    events = pull()
    with_ctx = [e for e in events if e.get("secsSinceResume") is not None]
    print(f"Total events: {len(events)}")
    print(f"With Phase-1 context: {len(with_ctx)} ({len(with_ctx)/len(events)*100:.1f}%)")
    if not with_ctx:
        print("\nNo Phase-1 events yet. Launch some apps via the launcher first.")
        return

    print("\n=== Recent context-rich events ===")
    for e in with_ctx[-10:]:
        ts = datetime.fromtimestamp(e["timestampMillis"]/1000).strftime("%m-%d %H:%M:%S")
        print(f"  {ts}  {e['packageName']:<35}  secs={e.get('secsSinceResume'):>3}  audio={str(e.get('audioActive')):>5}  dev={e.get('audioDevice'):<8}  charging={e.get('charging')}")

    print("\n=== Feature distributions ===")
    secs = [e['secsSinceResume'] for e in with_ctx]
    secs.sort()
    if secs:
        print(f"secsSinceResume:  min={secs[0]}  p50={secs[len(secs)//2]}  p90={secs[int(len(secs)*0.9)]}  max={secs[-1]}")
    audio = collections.Counter(e['audioActive'] for e in with_ctx)
    print(f"audioActive:      {dict(audio)}")
    dev = collections.Counter(e['audioDevice'] for e in with_ctx)
    print(f"audioDevice:      {dict(dev)}")
    chg = collections.Counter(e['charging'] for e in with_ctx)
    print(f"charging:         {dict(chg)}")

    # Per-app feature correlations
    print("\n=== Per-app context profile (top 10 by launches) ===")
    by_pkg = collections.defaultdict(list)
    for e in with_ctx:
        by_pkg[e['packageName']].append(e)
    top = sorted(by_pkg.items(), key=lambda x: -len(x[1]))[:10]
    print(f"  {'App':<35}  {'n':>3}  {'audio%':>6}  {'BT%':>5}  {'wired%':>6}  {'chg%':>5}  {'p50_secs':>8}")
    for pkg, evs in top:
        n = len(evs)
        audio_pct = sum(1 for e in evs if e['audioActive'])/n*100
        bt_pct    = sum(1 for e in evs if e['audioDevice']=='bt')/n*100
        wired_pct = sum(1 for e in evs if e['audioDevice']=='wired')/n*100
        chg_pct   = sum(1 for e in evs if e['charging'])/n*100
        secs_sorted = sorted(e['secsSinceResume'] for e in evs)
        p50 = secs_sorted[len(secs_sorted)//2]
        print(f"  {pkg[:35]:<35}  {n:>3}  {audio_pct:>5.0f}%  {bt_pct:>4.0f}%  {wired_pct:>5.0f}%  {chg_pct:>4.0f}%  {p50:>8}")

if __name__ == "__main__":
    main()
