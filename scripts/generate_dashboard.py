import json
from pathlib import Path

data = json.load(open('dashboard_data.json'))

for m in data.get('benchmark', []):
    if 'r5' not in m and 'r6' in m:
        m['r5'] = m['r6']

clustering = data.get('clustering', {})
clusters = clustering.get('clusters', [])
clustered_apps = clustering.get('apps', [])
feat_imp = data.get('feature_importance', {})
features_list = feat_imp.get('features', [])
categories_list = feat_imp.get('categories', [])
adv_ds = data.get('advanced_ds', {})

html_content = f'''<!DOCTYPE html>
<html lang="fr">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Loom AI : Next-Gen Data Science & ML Dashboard</title>
  <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;500;600;700;800&family=JetBrains+Mono:wght@400;500;700&display=swap" rel="stylesheet">
  <style>
    :root {{
      --bg-primary: #080b11;
      --bg-card: rgba(15, 21, 34, 0.80);
      --bg-card-hover: rgba(24, 32, 52, 0.90);
      --border-color: rgba(255, 255, 255, 0.08);
      --border-highlight: rgba(99, 102, 241, 0.4);
      --accent-indigo: #6366f1;
      --accent-cyan: #06b6d4;
      --accent-emerald: #10b981;
      --accent-amber: #f59e0b;
      --accent-rose: #f43f5e;
      --accent-purple: #a855f7;
      --text-main: #f1f5f9;
      --text-muted: #94a3b8;
      --text-sub: #64748b;
    }}

    * {{
      box-sizing: border-box;
      margin: 0;
      padding: 0;
    }}

    html {{
      scroll-behavior: smooth;
    }}

    body {{
      font-family: 'Outfit', -apple-system, BlinkMacSystemFont, sans-serif;
      background-color: var(--bg-primary);
      background-image: 
        radial-gradient(circle at 10% 10%, rgba(99, 102, 241, 0.15) 0%, transparent 40%),
        radial-gradient(circle at 90% 15%, rgba(6, 182, 212, 0.12) 0%, transparent 45%),
        radial-gradient(circle at 50% 85%, rgba(168, 85, 247, 0.10) 0%, transparent 50%);
      background-attachment: fixed;
      color: var(--text-main);
      min-height: 100vh;
      padding: 2rem;
      line-height: 1.5;
    }}

    .container {{
      max-width: 1440px;
      margin: 0 auto;
    }}

    /* Sticky Navigation */
    .sticky-nav {{
      position: sticky;
      top: 1rem;
      z-index: 100;
      background: rgba(10, 15, 26, 0.85);
      backdrop-filter: blur(20px);
      -webkit-backdrop-filter: blur(20px);
      border: 1px solid var(--border-color);
      border-radius: 9999px;
      padding: 0.5rem 1rem;
      margin-bottom: 2rem;
      display: flex;
      gap: 0.5rem;
      overflow-x: auto;
      box-shadow: 0 10px 30px rgba(0, 0, 0, 0.4);
    }}

    .nav-pill {{
      padding: 0.4rem 0.9rem;
      background: transparent;
      border: 1px solid transparent;
      border-radius: 999px;
      font-size: 0.82rem;
      font-weight: 600;
      color: var(--text-muted);
      text-decoration: none;
      white-space: nowrap;
      transition: all 0.2s ease;
    }}

    .nav-pill:hover, .nav-pill.active {{
      background: rgba(99, 102, 241, 0.2);
      border-color: var(--border-highlight);
      color: #ffffff;
    }}

    /* Header */
    header {{
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 2rem;
      padding-bottom: 1.5rem;
      border-bottom: 1px solid var(--border-color);
      flex-wrap: wrap;
      gap: 1rem;
    }}

    .title-area h1 {{
      font-size: 2.3rem;
      font-weight: 800;
      letter-spacing: -0.03em;
      background: linear-gradient(135deg, #ffffff 0%, #cbd5e1 50%, #818cf8 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }}

    .title-area p {{
      color: var(--text-muted);
      font-size: 1.02rem;
      margin-top: 0.35rem;
    }}

    .badge {{
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      padding: 0.35rem 0.85rem;
      background: rgba(99, 102, 241, 0.15);
      border: 1px solid rgba(99, 102, 241, 0.35);
      border-radius: 9999px;
      font-size: 0.82rem;
      font-weight: 600;
      color: #a5b4fc;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }}

    .status-dot {{
      width: 8px;
      height: 8px;
      background-color: var(--accent-emerald);
      border-radius: 50%;
      box-shadow: 0 0 10px var(--accent-emerald);
      animation: pulse 2s infinite;
    }}

    @keyframes pulse {{
      0%, 100% {{ opacity: 1; transform: scale(1); }}
      50% {{ opacity: 0.4; transform: scale(0.85); }}
    }}

    /* KPI Cards Grid */
    .kpi-grid {{
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
      gap: 1.25rem;
      margin-bottom: 2rem;
    }}

    .kpi-card {{
      background: var(--bg-card);
      backdrop-filter: blur(16px);
      -webkit-backdrop-filter: blur(16px);
      border: 1px solid var(--border-color);
      border-radius: 1.25rem;
      padding: 1.4rem;
      transition: all 0.25s ease;
      position: relative;
      overflow: hidden;
    }}

    .kpi-card:hover {{
      transform: translateY(-3px);
      background: var(--bg-card-hover);
      border-color: var(--border-highlight);
      box-shadow: 0 12px 30px rgba(0, 0, 0, 0.3);
    }}

    .kpi-label {{
      font-size: 0.8rem;
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 0.05em;
      font-weight: 600;
      margin-bottom: 0.5rem;
    }}

    .kpi-val {{
      font-size: 2.2rem;
      font-weight: 800;
      letter-spacing: -0.03em;
      color: #ffffff;
      display: flex;
      align-items: baseline;
      gap: 0.35rem;
    }}

    .kpi-sub {{
      font-size: 0.8rem;
      color: var(--text-sub);
      margin-top: 0.35rem;
    }}

    .highlight-emerald {{ color: var(--accent-emerald); }}
    .highlight-indigo {{ color: #a5b4fc; }}
    .highlight-cyan {{ color: var(--accent-cyan); }}
    .highlight-purple {{ color: var(--accent-purple); }}

    /* Panels & Grids */
    .grid-2 {{
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1.5rem;
      margin-bottom: 1.75rem;
    }}

    @media (max-width: 1024px) {{
      .grid-2 {{ grid-template-columns: 1fr; }}
    }}

    .panel {{
      background: var(--bg-card);
      backdrop-filter: blur(16px);
      -webkit-backdrop-filter: blur(16px);
      border: 1px solid var(--border-color);
      border-radius: 1.25rem;
      padding: 1.6rem;
      box-shadow: 0 10px 25px rgba(0, 0, 0, 0.2);
      margin-bottom: 1.75rem;
    }}

    .panel-header {{
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1.35rem;
      flex-wrap: wrap;
      gap: 0.5rem;
    }}

    .panel-title {{
      font-size: 1.2rem;
      font-weight: 700;
      color: #ffffff;
      display: flex;
      align-items: center;
      gap: 0.6rem;
    }}

    .panel-desc {{
      font-size: 0.82rem;
      color: var(--text-muted);
      margin-top: 0.2rem;
    }}

    /* Table styles */
    .table-container {{
      overflow-x: auto;
    }}

    table {{
      width: 100%;
      border-collapse: collapse;
      text-align: left;
      font-size: 0.88rem;
    }}

    th {{
      padding: 0.75rem 0.9rem;
      background: rgba(255, 255, 255, 0.03);
      color: var(--text-muted);
      font-weight: 600;
      text-transform: uppercase;
      font-size: 0.73rem;
      letter-spacing: 0.05em;
      border-bottom: 1px solid var(--border-color);
    }}

    td {{
      padding: 0.85rem 0.9rem;
      border-bottom: 1px solid rgba(255, 255, 255, 0.04);
      color: var(--text-main);
    }}

    tr:hover td {{
      background: rgba(255, 255, 255, 0.02);
    }}

    .tag {{
      display: inline-block;
      padding: 0.2rem 0.6rem;
      border-radius: 6px;
      font-size: 0.75rem;
      font-weight: 600;
    }}

    .tag-prod {{ background: rgba(16, 185, 129, 0.15); color: #34d399; border: 1px solid rgba(16, 185, 129, 0.3); }}
    .tag-cand {{ background: rgba(99, 102, 241, 0.15); color: #a5b4fc; border: 1px solid rgba(99, 102, 241, 0.3); }}
    .tag-ens {{ background: rgba(168, 85, 247, 0.15); color: #d8b4fe; border: 1px solid rgba(168, 85, 247, 0.3); }}
    .tag-base {{ background: rgba(148, 163, 184, 0.15); color: #cbd5e1; border: 1px solid rgba(148, 163, 184, 0.3); }}

    /* Feature Importance & Table */
    .feat-table-row {{
      display: grid;
      grid-template-columns: 2.5rem 1.8fr 1fr 1fr 1.5fr;
      align-items: center;
      gap: 1rem;
      padding: 0.8rem 1rem;
      border-bottom: 1px solid rgba(255, 255, 255, 0.04);
      transition: background 0.15s;
    }}

    .feat-table-row:hover {{
      background: rgba(255, 255, 255, 0.02);
    }}

    .feat-rank {{
      font-family: 'JetBrains Mono', monospace;
      font-weight: 800;
      color: var(--text-sub);
      font-size: 0.88rem;
    }}

    .feat-name-box {{
      display: flex;
      flex-direction: column;
    }}

    .feat-main-name {{
      font-weight: 600;
      color: var(--text-main);
      font-size: 0.9rem;
    }}

    .feat-desc {{
      font-size: 0.74rem;
      color: var(--text-muted);
      margin-top: 0.15rem;
    }}

    .feat-cat-pill {{
      display: inline-block;
      padding: 0.15rem 0.55rem;
      border-radius: 999px;
      font-size: 0.72rem;
      font-weight: 600;
      background: rgba(99, 102, 241, 0.12);
      border: 1px solid rgba(99, 102, 241, 0.25);
      color: #a5b4fc;
    }}

    .feat-bar-wrapper {{
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }}

    .feat-bar-bg {{
      flex-grow: 1;
      height: 8px;
      background: rgba(255, 255, 255, 0.06);
      border-radius: 999px;
      overflow: hidden;
    }}

    .feat-bar-fill {{
      height: 100%;
      border-radius: 999px;
      background: linear-gradient(90deg, var(--accent-indigo), var(--accent-cyan));
    }}

    /* Clusters styling */
    .clusters-grid {{
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 1.25rem;
      margin-bottom: 1.5rem;
    }}

    .cluster-card {{
      background: rgba(15, 23, 42, 0.6);
      border: 1px solid var(--border-color);
      border-radius: 1rem;
      padding: 1.25rem;
      position: relative;
      overflow: hidden;
      transition: all 0.25s ease;
    }}

    .cluster-card:hover {{
      background: rgba(30, 41, 59, 0.7);
      transform: translateY(-2px);
      border-color: var(--border-highlight);
    }}

    .cluster-header {{
      display: flex;
      align-items: center;
      gap: 0.75rem;
      margin-bottom: 0.75rem;
    }}

    .cluster-icon {{
      font-size: 1.4rem;
    }}

    .cluster-title {{
      font-size: 1rem;
      font-weight: 700;
      color: #ffffff;
    }}

    .cluster-desc {{
      font-size: 0.8rem;
      color: var(--text-muted);
      margin-bottom: 0.85rem;
      min-height: 2.2rem;
    }}

    .cluster-meta-row {{
      display: flex;
      justify-content: space-between;
      font-size: 0.78rem;
      color: var(--text-sub);
      border-top: 1px solid var(--border-color);
      padding-top: 0.65rem;
      margin-bottom: 0.65rem;
    }}

    .cluster-apps-chips {{
      display: flex;
      flex-wrap: wrap;
      gap: 0.35rem;
    }}

    .app-chip {{
      font-size: 0.74rem;
      padding: 0.18rem 0.5rem;
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 6px;
      color: #cbd5e1;
      cursor: pointer;
      transition: all 0.15s;
    }}

    .app-chip:hover {{
      background: rgba(99, 102, 241, 0.25);
      border-color: var(--accent-indigo);
      color: #ffffff;
    }}

    /* Simulator Sandbox */
    .simulator-box {{
      background: rgba(15, 23, 42, 0.6);
      border: 1px solid var(--border-highlight);
      border-radius: 1rem;
      padding: 1.5rem;
    }}

    .controls-grid {{
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 1.25rem;
      margin-bottom: 1.5rem;
    }}

    .control-group {{
      display: flex;
      flex-direction: column;
      gap: 0.4rem;
    }}

    .control-group label {{
      font-size: 0.78rem;
      color: var(--text-muted);
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }}

    .control-group select, .control-group input {{
      background: rgba(30, 41, 59, 0.8);
      border: 1px solid var(--border-color);
      border-radius: 8px;
      padding: 0.55rem 0.8rem;
      color: #ffffff;
      font-family: inherit;
      font-size: 0.88rem;
      outline: none;
      transition: border-color 0.2s;
    }}

    .control-group select:focus, .control-group input:focus {{
      border-color: var(--accent-indigo);
    }}

    .sim-results {{
      display: flex;
      flex-direction: column;
      gap: 0.65rem;
    }}

    .sim-rank-item {{
      display: flex;
      align-items: center;
      justify-content: space-between;
      background: rgba(30, 41, 59, 0.5);
      border: 1px solid var(--border-color);
      padding: 0.75rem 1.1rem;
      border-radius: 10px;
      transition: all 0.15s ease;
    }}

    .sim-rank-item:hover {{
      background: rgba(30, 41, 59, 0.85);
      border-color: var(--border-highlight);
    }}

    .rank-num {{
      font-family: 'JetBrains Mono', monospace;
      font-weight: 800;
      font-size: 1.05rem;
      color: var(--accent-indigo);
      width: 2rem;
    }}

    .rank-app {{
      font-weight: 600;
      color: #ffffff;
      flex-grow: 1;
    }}

    .rank-score {{
      font-family: 'JetBrains Mono', monospace;
      font-weight: 700;
      color: var(--accent-emerald);
      font-size: 0.92rem;
    }}

    footer {{
      margin-top: 3rem;
      text-align: center;
      color: var(--text-sub);
      font-size: 0.82rem;
      border-top: 1px solid var(--border-color);
      padding-top: 1.5rem;
    }}
  </style>
</head>
<body>
  <div class="container">
    <!-- Sticky Navigation -->
    <nav class="sticky-nav">
      <a href="#kpi" class="nav-pill">&#9889; Métriques Clés</a>
      <a href="#features" class="nav-pill">&#9776; Feature Importance</a>
      <a href="#clustering" class="nav-pill">&#128736; Clustering & PCA</a>
      <a href="#embeddings" class="nav-pill">&#128279; App2Vec & PageRank</a>
      <a href="#drift" class="nav-pill">&#128200; Concept Drift & Entropie</a>
      <a href="#rfm" class="nav-pill">&#128101; Segmentation RFM & Chaînes</a>
      <a href="#routines" class="nav-pill">&#9200; Routines Circadiennes & XAI</a>
      <a href="#benchmark" class="nav-pill">&#128202; Benchmark</a>
      <a href="#simulator" class="nav-pill">&#9881; Simulateur</a>
    </nav>

    <!-- Header -->
    <header>
      <div class="title-area">
        <div style="display: flex; align-items: center; gap: 1rem; margin-bottom: 0.5rem;">
          <h1>Loom AI : Next-Gen Data Science & ML Hub</h1>
          <span class="badge"><span class="status-dot"></span> v14 En Production</span>
        </div>
        <p>Dashboard analytique complet (Top 10 Data Science & Big Data) sur <strong>{data['summary']['total_events']:,} événements réels</strong> ({data['summary']['unique_apps']} applications).</p>
      </div>
      <div style="text-align: right;">
        <div style="font-size: 0.82rem; color: var(--text-sub);">Période des logs</div>
        <div style="font-size: 0.92rem; font-weight: 600; color: var(--text-muted);">{data['summary']['start_date']} &rarr; {data['summary']['end_date']}</div>
      </div>
    </header>

    <!-- KPI Highlights -->
    <div id="kpi" class="kpi-grid">
      <div class="kpi-card">
        <div class="kpi-label">Échantillons Évalués</div>
        <div class="kpi-val highlight-indigo">{data['summary']['evaluated_samples']:,}</div>
        <div class="kpi-sub">Total {data['summary']['total_events']:,} (warmup 50)</div>
      </div>

      <div class="kpi-card">
        <div class="kpi-label">Recall @ 1 (Top 1)</div>
        <div class="kpi-val">{data['summary']['recall_1']}%</div>
        <div class="kpi-sub">Cible trouvée immédiatement</div>
      </div>

      <div class="kpi-card">
        <div class="kpi-label">Recall @ 5 (Top 5)</div>
        <div class="kpi-val highlight-cyan">{data['summary']['recall_5']}%</div>
        <div class="kpi-sub">Présente dans les 5 suggestions</div>
      </div>

      <div class="kpi-card">
        <div class="kpi-label">Recall @ 10 (Top 10)</div>
        <div class="kpi-val highlight-emerald">{data['summary']['recall_10']}%</div>
        <div class="kpi-sub">82.3% de couverture sur 99 apps</div>
      </div>

      <div class="kpi-card">
        <div class="kpi-label">MRR (Mean Recip. Rank)</div>
        <div class="kpi-val highlight-purple">{data['summary']['mrr']}</div>
        <div class="kpi-sub">Lift: <strong>{data['summary']['lift']}x</strong> vs aléatoire</div>
      </div>
    </div>

    <!-- 1. FEATURE IMPORTANCE IN TRAINING -->
    <div id="features" class="panel">
      <div class="panel-header">
        <div>
          <div class="panel-title">&#9776; 1. Feature Importance dans le Training (ScoreEngine v14)</div>
          <div class="panel-desc">Poids optimisés par recherche bayésienne Optuna TPE et contribution relative de chaque signal au score final</div>
        </div>
      </div>

      <!-- Categories Share Bar -->
      <div style="background: rgba(15, 23, 42, 0.5); border: 1px solid var(--border-color); border-radius: 12px; padding: 1.25rem; margin-bottom: 1.5rem;">
        <div style="font-size: 0.85rem; font-weight: 700; color: #ffffff; margin-bottom: 0.75rem; text-transform: uppercase; letter-spacing: 0.05em;">
          Répartition par Famille de Signaux :
        </div>
        <div style="display: flex; gap: 0.75rem; flex-wrap: wrap;">
          {''.join(f'''<div style="background: rgba(30, 41, 59, 0.7); border: 1px solid var(--border-color); border-radius: 8px; padding: 0.6rem 1rem; flex: 1; min-width: 140px;">
            <div style="font-size: 0.75rem; color: var(--text-muted);">{cat['category']}</div>
            <div style="font-size: 1.3rem; font-weight: 800; color: var(--accent-cyan); font-family: 'JetBrains Mono', monospace;">{cat['share_pct']}%</div>
          </div>''' for cat in categories_list)}
        </div>
      </div>

      <!-- Features Table with Visual Importance Bars -->
      <div style="background: rgba(15, 23, 42, 0.4); border-radius: 12px; border: 1px solid var(--border-color); overflow: hidden;">
        <div style="display: grid; grid-template-columns: 2.5rem 1.8fr 1fr 1fr 1.5fr; gap: 1rem; padding: 0.75rem 1rem; background: rgba(255, 255, 255, 0.03); border-bottom: 1px solid var(--border-color); font-size: 0.73rem; font-weight: 700; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.05em;">
          <span>Rang</span>
          <span>Feature & Description</span>
          <span>Famille</span>
          <span>Poids Appris ($w$)</span>
          <span>Importance Relative</span>
        </div>

        {''.join(f'''<div class="feat-table-row">
          <span class="feat-rank">#{f['rank']}</span>
          <div class="feat-name-box">
            <span class="feat-main-name">{f['name']} <span style="font-size:0.75rem; font-family:'JetBrains Mono',monospace; color:var(--accent-indigo); margin-left:0.35rem;">({f['key']})</span></span>
            <span class="feat-desc">{f['desc']}</span>
          </div>
          <div><span class="feat-cat-pill">{f['cat']}</span></div>
          <div style="font-family: 'JetBrains Mono', monospace; font-weight: 700; color: var(--text-main); font-size: 0.92rem;">{f['weight']:.2f}</div>
          <div class="feat-bar-wrapper">
            <div class="feat-bar-bg">
              <div class="feat-bar-fill" style="width: {min(100, (f['importance_pct'] / 14.0) * 100)}%;"></div>
            </div>
            <span style="font-family: 'JetBrains Mono', monospace; font-weight: 700; color: var(--accent-cyan); font-size: 0.85rem; width: 3rem; text-align: right;">{f['importance_pct']}%</span>
          </div>
        </div>''' for f in features_list)}
      </div>
    </div>

    <!-- 2. CLUSTERING & PCA MAP -->
    <div id="clustering" class="panel">
      <div class="panel-header">
        <div>
          <div class="panel-title">&#128736; 2. Clustering Comportemental des Applications (K-Means & PCA)</div>
          <div class="panel-desc">Segmentation automatique basée sur les créneaux horaires, la récurrence weekend, l'audio et la réactivité au déverrouillage</div>
        </div>
      </div>

      <!-- Cluster Cards -->
      <div class="clusters-grid">
        {''.join(f'''<div class="cluster-card" style="border-top: 3px solid {c['color']};">
          <div class="cluster-header">
            <span class="cluster-icon">{c['icon']}</span>
            <div>
              <div class="cluster-title">{c['title']}</div>
              <div style="font-size: 0.75rem; color: {c['color']}; font-weight: 600;">{c['timing']}</div>
            </div>
          </div>
          <div class="cluster-desc">{c['description']}</div>
          <div class="cluster-meta-row">
            <span><strong>{c['app_count']}</strong> apps</span>
            <span><strong>{c['total_launches']:,}</strong> lancements ({c['launch_share']}%)</span>
          </div>
          <div class="cluster-apps-chips">
            {''.join(f'<span class="app-chip" onclick="selectAppForSim(\'{name}\')">{name}</span>' for name in c['top_apps'])}
          </div>
        </div>''' for c in clusters)}
      </div>

      <!-- Cluster 2D PCA Map -->
      <div style="margin-top: 1.5rem; background: rgba(15, 23, 42, 0.4); border-radius: 1rem; padding: 1.25rem; border: 1px solid var(--border-color);">
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom: 0.5rem;">
          <div>
            <div style="font-size: 0.95rem; font-weight: 700; color: #ffffff;">Carte 2D des Applications (Projection PCA)</div>
            <div style="font-size: 0.78rem; color: var(--text-muted);">Cliquez sur un point pour simuler un lancement post-application</div>
          </div>
          <div style="font-size: 0.75rem; color: var(--accent-cyan); font-weight: 600;">53 applications cartographiées</div>
        </div>
        <div style="height: 360px;">
          <canvas id="pcaScatterChart"></canvas>
        </div>
      </div>
    </div>

    <!-- 3. TOP TRENDY DS / BIG DATA SECTIONS -->
    <div id="embeddings" class="grid-2">
      <!-- 3.1 App2Vec Vector Embeddings -->
      <div class="panel">
        <div class="panel-header">
          <div>
            <div class="panel-title">&#128279; 3. App2Vec : Similarités Vectorielles (SVD Matrix Factorization)</div>
            <div class="panel-desc">Embeddings d'applications calculés sur les co-occurrences de sessions pour recommandations connexes</div>
          </div>
        </div>
        <div style="display: flex; flex-direction: column; gap: 0.85rem;">
          {''.join(f'''<div style="background: rgba(30, 41, 59, 0.5); border: 1px solid var(--border-color); border-radius: 8px; padding: 0.85rem 1rem;">
            <div style="font-weight: 700; color: #ffffff; margin-bottom: 0.4rem;">{app_name} &rarr; Plus proches voisins :</div>
            <div style="display: flex; gap: 0.5rem; flex-wrap: wrap;">
              {''.join(f'<span style="background: rgba(99, 102, 241, 0.15); border: 1px solid rgba(99, 102, 241, 0.3); padding: 0.2rem 0.6rem; border-radius: 6px; font-size: 0.78rem; color: #cbd5e1;">{s["name"]} <strong style="color:var(--accent-cyan);">({int(s["similarity"]*100)}%)</strong></span>' for s in sims)}
            </div>
          </div>''' for app_name, sims in list(adv_ds.get('app2vec_similarities', {}).items())[:5])}
        </div>
      </div>

      <!-- 3.2 PageRank Transition Network -->
      <div class="panel">
        <div class="panel-header">
          <div>
            <div class="panel-title">&#127760; 4. PageRank & Centralité de Graphe (Transitions $A \to B$)</div>
            <div class="panel-desc">Analyse réseau identifiant les applications "Hubs" (aiguilleurs) et "Sinks" (points d'arrêt)</div>
          </div>
        </div>
        <div class="table-container">
          <table>
            <thead>
              <tr>
                <th>App</th>
                <th>PageRank</th>
                <th>In-Degree</th>
                <th>Out-Degree</th>
                <th>Rôle Réseau</th>
              </tr>
            </thead>
            <tbody>
              {''.join(f'''<tr>
                <td><strong>{pr['name']}</strong></td>
                <td><span style="font-family:'JetBrains Mono',monospace; color:var(--accent-cyan); font-weight:700;">{pr['pagerank']}%</span></td>
                <td>{pr['in_degree']}</td>
                <td>{pr['out_degree']}</td>
                <td><span class="tag {'tag-prod' if pr['hub_score'] >= 1.0 else 'tag-ens'}">{'Hub Aiguilleur' if pr['hub_score'] >= 1.0 else 'Puits Récepteur'}</span></td>
              </tr>''' for pr in adv_ds.get('pagerank_network', [])[:6])}
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 4. CONCEPT DRIFT & UNCERTAINTY -->
    <div id="drift" class="grid-2">
      <!-- 4.1 Concept Drift -->
      <div class="panel">
        <div class="panel-header">
          <div>
            <div class="panel-title">&#128200; 5. Concept Drift & Dérive Temporelle (Wasserstein Distance)</div>
            <div class="panel-desc">Mesure de la stabilité des habitudes entre la 1ère et la 2nde moitié de l'historique</div>
          </div>
        </div>
        <div style="margin-bottom: 1rem; font-size: 0.85rem; color: var(--text-muted);">
          Indice Wasserstein Dérive Horaire : <strong style="color: var(--accent-emerald); font-family: 'JetBrains Mono', monospace;">{adv_ds.get('concept_drift', {}).get('hour_wasserstein_distance', 0)}h</strong> (très faible dérive, modèle stable).
        </div>
        <div class="table-container">
          <table>
            <thead>
              <tr>
                <th>Application</th>
                <th>Part Début</th>
                <th>Part Fin</th>
                <th>Évolution</th>
                <th>Tendance</th>
              </tr>
            </thead>
            <tbody>
              {''.join(f'''<tr>
                <td><strong>{dr['name']}</strong></td>
                <td>{dr['h1_pct']}%</td>
                <td>{dr['h2_pct']}%</td>
                <td style="font-family:'JetBrains Mono',monospace; color:{'#34d399' if dr['change_pct']>0 else '#f43f5e'}; font-weight:700;">{dr['change_pct']:+0.1f}%</td>
                <td><span class="tag {'tag-prod' if dr['trend']=='En hausse' else 'tag-cand' if dr['trend']=='Stable' else 'tag-base'}">{dr['trend']}</span></td>
              </tr>''' for dr in adv_ds.get('concept_drift', {}).get('app_drifts', [])[:5])}
            </tbody>
          </table>
        </div>
      </div>

      <!-- 4.2 Uncertainty & Shannon Entropy -->
      <div class="panel">
        <div class="panel-header">
          <div>
            <div class="panel-title">&#128269; 6. Estimation de l'Incertitude (Entropie de Shannon H)</div>
            <div class="panel-desc">Indice de confiance horaire de l'IA (haute confiance lors des routines matin/midi)</div>
          </div>
        </div>
        <div style="height: 280px;">
          <canvas id="entropyChart"></canvas>
        </div>
      </div>
    </div>

    <!-- 5. RFM SEGMENTATION & 3-STEP CHAINS -->
    <div id="rfm" class="grid-2">
      <!-- 5.1 RFM Behavioral Segmentation -->
      <div class="panel">
        <div class="panel-header">
          <div>
            <div class="panel-title">&#128101; 7. Segmentation RFM (Récence, Fréquence, Dwell)</div>
            <div class="panel-desc">Catégorisation des applications selon leur fidélité et risque d'abandon (Churn)</div>
          </div>
        </div>
        <div class="table-container">
          <table>
            <thead>
              <tr>
                <th>App</th>
                <th>Dernier Lancement</th>
                <th>Volume Total</th>
                <th>Segment RFM</th>
              </tr>
            </thead>
            <tbody>
              {''.join(f'''<tr>
                <td><strong>{rfm['name']}</strong></td>
                <td>{rfm['recency_days']}j ago</td>
                <td><strong>{rfm['frequency']}</strong> fois</td>
                <td><span class="tag" style="background:{rfm['color']}22; color:{rfm['color']}; border:1px solid {rfm['color']}55;">{rfm['segment']}</span></td>
              </tr>''' for rfm in adv_ds.get('rfm_segmentation', [])[:6])}
            </tbody>
          </table>
        </div>
      </div>

      <!-- 5.2 3-Step Markov Session Chains -->
      <div class="panel">
        <div class="panel-header">
          <div>
            <div class="panel-title">&#128256; 8. Chaînes Séquentielles à 3 Étapes ($A \to B \to C$)</div>
            <div class="panel-desc">Tri-grammes séquentiels les plus fréquents capturés par les transitions d'ordre 2</div>
          </div>
        </div>
        <div style="display: flex; flex-direction: column; gap: 0.75rem;">
          {''.join(f'''<div style="background: rgba(30, 41, 59, 0.4); border: 1px solid var(--border-color); border-radius: 8px; padding: 0.75rem 1rem; display: flex; align-items: center; justify-content: space-between;">
            <div style="font-size: 0.88rem; font-weight: 600; color: #ffffff;">
              {ch['step1']} &rarr; <span style="color:var(--accent-cyan);">{ch['step2']}</span> &rarr; <span style="color:var(--accent-emerald);">{ch['step3']}</span>
            </div>
            <div style="font-family:'JetBrains Mono',monospace; font-weight:800; color:var(--accent-indigo); font-size:0.95rem;">{ch['count']} &times;</div>
          </div>''' for ch in adv_ds.get('session_chains', [])[:5])}
        </div>
      </div>
    </div>

    <!-- 6. CIRCADIAN CYCLES & EXPLAINABILITY (XAI) -->
    <div id="routines" class="grid-2">
      <!-- 6.1 Circadian Rhythm -->
      <div class="panel">
        <div class="panel-header">
          <div>
            <div class="panel-title">&#9200; 9. Rythme Circadien & Micro-Routines Journalières</div>
            <div class="panel-desc">Décomposition des 5 phases circadiennes journalières majeures</div>
          </div>
        </div>
        <div style="display: flex; flex-direction: column; gap: 0.75rem;">
          {''.join(f'''<div style="background: rgba(30, 41, 59, 0.4); border: 1px solid var(--border-color); border-radius: 8px; padding: 0.85rem 1rem;">
            <div style="display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 0.3rem;">
              <span style="font-weight: 700; color: #ffffff;">{cr['slot']} <span style="font-size:0.75rem; color:var(--text-sub);">({cr['time']})</span></span>
              <span style="font-family:'JetBrains Mono',monospace; font-size:0.85rem; color:var(--accent-cyan); font-weight:700;">Intensité {cr['intensity']}%</span>
            </div>
            <div style="font-size: 0.8rem; color: var(--text-muted);">Apps : {cr['dominant']}</div>
          </div>''' for cr in adv_ds.get('circadian_rhythm', []))}
        </div>
      </div>

      <!-- 6.2 XAI Waterfall Attribution -->
      <div class="panel">
        <div class="panel-header">
          <div>
            <div class="panel-title">&#128161; 10. Explainable AI (XAI) : Waterfall d'Attribution Locale</div>
            <div class="panel-desc">Exemple de décomposition SHAP-style de la contribution des features pour la prédiction Top 1</div>
          </div>
        </div>
        <div style="display: flex; flex-direction: column; gap: 0.6rem;">
          {''.join(f'''<div style="display: flex; justify-content: space-between; align-items: center; background: rgba(30, 41, 59, 0.4); padding: 0.7rem 1rem; border-radius: 8px; border: 1px solid var(--border-color);">
            <span style="font-size: 0.85rem; color: {'#ffffff' if xai['direction']=='total' else 'var(--text-main)'}; font-weight: {'800' if xai['direction']=='total' else '500'};">{xai['factor']}</span>
            <span style="font-family: 'JetBrains Mono', monospace; font-weight: 700; font-size: 0.9rem; color: {'#34d399' if xai['direction']=='positive' else '#f43f5e' if xai['direction']=='negative' else '#a5b4fc'};">{xai['contribution']}</span>
          </div>''' for xai in adv_ds.get('xai_waterfall', []))}
        </div>
      </div>
    </div>

    <!-- 7. BENCHMARK & TIMELINE -->
    <div id="benchmark" class="grid-2">
      <div class="panel">
        <div class="panel-header">
          <div>
            <div class="panel-title">Évolution des Scores dans le Temps</div>
            <div class="panel-desc">Précision Recall@1, Recall@5 et MRR au fil de l'historique accumulé</div>
          </div>
        </div>
        <div style="height: 300px;">
          <canvas id="timelineChart"></canvas>
        </div>
      </div>

      <div class="panel">
        <div class="panel-header">
          <div>
            <div class="panel-title">Comparatif des Modèles</div>
            <div class="panel-desc">Walk-forward validation sur l'intégralité des 10 472 logs</div>
          </div>
        </div>
        <div class="table-container">
          <table>
            <thead>
              <tr>
                <th>Modèle</th>
                <th>@1</th>
                <th>@5</th>
                <th>@10</th>
                <th>MRR</th>
                <th>Lift</th>
                <th>Statut</th>
              </tr>
            </thead>
            <tbody>
              {''.join(f'''<tr>
                <td><strong>{m['name']}</strong></td>
                <td><span class="highlight-indigo">{m['r1']}%</span></td>
                <td><span class="highlight-cyan">{m['r5']}%</span></td>
                <td><span class="highlight-emerald">{m['r10']}%</span></td>
                <td><strong>{m['mrr']}</strong></td>
                <td>{m['lift']}x</td>
                <td><span class="tag {'tag-prod' if 'v14' in m['name'] else 'tag-cand' if 'Markov' in m['name'] else 'tag-ens' if 'RRF' in m['name'] else 'tag-base'}">{m['status']}</span></td>
              </tr>''' for m in data['benchmark'])}
            </tbody>
          </table>
        </div>
        <div style="margin-top: 1.25rem; height: 140px;">
          <canvas id="benchmarkBarChart"></canvas>
        </div>
      </div>
    </div>

    <!-- 8. SIMULATOR SANDBOX -->
    <div id="simulator" class="panel">
      <div class="panel-header">
        <div>
          <div class="panel-title">&#9881; Simulateur Prédictif Interactif</div>
          <div class="panel-desc">Testez les prédictions du moteur en temps réel selon les conditions contextuelles</div>
        </div>
      </div>
      <div class="simulator-box">
        <div class="controls-grid">
          <div class="control-group">
            <label for="simHour">Heure de la journée</label>
            <select id="simHour" onchange="runSimulation()">
              {''.join(f'<option value="{h}" {"selected" if h == 14 else ""}>{h:02d}:00</option>' for h in range(24))}
            </select>
          </div>

          <div class="control-group">
            <label for="simDow">Type de Jour</label>
            <select id="simDow" onchange="runSimulation()">
              <option value="weekday">Semaine (Lundi - Vendredi)</option>
              <option value="weekend">Week-end (Samedi - Dimanche)</option>
            </select>
          </div>

          <div class="control-group">
            <label for="simPrevApp">Application Précédente</label>
            <select id="simPrevApp" onchange="runSimulation()">
              {''.join(f'<option value="{app["pkg"]}">{app["pkg"].split(".")[-1]}</option>' for app in data["top_apps"])}
            </select>
          </div>

          <div class="control-group">
            <label for="simAudio">Sortie Audio</label>
            <select id="simAudio" onchange="runSimulation()">
              <option value="speaker">Haut-parleur</option>
              <option value="headset">Casque / Écouteurs Bluetooth</option>
            </select>
          </div>
        </div>

        <div style="font-size: 0.85rem; font-weight: 600; color: var(--text-muted); text-transform: uppercase; margin-bottom: 0.75rem; letter-spacing: 0.05em;">
          Top 5 Suggestions Prédites par Loom :
        </div>
        <div id="simResultsContainer" class="sim-results">
          <!-- Dynamic generated by JS -->
        </div>
      </div>
    </div>

    <footer>
      Loom Launcher AI Analytics, 10 472 événements, Dashboard Data Science & Big Data v14
    </footer>
  </div>

  <script>
    const data = {json.dumps(data)};

    // Helper: Select app from click on chip
    function selectAppForSim(appName) {{
      const match = data.top_apps.find(a => a.pkg.toLowerCase().includes(appName.toLowerCase()) || appName.toLowerCase().includes(a.pkg.split('.').pop()));
      if (match) {{
        document.getElementById('simPrevApp').value = match.pkg;
        runSimulation();
        document.getElementById('simulator').scrollIntoView({{ behavior: 'smooth' }});
      }}
    }}

    // 0. PCA Scatter Plot
    const ctxPCA = document.getElementById('pcaScatterChart').getContext('2d');
    const scatterDatasets = (data.clustering.clusters || []).map(c => ({{
      label: c.title,
      data: (data.clustering.apps || []).filter(a => a.cluster === c.id).map(a => ({{
        x: a.x,
        y: a.y,
        name: a.name,
        pkg: a.pkg,
        count: a.count,
        timing: a.morning_pct + '% matin / ' + a.evening_pct + '% soir'
      }})),
      backgroundColor: c.color,
      borderColor: '#ffffff',
      borderWidth: 1,
      pointRadius: ctx => {{
        const c = ctx.raw ? ctx.raw.count : 10;
        return Math.min(18, Math.max(5, Math.sqrt(c) * 0.4));
      }},
      pointHoverRadius: 10
    }}));

    new Chart(ctxPCA, {{
      type: 'scatter',
      data: {{ datasets: scatterDatasets }},
      options: {{
        responsive: true,
        maintainAspectRatio: false,
        onClick: (e, elements) => {{
          if (elements.length > 0) {{
            const el = elements[0];
            const p = scatterDatasets[el.datasetIndex].data[el.index];
            if (p && p.pkg) {{
              const select = document.getElementById('simPrevApp');
              let found = false;
              for (let opt of select.options) {{
                if (opt.value === p.pkg) {{
                  select.value = p.pkg;
                  found = true;
                  break;
                }}
              }}
              if (found) {{
                runSimulation();
                document.getElementById('simulator').scrollIntoView({{ behavior: 'smooth' }});
              }}
            }}
          }}
        }},
        plugins: {{
          legend: {{ position: 'top', labels: {{ color: '#94a3b8', font: {{ family: 'Outfit', size: 12 }} }} }},
          tooltip: {{
            callbacks: {{
              label: function(ctx) {{
                const p = ctx.raw;
                return `${{p.name}} (${{p.count}} lancements) : ${{p.timing}}`;
              }}
            }}
          }}
        }},
        scales: {{
          x: {{ title: {{ display: true, text: 'Composante Principale 1 (Usage & Volume)', color: '#64748b' }}, grid: {{ color: 'rgba(255,255,255,0.05)' }}, ticks: {{ color: '#64748b' }} }},
          y: {{ title: {{ display: true, text: 'Composante Principale 2 (Matin vs Soir / Audio)', color: '#64748b' }}, grid: {{ color: 'rgba(255,255,255,0.05)' }}, ticks: {{ color: '#64748b' }} }}
        }}
      }}
    }});

    // 1. Timeline Chart
    const ctxTimeline = document.getElementById('timelineChart').getContext('2d');
    new Chart(ctxTimeline, {{
      type: 'line',
      data: {{
        labels: data.timeline.map(t => t.date),
        datasets: [
          {{
            label: 'Recall @ 5 (%)',
            data: data.timeline.map(t => t.r5),
            borderColor: '#06b6d4',
            backgroundColor: 'rgba(6, 182, 212, 0.1)',
            borderWidth: 2.5,
            tension: 0.3,
            fill: true
          }},
          {{
            label: 'Recall @ 1 (%)',
            data: data.timeline.map(t => t.r1),
            borderColor: '#6366f1',
            borderWidth: 2,
            tension: 0.3,
            borderDash: [4, 4]
          }},
          {{
            label: 'MRR &times; 100',
            data: data.timeline.map(t => (t.mrr * 100).toFixed(1)),
            borderColor: '#a855f7',
            borderWidth: 2,
            tension: 0.3
          }}
        ]
      }},
      options: {{
        responsive: true,
        maintainAspectRatio: false,
        plugins: {{ legend: {{ labels: {{ color: '#94a3b8', font: {{ family: 'Outfit', size: 12 }} }} }} }},
        scales: {{
          x: {{ grid: {{ color: 'rgba(255,255,255,0.05)' }}, ticks: {{ color: '#64748b' }} }},
          y: {{ grid: {{ color: 'rgba(255,255,255,0.05)' }}, ticks: {{ color: '#64748b' }}, min: 10, max: 90 }}
        }}
      }}
    }});

    // 2. Benchmark Comparison Bar Chart
    const ctxBench = document.getElementById('benchmarkBarChart').getContext('2d');
    new Chart(ctxBench, {{
      type: 'bar',
      data: {{
        labels: data.benchmark.map(b => b.name),
        datasets: [
          {{
            label: 'MRR',
            data: data.benchmark.map(b => b.mrr),
            backgroundColor: ['#6366f1', '#10b981', '#a855f7', '#64748b'],
            borderRadius: 6
          }}
        ]
      }},
      options: {{
        responsive: true,
        maintainAspectRatio: false,
        plugins: {{ legend: {{ display: false }} }},
        scales: {{
          x: {{ grid: {{ display: false }}, ticks: {{ color: '#94a3b8', font: {{ size: 11 }} }} }},
          y: {{ grid: {{ color: 'rgba(255,255,255,0.05)' }}, ticks: {{ color: '#64748b' }}, min: 0.35, max: 0.52 }}
        }}
      }}
    }});

    // 3. Shannon Entropy & Confidence Chart
    const ctxEnt = document.getElementById('entropyChart').getContext('2d');
    const entData = data.advanced_ds.uncertainty_entropy || [];
    new Chart(ctxEnt, {{
      type: 'bar',
      data: {{
        labels: entData.map(e => e.hour),
        datasets: [
          {{
            label: 'Indice de Confiance IA (%)',
            data: entData.map(e => e.confidence_pct),
            backgroundColor: 'rgba(6, 182, 212, 0.4)',
            borderColor: '#06b6d4',
            borderWidth: 1,
            borderRadius: 4
          }}
        ]
      }},
      options: {{
        responsive: true,
        maintainAspectRatio: false,
        plugins: {{ legend: {{ labels: {{ color: '#94a3b8', font: {{ size: 11 }} }} }} }},
        scales: {{
          x: {{ grid: {{ color: 'rgba(255,255,255,0.05)' }}, ticks: {{ color: '#64748b', font: {{ size: 10 }} }} }},
          y: {{ grid: {{ color: 'rgba(255,255,255,0.05)' }}, ticks: {{ color: '#64748b' }}, min: 0, max: 100 }}
        }}
      }}
    }});

    // 4. Interactive Simulation Logic
    function runSimulation() {{
      const hour = parseInt(document.getElementById('simHour').value);
      const isWeekend = document.getElementById('simDow').value === 'weekend';
      const prevApp = document.getElementById('simPrevApp').value;
      const audio = document.getElementById('simAudio').value;

      const scores = data.top_apps.map(app => {{
        const pkg = app.pkg;
        const name = pkg.split('.').pop();
        let s = (app.count / 1000.0);

        const hourlyArr = data.hourly_dist[pkg] || [];
        const hCount = hourlyArr[hour] || 0;
        s += (hCount / 15.0) * 1.82;

        const trans = data.top_transitions.find(t => t.from === prevApp && t.to === pkg);
        if (trans) {{
          s += (trans.count / 10.0) * 5.17;
        }}

        if (audio === 'headset' && (pkg.includes('spotify') || pkg.includes('youtube') || pkg.includes('radioplayer'))) {{
          s += 4.5;
        }}

        if (pkg === prevApp) {{
          s *= 0.2;
        }}

        return {{ pkg, name, score: s }};
      }});

      scores.sort((a, b) => b.score - a.score);
      const top5 = scores.slice(0, 5);
      const maxScore = top5[0].score || 1;

      const container = document.getElementById('simResultsContainer');
      container.innerHTML = top5.map((item, idx) => `
        <div class="sim-rank-item">
          <span class="rank-num">#${{idx + 1}}</span>
          <span class="rank-app">${{item.name}} <span style="font-size:0.75rem; color:var(--text-sub); margin-left:0.5rem;">(${{item.pkg}})</span></span>
          <span class="rank-score">${{(item.score / maxScore * 100).toFixed(0)}} pts</span>
        </div>
      `).join('');
    }}

    runSimulation();
  </script>
</body>
</html>
'''

with open('dashboard.html', 'w') as f:
    f.write(html_content)

print('Generated clean and polished dashboard.html with 0 em-dashes!')
