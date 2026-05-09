"""
AI Launcher — usage stats report.
Pull data from device, score it in Python, render 6 diagnostic charts as HTML.
Usage: uv run --with matplotlib python3 scripts/report.py
"""

import base64
import collections
import datetime
import io
import json
import subprocess
import sys
import time
import webbrowser

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

# ---------------------------------------------------------------------------
# 1. Pull data from device
# ---------------------------------------------------------------------------

def pull_events():
    result = subprocess.run(
        ["adb", "shell", "run-as", "com.example.ailauncher", "cat", "files/usage_log.json"],
        capture_output=True, text=True
    )
    if result.returncode != 0 or not result.stdout.strip():
        sys.exit(f"Failed to pull usage_log.json: {result.stderr}")
    return json.loads(result.stdout)

# ---------------------------------------------------------------------------
# 2. ScoreEngine — Python port of ScoreEngine.kt
# ---------------------------------------------------------------------------

HOUR_MATCH_WEIGHT = 1.0
HOUR_MISS_WEIGHT  = 0.15
HOUR_TOLERANCE    = 1
DECAY_HALF_LIFE   = 7.0

def is_weekend(dow):
    return dow >= 6

def compute_scores(events, now_hour, now_dow, now_ms):
    by_pkg = collections.defaultdict(list)
    for e in events:
        by_pkg[e["packageName"]].append(e)
    scores = {}
    for pkg, evts in by_pkg.items():
        total = 0.0
        for e in evts:
            diff = abs(e["hour"] - now_hour)
            hour_dist = min(diff, 24 - diff)
            hour_match = HOUR_MATCH_WEIGHT if hour_dist <= HOUR_TOLERANCE else HOUR_MISS_WEIGHT
            dow = e.get("dayOfWeek", 0)
            if dow == 0 or dow == now_dow:
                day_match = 1.0
            elif is_weekend(dow) == is_weekend(now_dow):
                day_match = 0.6
            else:
                day_match = 0.2
            days_ago = (now_ms - e["timestampMillis"]) / 86_400_000
            decay = 0.5 ** (days_ago / DECAY_HALF_LIFE)
            total += hour_match * day_match * decay
        scores[pkg] = total
    return scores

# ---------------------------------------------------------------------------
# 3. Helpers
# ---------------------------------------------------------------------------

_PKG_ALIASES = {
    "com.google.android.apps.dynamite": "Chat",
    "com.google.android.apps.messaging": "Messages",
    "com.google.android.apps.photos": "Photos",
    "com.google.android.apps.maps": "Maps",
    "com.google.android.gm": "Gmail",
    "com.google.android.youtube": "YouTube",
    "com.android.chrome": "Chrome",
    "com.facebook.orca": "Messenger",
    "com.facebook.katana": "Facebook",
    "com.whatsapp": "WhatsApp",
    "com.instagram.android": "Instagram",
    "com.twitter.android": "Twitter",
    "com.spotify.music": "Spotify",
    "com.google.android.calendar": "Calendar",
    "com.google.android.contacts": "Contacts",
    "com.google.android.deskclock": "Clock",
    "com.android.settings": "Settings",
}

def short_name(pkg):
    if pkg in _PKG_ALIASES:
        return _PKG_ALIASES[pkg]
    parts = pkg.split(".")
    return parts[-1] if parts[-1] not in ("android", "app") else parts[-2]

def fig_to_b64(fig):
    buf = io.BytesIO()
    fig.savefig(buf, format="png", dpi=130, bbox_inches="tight")
    plt.close(fig)
    return base64.b64encode(buf.getvalue()).decode()

DARK_BG   = "#111111"
ACCENT    = "#DDDDDD"
GRID      = "#2a2a2a"
BAR_COLOR = "#888888"

def dark_fig(w=9, h=4.5):
    fig, ax = plt.subplots(figsize=(w, h), facecolor=DARK_BG)
    ax.set_facecolor(DARK_BG)
    for spine in ax.spines.values():
        spine.set_edgecolor(GRID)
    ax.tick_params(colors=ACCENT)
    ax.xaxis.label.set_color(ACCENT)
    ax.yaxis.label.set_color(ACCENT)
    ax.title.set_color(ACCENT)
    ax.grid(color=GRID, linewidth=0.5)
    return fig, ax

# ---------------------------------------------------------------------------
# 4. Charts
# ---------------------------------------------------------------------------

def chart_score_ranking(scores):
    max_score = max(scores.values()) or 1
    ranked = sorted(scores.items(), key=lambda x: -x[1])[:15]
    names  = [short_name(p) for p, _ in ranked]
    vals   = [s / max_score for _, s in ranked]

    fig, ax = dark_fig(9, 5)
    bars = ax.barh(names[::-1], vals[::-1], color=BAR_COLOR, height=0.6)
    # highlight top 3
    for bar in bars[-3:]:
        bar.set_color(ACCENT)
    ax.set_xlim(0, 1.05)
    ax.set_xlabel("Normalised score")
    ax.set_title("Current score ranking (top 15)")
    ax.axvline(0.5, color=GRID, linewidth=1, linestyle="--")
    return fig_to_b64(fig)


def chart_launches_by_hour(events):
    hours = [0] * 24
    for e in events:
        hours[e["hour"]] += 1

    fig, ax = dark_fig(10, 4)
    ax.bar(range(24), hours, color=BAR_COLOR, width=0.8)
    now_h = datetime.datetime.now().hour
    ax.axvline(now_h, color="#FF6B6B", linewidth=1.5, linestyle="--", label=f"now ({now_h}h)")
    ax.set_xticks(range(24))
    ax.set_xlabel("Hour of day")
    ax.set_ylabel("Launch count")
    ax.set_title("Launches by hour of day")
    ax.legend(facecolor=DARK_BG, edgecolor=GRID, labelcolor=ACCENT)
    return fig_to_b64(fig)


def chart_app_hour_heatmap(events):
    top_pkgs = [p for p, _ in collections.Counter(e["packageName"] for e in events).most_common(8)]
    names = [short_name(p) for p in top_pkgs]

    matrix = np.zeros((len(top_pkgs), 24))
    for e in events:
        if e["packageName"] in top_pkgs:
            row = top_pkgs.index(e["packageName"])
            matrix[row][e["hour"]] += 1

    fig, ax = plt.subplots(figsize=(11, 4), facecolor=DARK_BG)
    ax.set_facecolor(DARK_BG)
    im = ax.imshow(matrix, aspect="auto", cmap="Blues", interpolation="nearest")
    ax.set_xticks(range(24))
    ax.set_xticklabels(range(24), color=ACCENT, fontsize=8)
    ax.set_yticks(range(len(names)))
    ax.set_yticklabels(names, color=ACCENT)
    ax.set_xlabel("Hour of day", color=ACCENT)
    ax.set_title("App × hour heatmap (top 8 apps)", color=ACCENT)
    now_h = datetime.datetime.now().hour
    ax.axvline(now_h - 0.5, color="#FF6B6B", linewidth=1.5, linestyle="--")
    for spine in ax.spines.values():
        spine.set_edgecolor(GRID)
    cb = fig.colorbar(im, ax=ax)
    cb.ax.tick_params(colors=ACCENT)
    return fig_to_b64(fig)


def chart_decay_curve():
    days = np.linspace(0, 28, 200)
    score = 0.5 ** (days / DECAY_HALF_LIFE)

    fig, ax = dark_fig(8, 4)
    ax.plot(days, score, color=ACCENT, linewidth=2)
    ax.axhline(0.5, color=GRID, linewidth=1, linestyle="--")
    ax.axvline(7,   color="#888888", linewidth=1, linestyle=":")
    ax.axvline(14,  color="#888888", linewidth=1, linestyle=":")
    ax.text(7.2,  0.05, "7d",  color=ACCENT, fontsize=9)
    ax.text(14.2, 0.05, "14d", color=ACCENT, fontsize=9)
    ax.set_xlabel("Days since last launch")
    ax.set_ylabel("Decay factor")
    ax.set_title(f"Score decay curve (half-life = {int(DECAY_HALF_LIFE)} days)")
    ax.set_ylim(0, 1.05)
    return fig_to_b64(fig)


def chart_day_of_week(events):
    days = [0] * 8  # index 1-7
    for e in events:
        dow = e.get("dayOfWeek", 0)
        if 1 <= dow <= 7:
            days[dow] += 1
    labels = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
    vals = days[1:8]

    fig, ax = dark_fig(7, 4)
    colors = [BAR_COLOR] * 5 + ["#555555", "#555555"]
    ax.bar(labels, vals, color=colors, width=0.6)
    now_dow = datetime.datetime.now().isoweekday()
    ax.get_children()[now_dow - 1].set_color(ACCENT)
    ax.set_ylabel("Launch count")
    ax.set_title("Launches by day of week")
    return fig_to_b64(fig)


def chart_score_vs_recency(scores, events):
    now_ms = int(time.time() * 1000)
    last_by_pkg = {}
    for e in events:
        pkg = e["packageName"]
        if pkg not in last_by_pkg or e["timestampMillis"] > last_by_pkg[pkg]:
            last_by_pkg[pkg] = e["timestampMillis"]

    max_score = max(scores.values()) or 1
    xs, ys, names = [], [], []
    for pkg, score in scores.items():
        if pkg in last_by_pkg:
            days_ago = (now_ms - last_by_pkg[pkg]) / 86_400_000
            xs.append(days_ago)
            ys.append(score / max_score)
            names.append(short_name(pkg))

    fig, ax = dark_fig(8, 4.5)
    ax.scatter(xs, ys, color=ACCENT, alpha=0.7, s=60, zorder=3)
    for x, y, n in zip(xs, ys, names):
        ax.annotate(n, (x, y), textcoords="offset points", xytext=(4, 3),
                    color=ACCENT, fontsize=7, alpha=0.8)
    # reference decay curve
    ref_x = np.linspace(0, max(xs) + 0.5, 100)
    ref_y = 0.5 ** (ref_x / DECAY_HALF_LIFE)
    ax.plot(ref_x, ref_y, color="#FF6B6B", linewidth=1, linestyle="--", label="pure decay ref", zorder=2)
    ax.set_xlabel("Days since last launch")
    ax.set_ylabel("Normalised score")
    ax.set_title("Score vs recency (confirms decay)")
    ax.legend(facecolor=DARK_BG, edgecolor=GRID, labelcolor=ACCENT)
    ax.set_ylim(0, 1.05)
    return fig_to_b64(fig)

def chart_score_forecast(events):
    """
    Simulate score for each top-5 app at every hour over the next 7 days.
    Uses the exact ScoreEngine formula with real events; only 'now' advances.
    """
    now_ms = int(time.time() * 1000)
    counts = collections.Counter(e["packageName"] for e in events)
    top_pkgs = [p for p, _ in counts.most_common(5)]
    by_pkg = {pkg: [e for e in events if e["packageName"] == pkg] for pkg in top_pkgs}

    # one data point per hour over 7 days = 168 points
    hours_ahead = list(range(7 * 24 + 1))
    xs = [h / 24 for h in hours_ahead]  # x axis in days

    series = {}
    for pkg, evts in by_pkg.items():
        ys = []
        for h in hours_ahead:
            future_ms  = now_ms + h * 3_600_000
            future_dt  = datetime.datetime.fromtimestamp(future_ms / 1000)
            future_h   = future_dt.hour
            future_dow = future_dt.isoweekday()
            total = 0.0
            for e in evts:
                diff = abs(e["hour"] - future_h)
                hour_dist  = min(diff, 24 - diff)
                hour_match = HOUR_MATCH_WEIGHT if hour_dist <= HOUR_TOLERANCE else HOUR_MISS_WEIGHT
                dow = e.get("dayOfWeek", 0)
                if dow == 0 or dow == future_dow:
                    day_match = 1.0
                elif is_weekend(dow) == is_weekend(future_dow):
                    day_match = 0.6
                else:
                    day_match = 0.2
                days_ago = (future_ms - e["timestampMillis"]) / 86_400_000
                decay = 0.5 ** (days_ago / DECAY_HALF_LIFE)
                total += hour_match * day_match * decay
            ys.append(total)
        series[pkg] = ys

    # normalise by current max score so y-axis is comparable to ranking chart
    max_score = max(v[0] for v in series.values()) or 1

    fig, ax = dark_fig(12, 5)
    colors = ["#FFFFFF", "#AAAAAA", "#888888", "#555555", "#333333"]
    for (pkg, ys), color in zip(series.items(), colors):
        norm_ys = [y / max_score for y in ys]
        ax.plot(xs, norm_ys, label=short_name(pkg), color=color, linewidth=1.5)

    # day grid lines
    for d in range(1, 8):
        ax.axvline(d, color=GRID, linewidth=0.8, linestyle=":")
        ax.text(d + 0.03, 0.02, f"day {d}", color="#444", fontsize=7)

    ax.set_xlabel("Days from now")
    ax.set_ylabel("Normalised score")
    ax.set_title("Score forecast — top 5 apps over next 7 days (hourly, real events + real formula)")
    ax.legend(facecolor=DARK_BG, edgecolor=GRID, labelcolor=ACCENT, fontsize=9)
    ax.set_xlim(0, 7)
    ax.set_ylim(0)
    return fig_to_b64(fig)

# ---------------------------------------------------------------------------
# 5. HTML report
# ---------------------------------------------------------------------------

HTML_TEMPLATE = """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>AI Launcher — Usage Report</title>
<style>
  body {{ background:#111; color:#ddd; font-family:system-ui,sans-serif; margin:0; padding:24px; }}
  h1 {{ font-size:1.4rem; font-weight:500; margin-bottom:4px; }}
  .meta {{ color:#666; font-size:.85rem; margin-bottom:32px; }}
  .grid {{ display:grid; grid-template-columns:1fr 1fr; gap:24px; }}
  .card {{ background:#1a1a1a; border-radius:12px; padding:16px; }}
  .card img {{ width:100%; border-radius:6px; }}
  .caption {{ font-size:.78rem; color:#666; margin-top:8px; }}
  @media(max-width:900px) {{ .grid {{ grid-template-columns:1fr; }} }}
</style>
</head>
<body>
<h1>AI Launcher — Usage Report</h1>
<div class="meta">Generated {ts} · {n_events} events · {n_apps} apps · {span_days:.1f} day span</div>
<div class="grid">
  <div class="card">
    <img src="data:image/png;base64,{c1}">
    <div class="caption">Chart 1 — Score ranking: proves scoring produces meaningful differentiation between apps.</div>
  </div>
  <div class="card">
    <img src="data:image/png;base64,{c2}">
    <div class="caption">Chart 2 — Launches by hour: confirms hour field is captured correctly across the day.</div>
  </div>
  <div class="card" style="grid-column:1/-1">
    <img src="data:image/png;base64,{c3}">
    <div class="caption">Chart 3 — App × hour heatmap: each app has a distinct peak hour, giving the hour-match signal real value.</div>
  </div>
  <div class="card">
    <img src="data:image/png;base64,{c4}">
    <div class="caption">Chart 4 — Decay curve: shows the 7-day half-life used by ScoreEngine. Score at day 7 = 0.5, day 14 = 0.25.</div>
  </div>
  <div class="card">
    <img src="data:image/png;base64,{c5}">
    <div class="caption">Chart 5 — Day-of-week distribution: confirms dayOfWeek field is populated. Current day highlighted.</div>
  </div>
  <div class="card" style="grid-column:1/-1">
    <img src="data:image/png;base64,{c6}">
    <div class="caption">Chart 6 — Score vs recency: points should cluster near the decay reference curve, confirming decay drives ranking.</div>
  </div>
  <div class="card" style="grid-column:1/-1">
    <img src="data:image/png;base64,{c7}">
    <div class="caption">Chart 7 — Score forecast (next 7 days, hourly): oscillations from hour/day matching, envelope declining due to decay. Top app should stay above others at matching hours.</div>
  </div>
</div>
</body>
</html>
"""

def main():
    print("Pulling usage_log.json from device…")
    events = pull_events()
    print(f"  {len(events)} events loaded")

    now = datetime.datetime.now()
    now_ms  = int(time.time() * 1000)
    now_h   = now.hour
    now_dow = now.isoweekday()

    scores = compute_scores(events, now_h, now_dow, now_ms)

    pkgs = set(e["packageName"] for e in events)
    ts_vals = [e["timestampMillis"] for e in events]
    span_days = (max(ts_vals) - min(ts_vals)) / 86_400_000 if len(ts_vals) > 1 else 0

    print("Generating charts…")
    c1 = chart_score_ranking(scores)
    c2 = chart_launches_by_hour(events)
    c3 = chart_app_hour_heatmap(events)
    c4 = chart_decay_curve()
    c5 = chart_day_of_week(events)
    c6 = chart_score_vs_recency(scores, events)
    print("Generating 7-day forecast (168 time steps × top 5 apps)…")
    c7 = chart_score_forecast(events)

    html = HTML_TEMPLATE.format(
        ts=now.strftime("%Y-%m-%d %H:%M"),
        n_events=len(events),
        n_apps=len(pkgs),
        span_days=span_days,
        c1=c1, c2=c2, c3=c3, c4=c4, c5=c5, c6=c6, c7=c7,
    )

    out = "/tmp/ai_launcher_report.html"
    with open(out, "w") as f:
        f.write(html)
    print(f"Report saved → {out}")
    webbrowser.open(f"file://{out}")

if __name__ == "__main__":
    main()
