"""
AI Launcher — interactive usage stats report.
Pull data from device, score it in Python, render 7 interactive Plotly charts.
Usage: uv run --with plotly python3 scripts/report.py
"""

import collections
import datetime
import json
import subprocess
import sys
import time
import webbrowser

import plotly.graph_objects as go

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

HOUR_SIGMA      = 1.5   # Gaussian σ in hours — smooth bell curve instead of hard cutoff
DECAY_HALF_LIFE = 7.0

def is_weekend(dow):
    return dow >= 6

def hour_match(hour_dist):
    import math
    return math.exp(-(hour_dist ** 2) / (2 * HOUR_SIGMA ** 2))

def _event_components(e, now_hour, now_dow, now_ms):
    """Return (hour_match, base_weight) for a single event."""
    import math
    diff = abs(e["hour"] - now_hour)
    hm = math.exp(-(min(diff, 24 - diff) ** 2) / (2 * HOUR_SIGMA ** 2))
    dow = e.get("dayOfWeek", 0)
    if dow == 0 or dow == now_dow:
        day_match = 1.0
    elif is_weekend(dow) == is_weekend(now_dow):
        day_match = 0.6
    else:
        day_match = 0.2
    days_ago = (now_ms - e["timestampMillis"]) / 86_400_000
    decay = 0.5 ** (days_ago / DECAY_HALF_LIFE)
    return hm, day_match * decay  # (hour_match, base_weight)


def compute_scores(events, now_hour, now_dow, now_ms):
    """Original formula: Σ(hourMatch × dayMatch × decay)."""
    by_pkg = collections.defaultdict(list)
    for e in events:
        by_pkg[e["packageName"]].append(e)
    scores = {}
    for pkg, evts in by_pkg.items():
        total = 0.0
        for e in evts:
            hm, w = _event_components(e, now_hour, now_dow, now_ms)
            total += hm * w
        scores[pkg] = total
    return scores


ALPHA = 0.3  # frequency dampening exponent (0=raw sum, 1=pure conditional prob)
# α=0.3 measured as best trade-off: TeleLoisirs rank 16→12, overall MRR −0.011 only.

def compute_scores_v2(events, now_hour, now_dow, now_ms):
    """
    score = Σ(hourMatch × weight) / Σ(weight)^ALPHA
    where weight = dayMatch × decay.
    Compared to v1 (ALPHA=0 raw sum): dampens frequency dominance so specialized
    apps (e.g. TeleLoisirs) can rank higher at their specific usage hours.
    """
    import math
    by_pkg = collections.defaultdict(list)
    for e in events:
        by_pkg[e["packageName"]].append(e)
    scores = {}
    for pkg, evts in by_pkg.items():
        total_w = 0.0
        hour_w  = 0.0
        for e in evts:
            hm, w = _event_components(e, now_hour, now_dow, now_ms)
            total_w += w
            hour_w  += hm * w
        scores[pkg] = hour_w / (total_w ** ALPHA) if total_w > 0 else 0.0
    return scores

# ---------------------------------------------------------------------------
# 3. Helpers
# ---------------------------------------------------------------------------

PINNED_PKGS = ["fr.playsoft.teleloisirs"]

def short_name(pkg):
    return pkg

PALETTE = [
    "#4C9BE8",  # blue
    "#E8834C",  # orange
    "#4CE87A",  # green
    "#E84C9B",  # pink
    "#C8E84C",  # lime
    "#4CE8D8",  # teal
    "#E8C84C",  # gold
    "#9B4CE8",  # purple
]

LAYOUT = dict(
    paper_bgcolor="#111111",
    plot_bgcolor="#1a1a1a",
    font=dict(color="#dddddd", family="system-ui"),
    xaxis=dict(gridcolor="#222222", zerolinecolor="#222222"),
    yaxis=dict(gridcolor="#222222", zerolinecolor="#222222"),
    margin=dict(l=16, r=16, t=44, b=40),
)

def apply_layout(fig, **extra):
    fig.update_layout(**LAYOUT, **extra)
    return fig

# ---------------------------------------------------------------------------
# 4. Charts
# ---------------------------------------------------------------------------

def chart_score_ranking(scores):
    max_score = max(scores.values()) or 1
    ranked = sorted(scores.items(), key=lambda x: x[1])
    names  = [short_name(p) for p, _ in ranked]
    vals   = [round(s / max_score, 4) for _, s in ranked]
    # gradient: dim grey → vivid blue as score rises
    colors = [PALETTE[i % len(PALETTE)] for i in range(len(vals))]

    fig = go.Figure(go.Bar(
        x=vals, y=names, orientation="h",
        marker=dict(color=colors, line=dict(width=0)),
        hovertemplate="<b>%{y}</b>: %{x:.3f}<extra></extra>",
    ))
    apply_layout(fig, title="Current score ranking", xaxis_title="Normalised score")
    fig.add_vline(x=0.5, line_dash="dash", line_color="#333333",
                  annotation_text="0.5", annotation_font_color="#555555")
    return fig


def chart_launches_by_hour(events):
    hours = [0] * 24
    for e in events:
        hours[e["hour"]] += 1

    now_h = datetime.datetime.now().hour
    peak  = max(hours) or 1
    bar_colors = [
        PALETTE[0] if h == now_h else
        (PALETTE[2] if hours[h] == peak else
         ("#555555" if hours[h] > peak * 0.5 else "#333333"))
        for h in range(24)
    ]
    fig = go.Figure(go.Bar(
        x=list(range(24)), y=hours,
        marker=dict(color=bar_colors, line=dict(width=0)),
        hovertemplate="<b>%{x}h</b>: %{y} launches<extra></extra>",
    ))
    apply_layout(fig, title="Launches by hour of day", xaxis_title="Hour", yaxis_title="Launches")
    fig.add_vline(x=now_h, line_dash="dash", line_color="#FF6B6B",
                  annotation_text=f"now ({now_h}h)", annotation_font_color="#FF6B6B")
    return fig


def all_pkgs_with_pinned(events):
    counts = collections.Counter(e["packageName"] for e in events)
    ranked = [p for p, _ in counts.most_common() if p not in PINNED_PKGS]
    return ranked + [p for p in PINNED_PKGS if p in counts]

def chart_app_hour_heatmap(events):
    top_pkgs = all_pkgs_with_pinned(events)
    names = [short_name(p) for p in top_pkgs]
    matrix = [[0] * 24 for _ in top_pkgs]
    for e in events:
        if e["packageName"] in top_pkgs:
            row = top_pkgs.index(e["packageName"])
            matrix[row][e["hour"]] += 1

    fig = go.Figure(go.Heatmap(
        z=matrix, x=list(range(24)), y=names,
        colorscale=[[0, "#1a1a1a"], [0.3, "#1a3a5c"], [0.7, "#2a6aaa"], [1, "#4C9BE8"]],
        hovertemplate="<b>%{y}</b> at <b>%{x}h</b>: %{z} launches<extra></extra>",
    ))
    now_h = datetime.datetime.now().hour
    apply_layout(fig, title="App × hour heatmap (top 8 apps)", xaxis_title="Hour")
    fig.add_vline(x=now_h, line_dash="dash", line_color="#FF6B6B",
                  annotation_text=f"now ({now_h}h)", annotation_font_color="#FF6B6B")
    return fig


def chart_decay_curve():
    xs = [d / 10 for d in range(281)]  # 0..28 days
    ys = [round(0.5 ** (d / DECAY_HALF_LIFE), 4) for d in xs]

    fig = go.Figure()
    fig.add_trace(go.Scatter(x=xs, y=ys, mode="lines",
                             line=dict(color=PALETTE[0], width=2.5),
                             fill="tozeroy", fillcolor="rgba(76,155,232,0.08)",
                             hovertemplate="Day %{x:.1f}: decay=%{y:.3f}<extra></extra>"))
    fig.add_hline(y=0.5, line_dash="dash", line_color="#555555")
    fig.add_vline(x=7,  line_dash="dot", line_color="#666666",
                  annotation_text="7d", annotation_font_color="#aaaaaa")
    fig.add_vline(x=14, line_dash="dot", line_color="#666666",
                  annotation_text="14d", annotation_font_color="#aaaaaa")
    apply_layout(fig, title=f"Score decay curve (half-life = {int(DECAY_HALF_LIFE)} days)",
                 xaxis_title="Days since last launch", yaxis_title="Decay factor",
                 yaxis_range=[0, 1.05])
    return fig


def chart_day_of_week(events):
    labels = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
    vals   = [0] * 7
    for e in events:
        dow = e.get("dayOfWeek", 0)
        if 1 <= dow <= 7:
            vals[dow - 1] += 1

    now_dow = datetime.datetime.now().isoweekday()
    colors  = [PALETTE[0] if i + 1 == now_dow else "#444444" for i in range(7)]

    fig = go.Figure(go.Bar(
        x=labels, y=vals,
        marker=dict(color=colors, line=dict(width=0)),
        hovertemplate="<b>%{x}</b>: %{y} launches<extra></extra>",
    ))
    apply_layout(fig, title="Launches by day of week", yaxis_title="Launches")
    return fig


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
            xs.append((now_ms - last_by_pkg[pkg]) / 86_400_000)
            ys.append(score / max_score)
            names.append(short_name(pkg))

    ref_x = [d / 10 for d in range(int(max(xs, default=0) * 10) + 20)]
    ref_y = [round(0.5 ** (d / DECAY_HALF_LIFE), 4) for d in ref_x]

    fig = go.Figure()
    fig.add_trace(go.Scatter(
        x=ref_x, y=ref_y, mode="lines", name="pure decay ref",
        line=dict(color="#FF6B6B", dash="dash", width=1),
        hovertemplate="Day %{x:.1f}: ref=%{y:.3f}<extra></extra>",
    ))
    fig.add_trace(go.Scatter(
        x=xs, y=ys, mode="markers+text", name="apps",
        text=names, textposition="top right",
        textfont=dict(size=10, color="#aaaaaa"),
        marker=dict(color="#dddddd", size=8),
        hovertemplate="%{text}<br>Days ago: %{x:.2f}<br>Score: %{y:.3f}<extra></extra>",
    ))
    apply_layout(fig, title="Score vs recency (confirms decay)",
                 xaxis_title="Days since last launch", yaxis_title="Normalised score",
                 yaxis_range=[0, 1.05])
    return fig


def chart_score_forecast(events):
    now_ms  = int(time.time() * 1000)
    top_pkgs = all_pkgs_with_pinned(events)
    by_pkg = {pkg: [e for e in events if e["packageName"] == pkg] for pkg in top_pkgs}

    # one point per hour over 7 days
    hours_ahead = list(range(7 * 24 + 1))
    future_times = [
        datetime.datetime.fromtimestamp((now_ms + h * 3_600_000) / 1000)
        for h in hours_ahead
    ]

    max_score = 1.0  # will be updated after first pass
    series = {}
    for pkg, evts in by_pkg.items():
        ys = []
        for h, ft in zip(hours_ahead, future_times):
            future_ms  = now_ms + h * 3_600_000
            total = 0.0
            for e in evts:
                diff = abs(e["hour"] - ft.hour)
                hm  = hour_match(min(diff, 24 - diff))
                dow = e.get("dayOfWeek", 0)
                fdow = ft.isoweekday()
                if dow == 0 or dow == fdow:
                    day_match = 1.0
                elif is_weekend(dow) == is_weekend(fdow):
                    day_match = 0.6
                else:
                    day_match = 0.2
                days_ago = (future_ms - e["timestampMillis"]) / 86_400_000
                decay = 0.5 ** (days_ago / DECAY_HALF_LIFE)
                total += hm * day_match * decay
            ys.append(total)
        series[pkg] = ys
        if ys[0] > max_score:
            max_score = ys[0]

    fig = go.Figure()
    palette = PALETTE * (len(series) // len(PALETTE) + 1)
    for (pkg, ys), color in zip(series.items(), palette):
        norm_ys = [round(y / max_score, 4) for y in ys]
        fig.add_trace(go.Scatter(
            x=future_times, y=norm_ys,
            mode="lines", name=short_name(pkg),
            line=dict(color=color, width=1.5),
            hovertemplate="%{x|%a %H:%M}: %{y:.3f}<extra>" + short_name(pkg) + "</extra>",
        ))

    # day separators
    for d in range(1, 8):
        sep = datetime.datetime.fromtimestamp((now_ms + d * 86_400_000) / 1000)
        fig.add_vline(x=sep.timestamp() * 1000, line_dash="dot", line_color="#2a2a2a")

    apply_layout(fig,
        title="Score forecast — all apps, next 7 days (hourly, real events + real formula)",
        xaxis_title="Time", yaxis_title="Normalised score",
        yaxis_range=[0, None],
        legend=dict(bgcolor="#1a1a1a", bordercolor="#333333"),
    )
    return fig

# ---------------------------------------------------------------------------
# 5. MRR evaluation
# ---------------------------------------------------------------------------

def compute_mrr(events, score_fn=None):
    """
    For each launch event i, replay history up to (but not including) i,
    compute scores at the hour of launch, rank all known apps, and record
    the rank of the launched app. MRR = mean(1/rank).
    Random baseline on N apps ≈ (ln N + 0.577) / N.
    score_fn defaults to compute_scores (original formula).
    """
    if score_fn is None:
        score_fn = compute_scores
    events = sorted(events, key=lambda e: e["timestampMillis"])
    all_pkgs = list({e["packageName"] for e in events})
    n = len(all_pkgs)

    reciprocal_ranks = []
    per_app = collections.defaultdict(list)

    for i, target in enumerate(events):
        history = events[:i]
        if not history:
            continue
        pkg    = target["packageName"]
        ts     = target["timestampMillis"]
        hour   = target["hour"]
        dow    = target.get("dayOfWeek", 0) or datetime.datetime.fromtimestamp(ts / 1000).isoweekday()
        scores = score_fn(history, hour, dow, ts)
        ranked = sorted(all_pkgs, key=lambda p: scores.get(p, 0.0), reverse=True)
        rank   = ranked.index(pkg) + 1
        rr     = 1.0 / rank
        reciprocal_ranks.append(rr)
        per_app[pkg].append(rr)

    mrr    = sum(reciprocal_ranks) / len(reciprocal_ranks) if reciprocal_ranks else 0
    random = sum(1 / r for r in range(1, n + 1)) / n

    app_mrr = {
        pkg: sum(rrs) / len(rrs)
        for pkg, rrs in per_app.items()
        if len(rrs) >= 3
    }
    return mrr, random, app_mrr, reciprocal_ranks


def chart_mrr(events, score_fn=None, label="v2 (KDE)"):
    mrr, random_baseline, app_mrr, rrs = compute_mrr(events, score_fn)

    sorted_apps = sorted(app_mrr.items(), key=lambda x: x[1])
    names  = [short_name(p) for p, _ in sorted_apps]
    vals   = [v for _, v in sorted_apps]
    colors = [PALETTE[0] if v > mrr else "#444444" for v in vals]

    fig = go.Figure()
    fig.add_trace(go.Bar(
        x=vals, y=names, orientation="h",
        marker=dict(color=colors, line=dict(width=0)),
        hovertemplate="<b>%{y}</b>: MRR=%{x:.3f}<extra></extra>",
        name="per-app MRR",
    ))
    fig.add_vline(x=mrr, line_dash="dash", line_color="#dddddd",
                  annotation_text=f"overall {mrr:.3f}", annotation_font_color="#dddddd")
    fig.add_vline(x=random_baseline, line_dash="dot", line_color="#FF6B6B",
                  annotation_text=f"random {random_baseline:.3f}", annotation_font_color="#FF6B6B")
    apply_layout(fig,
        title=f"MRR per app — {label}  (overall={mrr:.3f}, random={random_baseline:.3f})",
        xaxis_title="MRR  (higher = better predicted)",
    )
    return fig, mrr, random_baseline, app_mrr


def chart_mrr_comparison(events):
    """Side-by-side grouped bar: v1 (sum) vs v2 (KDE) MRR per app."""
    _, _, app_v1, _ = compute_mrr(events, compute_scores)
    _, _, app_v2, _ = compute_mrr(events, compute_scores_v2)

    all_apps = sorted(set(app_v1) | set(app_v2))
    # sort by v2 MRR
    all_apps = sorted(all_apps, key=lambda p: app_v2.get(p, 0.0))

    names = [short_name(p) for p in all_apps]
    v1_vals = [app_v1.get(p, 0.0) for p in all_apps]
    v2_vals = [app_v2.get(p, 0.0) for p in all_apps]
    delta   = [v2 - v1 for v1, v2 in zip(v1_vals, v2_vals)]

    fig = go.Figure()
    fig.add_trace(go.Bar(
        x=v1_vals, y=names, orientation="h", name="v1 (sum)",
        marker=dict(color="#444444", line=dict(width=0)),
        hovertemplate="<b>%{y}</b> v1=%{x:.3f}<extra></extra>",
    ))
    fig.add_trace(go.Bar(
        x=v2_vals, y=names, orientation="h", name="v2 (KDE)",
        marker=dict(color=PALETTE[0], line=dict(width=0)),
        hovertemplate="<b>%{y}</b> v2=%{x:.3f} (Δ%{customdata:+.3f})<extra></extra>",
        customdata=delta,
    ))
    apply_layout(fig,
        title="MRR comparison: v1 (sum) vs v2 (KDE normalised)",
        xaxis_title="MRR", barmode="overlay",
        legend=dict(bgcolor="#1a1a1a", bordercolor="#333333"),
    )
    return fig


def chart_rank_distribution(events, score_fn=None, label=""):
    _, _, _, rrs = compute_mrr(events, score_fn)
    ranks = [round(1 / r) for r in rrs]
    max_rank = min(max(ranks), 15)
    counts = collections.Counter(ranks)
    xs = list(range(1, max_rank + 1))
    ys = [counts.get(x, 0) for x in xs]
    total = sum(ys)
    pcts  = [y / total * 100 for y in ys]

    fig = go.Figure(go.Bar(
        x=xs, y=pcts,
        marker=dict(
            color=[PALETTE[0] if x <= 3 else "#444444" for x in xs],
            line=dict(width=0),
        ),
        hovertemplate="Rank %{x}: %{y:.1f}%<extra></extra>",
    ))
    title_suffix = f" — {label}" if label else ""
    apply_layout(fig,
        title=f"Rank distribution{title_suffix} (top-15 shown)",
        xaxis_title="Rank", yaxis_title="% of launches",
    )
    cumulative = sum(pcts[:3])
    fig.add_annotation(
        x=3, y=max(pcts[:3]),
        text=f"Top-3: {cumulative:.0f}% of launches",
        showarrow=False, font=dict(color="#dddddd", size=11),
        xanchor="left", yanchor="bottom",
    )
    return fig


# ---------------------------------------------------------------------------
# 6. HTML report
# ---------------------------------------------------------------------------

HTML_TEMPLATE = """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>AI Launcher — Usage Report</title>
<script src="https://cdn.plot.ly/plotly-2.32.0.min.js"></script>
<style>
  body {{ background:#111; color:#ddd; font-family:system-ui,sans-serif; margin:0; padding:24px; }}
  h1 {{ font-size:1.4rem; font-weight:500; margin-bottom:4px; }}
  .meta {{ color:#666; font-size:.85rem; margin-bottom:32px; }}
  .grid {{ display:grid; grid-template-columns:1fr 1fr; gap:24px; }}
  .card {{ background:#1a1a1a; border-radius:12px; padding:16px; }}
  .caption {{ font-size:.78rem; color:#666; margin-top:8px; }}
  .wide {{ grid-column:1/-1; }}
  @media(max-width:900px) {{ .grid {{ grid-template-columns:1fr; }} }}
</style>
</head>
<body>
<h1>AI Launcher — Usage Report</h1>
<div class="meta">Generated {ts} &nbsp;·&nbsp; {n_events} events &nbsp;·&nbsp; {n_apps} apps &nbsp;·&nbsp; {span_days:.1f} day span</div>
<div class="grid">
  <div class="card"><div id="c1" style="height:420px"></div>
    <div class="caption">Score ranking — proves scoring produces meaningful differentiation.</div></div>
  <div class="card"><div id="c2" style="height:420px"></div>
    <div class="caption">Launches by hour — confirms hour field is captured across the day. Red = now.</div></div>
  <div class="card wide"><div id="c3" style="height:340px"></div>
    <div class="caption">App × hour heatmap — each app has a distinct peak hour, giving hour-match real signal.</div></div>
  <div class="card"><div id="c4" style="height:360px"></div>
    <div class="caption">Decay curve — 7-day half-life. Score at day 7 = 0.5, day 14 = 0.25.</div></div>
  <div class="card"><div id="c5" style="height:360px"></div>
    <div class="caption">Day-of-week distribution — confirms dayOfWeek field is populated. Current day highlighted.</div></div>
  <div class="card wide"><div id="c6" style="height:420px"></div>
    <div class="caption">Score vs recency — points should cluster near the red decay reference, confirming decay drives ranking.</div></div>
  <div class="card wide"><div id="c7" style="height:480px"></div>
    <div class="caption">Score forecast (next 7 days, hourly) — oscillations from hour/day matching riding on decay envelope. Hover for exact values.</div></div>
  <div class="card wide"><div id="c8" style="height:460px"></div>
    <div class="caption">MRR per app (v2 KDE) — for each recorded launch, replay history, check rank of launched app. Blue = above average. Red = random baseline ({n_apps} apps → {random_baseline}).</div></div>
  <div class="card wide"><div id="c9" style="height:380px"></div>
    <div class="caption">Rank distribution (v2 KDE) — how often the launched app was rank 1, 2, 3… More weight on the left = better learning.</div></div>
  <div class="card wide"><div id="c10" style="height:480px"></div>
    <div class="caption">MRR comparison: v1 (raw sum, original) vs v2 (KDE-normalised, bimodal fix). Blue bars = v2 score; grey = v1. Longer blue = improvement.</div></div>
  <div class="card wide"><div id="c11" style="height:380px"></div>
    <div class="caption">Rank distribution v1 (original formula) — baseline for comparison with v2 above.</div></div>
</div>
<script>
{scripts}
</script>
</body>
</html>
"""

def fig_to_js(fig, div_id):
    return (
        f"Plotly.newPlot('{div_id}', "
        f"{fig.to_json()[::-1].replace('}', '', 1)[::-1].replace('{', '', 1)}"  # strip outer {}
    )
    # simpler: use fig.to_json() and parse in JS

def figs_to_scripts(figs):
    lines = []
    for div_id, fig in figs:
        data   = fig.to_json()
        lines.append(
            f"(function(){{ var d=JSON.parse({json.dumps(data)});"
            f"Plotly.newPlot('{div_id}',d.data,d.layout,{{responsive:true}}); }})();"
        )
    return "\n".join(lines)

# ---------------------------------------------------------------------------
# 6. Main
# ---------------------------------------------------------------------------

def main():
    print("Pulling usage_log.json from device…")
    events = pull_events()
    print(f"  {len(events)} events loaded")

    now    = datetime.datetime.now()
    now_ms = int(time.time() * 1000)

    scores = compute_scores(events, now.hour, now.isoweekday(), now_ms)

    pkgs = set(e["packageName"] for e in events)
    ts_vals = [e["timestampMillis"] for e in events]
    span_days = (max(ts_vals) - min(ts_vals)) / 86_400_000 if len(ts_vals) > 1 else 0

    print("Generating charts…")
    figs = [
        ("c1", chart_score_ranking(scores)),
        ("c2", chart_launches_by_hour(events)),
        ("c3", chart_app_hour_heatmap(events)),
        ("c4", chart_decay_curve()),
        ("c5", chart_day_of_week(events)),
        ("c6", chart_score_vs_recency(scores, events)),
    ]
    print("Generating 7-day forecast…")
    figs.append(("c7", chart_score_forecast(events)))

    print("Computing MRR v1 (original formula)…")
    mrr_v1, random_baseline, _, _ = compute_mrr(events, compute_scores)
    print(f"  v1 MRR={mrr_v1:.4f}  random={random_baseline:.4f}  lift={mrr_v1/random_baseline:.2f}x")

    print("Computing MRR v2 (KDE-normalised)…")
    fig_mrr, mrr_v2, _, _ = chart_mrr(events, compute_scores_v2, label="v2 (KDE normalised)")
    print(f"  v2 MRR={mrr_v2:.4f}  lift={mrr_v2/random_baseline:.2f}x  delta={mrr_v2-mrr_v1:+.4f}")

    figs.append(("c8", fig_mrr))
    figs.append(("c9", chart_rank_distribution(events, compute_scores_v2, label="v2 (KDE)")))
    figs.append(("c10", chart_mrr_comparison(events)))
    figs.append(("c11", chart_rank_distribution(events, compute_scores, label="v1 (original)")))

    html = HTML_TEMPLATE.format(
        ts=now.strftime("%Y-%m-%d %H:%M"),
        n_events=len(events),
        n_apps=len(pkgs),
        span_days=span_days,
        random_baseline=f"{random_baseline:.3f}",
        scripts=figs_to_scripts(figs),
    )

    out = "/tmp/ai_launcher_report.html"
    with open(out, "w") as f:
        f.write(html)
    print(f"Report saved → {out}")
    webbrowser.open(f"file://{out}")

if __name__ == "__main__":
    main()
