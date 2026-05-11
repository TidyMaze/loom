#!/usr/bin/env python3
"""
Analyze Loom usage_log.json — replicate ScoreEngine.kt + visualize rankings.

Usage:
  python3 scripts/analyze.py /tmp/usage_log.json [options]

  --hour INT        Override current hour (default: now)
  --day INT         Override current day-of-week 1=Mon..7=Sun (default: today)
  --sigma FLOAT     Hour Gaussian sigma (default: 1.5)
  --half-life FLOAT Decay half-life in days (default: 7.0)
  --top INT         Show top N apps (default: 20)
  --sensitivity     Also show ±20% parameter sensitivity table
  --tune            Grid-search sigma/half-life to maximise top-1/3 hit rate
  --apply           Write best params back to ScoreEngine.kt (use with --tune)
"""

import argparse
import json
import math
import sys
from datetime import datetime, timezone
from pathlib import Path


def score(events, current_hour, current_dow, now_ms, sigma, half_life_days):
    """Replicate ScoreEngine.kt: Naive Bayes hour×day conditional with decay."""
    by_pkg = {}
    for e in events:
        by_pkg.setdefault(e["packageName"], []).append(e)

    scores = {}
    ln2 = math.log(2)
    for pkg, pkg_events in by_pkg.items():
        total_decay = 0.0
        hour_sum = 0.0
        day_sum = 0.0
        for ev in pkg_events:
            hour = ev["hour"]
            dow = ev["dayOfWeek"]
            ts = ev["timestampMillis"]

            diff = abs(hour - current_hour)
            hour_dist = 24 - diff if diff > 12 else diff
            hour_match = math.exp(-(hour_dist ** 2) / (2 * sigma * sigma))

            if dow == 0:
                day_match = 1.0
            elif dow == current_dow:
                day_match = 1.0
            elif (dow >= 6) == (current_dow >= 6):
                day_match = 0.6
            else:
                day_match = 0.2

            days_ago = (now_ms - ts) / 86_400_000
            decay = math.exp(-(days_ago / half_life_days) * ln2)

            total_decay += decay
            hour_sum += hour_match * decay
            day_sum += day_match * decay

        scores[pkg] = (hour_sum * day_sum / total_decay) if total_decay > 0 else 0.0

    return scores


def build_stats(events, now_ms):
    by_pkg = {}
    for e in events:
        by_pkg.setdefault(e["packageName"], []).append(e)

    stats = {}
    for pkg, evs in by_pkg.items():
        count = len(evs)
        last_ms = max(e["timestampMillis"] for e in evs)
        today_start = now_ms - 86_400_000
        today_count = sum(1 for e in evs if e["timestampMillis"] >= today_start)
        oldest_ms = min(e["timestampMillis"] for e in evs)
        span_days = max(1, (now_ms - oldest_ms) / 86_400_000)
        stats[pkg] = {
            "count": count,
            "last_ms": last_ms,
            "today": today_count,
            "daily_avg": count / span_days,
        }
    return stats


def fmt_last(last_ms, now_ms):
    diff = now_ms - last_ms
    if diff < 60_000:
        return "now"
    if diff < 3_600_000:
        return f"{int(diff/60_000)}m"
    if diff < 86_400_000:
        return f"{int(diff/3_600_000)}h"
    return f"{int(diff/86_400_000)}d"


def pkg_label(pkg):
    """Best-effort short name from package."""
    parts = pkg.split(".")
    # take last meaningful segment, capitalize
    for p in reversed(parts):
        if p not in ("android", "com", "org", "net", "app", "google"):
            return p.capitalize()
    return parts[-1]


def ranked_table(events, current_hour, current_dow, now_ms, sigma, half_life_days, top):
    raw = score(events, current_hour, current_dow, now_ms, sigma, half_life_days)
    stats = build_stats(events, now_ms)
    total = sum(v for v in raw.values() if v > 0) or 1.0

    rows = []
    for pkg, s in raw.items():
        st = stats.get(pkg, {})
        rows.append({
            "pkg": pkg,
            "label": pkg_label(pkg),
            "score": s,
            "pct": s / total * 100,
            "count": st.get("count", 0),
            "today": st.get("today", 0),
            "avg": st.get("daily_avg", 0),
            "last": fmt_last(st["last_ms"], now_ms) if "last_ms" in st else "—",
        })

    rows.sort(key=lambda r: -r["score"])
    return rows[:top]


def print_table(rows, sigma, half_life_days, current_hour, current_dow):
    print(f"\nParams: σ={sigma}  half-life={half_life_days}d  hour={current_hour}  dow={current_dow}")
    print(f"{'#':>3}  {'App':<22}  {'Score%':>7}  {'Total':>6}  {'Today':>5}  {'Avg/d':>5}  {'Last':>5}")
    print("-" * 65)
    for i, r in enumerate(rows, 1):
        avg_str = f"{r['avg']:.1f}"
        print(f"{i:>3}  {r['label']:<22}  {r['pct']:>6.2f}%  {r['count']:>6}  {r['today']:>5}  {avg_str:>5}  {r['last']:>5}")


def sensitivity_table(events, current_hour, current_dow, now_ms, sigma, half_life_days, top):
    base_rows = ranked_table(events, current_hour, current_dow, now_ms, sigma, half_life_days, top)
    base_rank = {r["pkg"]: i for i, r in enumerate(base_rows, 1)}

    variants = [
        ("σ×0.8", sigma * 0.8, half_life_days),
        ("σ×1.2", sigma * 1.2, half_life_days),
        ("hl×0.8", sigma, half_life_days * 0.8),
        ("hl×1.2", sigma, half_life_days * 1.2),
    ]

    print(f"\n{'App':<22}  {'Base':>4}", end="")
    for name, _, _ in variants:
        print(f"  {name:>6}", end="")
    print()
    print("-" * (22 + 6 + len(variants) * 8))

    for r in base_rows:
        pkg = r["pkg"]
        print(f"{r['label']:<22}  {base_rank[pkg]:>4}", end="")
        for _, s, hl in variants:
            vrows = ranked_table(events, current_hour, current_dow, now_ms, s, hl, top)
            vrank = {vr["pkg"]: i for i, vr in enumerate(vrows, 1)}
            delta = vrank.get(pkg, top + 1) - base_rank[pkg]
            arrow = f"{delta:+d}" if delta != 0 else "  ="
            print(f"  {arrow:>6}", end="")
        print()


def tune(events):
    """
    Time-series cross-validation: for each event[i], train on events[0..i-1],
    predict top app at event[i]'s (hour, dow), check if actual app is in top-1/3.
    Grid-search sigma × half_life to maximise top-1 hit rate.
    Min 20 prior events required before evaluating (cold-start skip).
    """
    events = sorted(events, key=lambda e: e["timestampMillis"])
    MIN_HISTORY = 20

    sigmas     = [0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0]
    half_lifes = [3.0, 5.0, 7.0, 10.0, 14.0, 21.0, 30.0]

    best = {"top1": -1, "params": None}
    results = []

    total_evals = len(events) - MIN_HISTORY
    print(f"Evaluating {total_evals} prediction points across "
          f"{len(sigmas) * len(half_lifes)} param combinations…")

    for sigma in sigmas:
        for hl in half_lifes:
            hits1 = hits3 = 0
            for i in range(MIN_HISTORY, len(events)):
                ev = events[i]
                history = events[:i]
                raw = score(history, ev["hour"], ev["dayOfWeek"],
                            ev["timestampMillis"], sigma, hl)
                if not raw:
                    continue
                ranked = sorted(raw, key=lambda p: -raw[p])
                if ranked[0] == ev["packageName"]:
                    hits1 += 1
                if ev["packageName"] in ranked[:3]:
                    hits3 += 1

            top1 = hits1 / total_evals * 100
            top3 = hits3 / total_evals * 100
            results.append((top1, top3, sigma, hl))
            if top1 > best["top1"]:
                best = {"top1": top1, "top3": top3, "params": (sigma, hl)}

    results.sort(reverse=True)
    print(f"\n{'σ':>5}  {'half-life':>9}  {'top-1%':>7}  {'top-3%':>7}")
    print("-" * 35)
    for top1, top3, s, hl in results[:15]:
        marker = " ◀" if (s, hl) == best["params"] else ""
        print(f"{s:>5.2f}  {hl:>9.1f}  {top1:>6.1f}%  {top3:>6.1f}%{marker}")

    print(f"\nBest: σ={best['params'][0]}  half-life={best['params'][1]}d  "
          f"top-1={best['top1']:.1f}%  top-3={best['top3']:.1f}%")
    print(f"Default: σ=1.5  half-life=7.0d")
    return best["params"]


def apply_params(sigma, half_life, kt_path):
    text = kt_path.read_text()
    import re
    text = re.sub(r'(HOUR_SIGMA\s*=\s*)[\d.]+f', f'\\g<1>{sigma}f', text)
    text = re.sub(r'(DECAY_HALF_LIFE_DAYS\s*=\s*)[\d.]+f', f'\\g<1>{half_life}f', text)
    kt_path.write_text(text)
    print(f"Updated {kt_path}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("file", help="Path to usage_log.json")
    parser.add_argument("--hour", type=int, default=None)
    parser.add_argument("--day", type=int, default=None)
    parser.add_argument("--sigma", type=float, default=1.5)
    parser.add_argument("--half-life", type=float, default=7.0, dest="half_life")
    parser.add_argument("--top", type=int, default=20)
    parser.add_argument("--sensitivity", action="store_true")
    parser.add_argument("--tune", action="store_true")
    parser.add_argument("--apply", action="store_true", help="Write best params to ScoreEngine.kt (requires --tune)")
    args = parser.parse_args()

    data = json.loads(Path(args.file).read_text())
    now_ms = int(datetime.now(timezone.utc).timestamp() * 1000)
    now_local = datetime.now()
    current_hour = args.hour if args.hour is not None else now_local.hour
    current_dow = args.day if args.day is not None else now_local.isoweekday()  # 1=Mon..7=Sun

    print(f"Events: {len(data)}  Unique apps: {len({e['packageName'] for e in data})}")

    rows = ranked_table(data, current_hour, current_dow, now_ms, args.sigma, args.half_life, args.top)
    print_table(rows, args.sigma, args.half_life, current_hour, current_dow)

    if args.tune:
        best_sigma, best_hl = tune(data)
        if args.apply:
            kt = Path(__file__).parent.parent / "app/src/main/kotlin/com/yrolland/loom/ScoreEngine.kt"
            apply_params(best_sigma, best_hl, kt)
        return

    if args.sensitivity:
        print("\n=== Rank sensitivity (delta vs base) ===")
        sensitivity_table(data, current_hour, current_dow, now_ms, args.sigma, args.half_life, args.top)


if __name__ == "__main__":
    main()
