import json
import collections
import numpy as np
from sklearn.cluster import KMeans
from sklearn.decomposition import PCA
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import silhouette_score

def run_clustering(data_path='usage_log.json'):
    with open(data_path) as f:
        events = json.load(f)

    events = sorted(events, key=lambda e: e['timestampMillis'])
    app_events = collections.defaultdict(list)
    for e in events:
        app_events[e['packageName']].append(e)

    # Session starters
    session_starters = collections.defaultdict(int)
    for i in range(len(events)):
        if i == 0 or (events[i]['timestampMillis'] - events[i-1]['timestampMillis'] > 70_000):
            session_starters[events[i]['packageName']] += 1

    # Filter user-facing apps with >= 12 events
    valid_apps = [pkg for pkg, evs in app_events.items() if len(evs) >= 12 and not pkg.startswith('com.android.internal')]

    app_names = {
        'com.android.chrome': 'Chrome',
        'com.google.android.googlequicksearchbox': 'Google Search',
        'com.google.android.youtube': 'YouTube',
        'com.instagram.android': 'Instagram',
        'com.facebook.orca': 'Messenger',
        'com.google.android.apps.messaging': 'Messages SMS',
        'com.spotify.music': 'Spotify',
        'com.whatsapp': 'WhatsApp',
        'com.google.android.apps.maps': 'Google Maps',
        'com.google.android.calendar': 'Google Calendar',
        'com.google.android.gm': 'Gmail',
        'com.google.android.apps.dynamite': 'Google Chat',
        'com.facebook.katana': 'Facebook',
        'com.twitter.android': 'X (Twitter)',
        'fr.playsoft.teleloisirs': 'Télé-Loisirs',
        'com.devhd.feedly': 'Feedly',
        'com.openai.chatgpt': 'ChatGPT',
        'com.anthropic.claude': 'Claude',
        'com.google.android.apps.bard': 'Gemini',
        'com.google.android.apps.walletnfcrel': 'Google Wallet',
        'com.google.android.gms': 'Google Services',
        'com.waze': 'Waze',
        'fr.geovelo': 'Geovelo',
        'com.google.android.apps.photos': 'Google Photos',
        'com.google.android.GoogleCamera': 'Caméra',
        'com.tradingview.tradingviewapp': 'TradingView',
        'com.github.android': 'GitHub',
        'com.android.settings': 'Paramètres',
        'com.google.android.dialer': 'Téléphone',
        'com.android.vending': 'Play Store',
        'com.niksoftware.snapseed': 'Snapseed',
        'com.netflix.mediaclient': 'Netflix',
        'com.google.android.apps.docs': 'Google Drive',
        'com.google.android.apps.docs.editors.docs': 'Google Docs',
        'com.google.android.calculator': 'Calculatrice',
    }

    feature_list = []
    app_meta = []

    for pkg in valid_apps:
        evs = app_events[pkg]
        n = len(evs)
        
        morn = sum(1 for e in evs if 6 <= e.get('hour', 0) <= 11) / n
        aft = sum(1 for e in evs if 12 <= e.get('hour', 0) <= 17) / n
        eve = sum(1 for e in evs if 18 <= e.get('hour', 0) or e.get('hour', 0) <= 5) / n
        wkd = sum(1 for e in evs if e.get('dayOfWeek', 1) in (6, 7)) / n
        starter = session_starters[pkg] / n
        
        audio_evs = [e for e in evs if e.get('audioActive') is not None]
        audio = (sum(1 for e in audio_evs if e['audioActive']) / len(audio_evs)) if len(audio_evs) >= 5 else 0.0
        
        hours = [0]*24
        for e in evs:
            hours[e.get('hour', 0)] += 1
            
        features = [morn, aft, eve, wkd, starter, audio]
        feature_list.append(features)
        
        clean_name = app_names.get(pkg, pkg.split('.')[-1].capitalize())
        app_meta.append({
            'pkg': pkg,
            'name': clean_name,
            'count': n,
            'morning_pct': round(morn * 100, 1),
            'afternoon_pct': round(aft * 100, 1),
            'evening_pct': round(eve * 100, 1),
            'weekend_pct': round(wkd * 100, 1),
            'audio_pct': round(audio * 100, 1),
            'starter_pct': round(starter * 100, 1),
            'peak_hour': int(np.argmax(hours))
        })

    X = np.array(feature_list)
    scaler = StandardScaler()
    X_scaled = scaler.fit_transform(X)

    # 4 clean interpretable clusters
    kmeans = KMeans(n_clusters=4, random_state=42, n_init=25)
    labels = kmeans.fit_predict(X_scaled)
    sil_score = silhouette_score(X_scaled, labels)

    # 2D PCA for visual mapping
    pca = PCA(n_components=2, random_state=42)
    coords_2d = pca.fit_transform(X_scaled)

    for i, meta in enumerate(app_meta):
        meta['cluster'] = int(labels[i])
        meta['x'] = round(float(coords_2d[i, 0]), 2)
        meta['y'] = round(float(coords_2d[i, 1]), 2)

    # Define metadata for each cluster
    cluster_profiles = [
        {
            'id': 0,
            'title': 'Mobilité, Travail & Journée',
            'icon': '💼',
            'desc': 'Applications professionnelles et de déplacement utilisées principalement en journée en semaine.',
            'color': '#6366f1',
            'timing': 'Journée (8h-17h)'
        },
        {
            'id': 1,
            'title': 'Messagerie & Social Réflexe',
            'icon': '💬',
            'desc': 'Applications de communication instantanée avec forte fréquence en fin de journée et soirée.',
            'color': '#06b6d4',
            'timing': 'Soirée (18h-23h)'
        },
        {
            'id': 2,
            'title': 'Navigation, Recherche & Multimédia',
            'icon': '🌐',
            'desc': 'Piliers du quotidien à très fort volume, écoute audio et recherche d\'informations.',
            'color': '#10b981',
            'timing': 'Continu & Weekend'
        },
        {
            'id': 3,
            'title': 'Médias & Outils Spécifiques',
            'icon': '🌙',
            'desc': 'Applications spécialisées ou consultées ponctuellement le weekend et tard en soirée.',
            'color': '#f59e0b',
            'timing': 'Weekend & Soirée'
        }
    ]

    # Map actual cluster index to best matching semantic profile by centroid inspection
    centroids = kmeans.cluster_centers_
    # Feature indices: 0:morn, 1:aft, 2:eve, 3:wkd, 4:starter, 5:audio
    
    # Assign cluster labels based on profile statistics
    final_clusters = []
    for c_id in range(4):
        c_apps = [a for a in app_meta if a['cluster'] == c_id]
        c_apps.sort(key=lambda a: a['count'], reverse=True)
        tot_count = sum(a['count'] for a in c_apps)
        
        avg_m = np.mean([a['morning_pct'] for a in c_apps])
        avg_e = np.mean([a['evening_pct'] for a in c_apps])
        avg_w = np.mean([a['weekend_pct'] for a in c_apps])
        avg_a = np.mean([a['audio_pct'] for a in c_apps])
        
        # Match heuristic
        if avg_e >= 50.0:
            profile = cluster_profiles[1] # Social / Soirée
        elif avg_m >= 25.0 and avg_w <= 22.0:
            profile = cluster_profiles[0] # Travail / Journée
        elif tot_count > 4000 or avg_a >= 20.0:
            profile = cluster_profiles[2] # Nav / Multimédia
        else:
            profile = cluster_profiles[3] # Spécifique / Weekend
            
        final_clusters.append({
            'id': c_id,
            'title': profile['title'],
            'icon': profile['icon'],
            'description': profile['desc'],
            'timing': profile['timing'],
            'color': profile['color'],
            'app_count': len(c_apps),
            'total_launches': tot_count,
            'launch_share': round(tot_count / len(events) * 100, 1),
            'avg_morning': round(avg_m, 1),
            'avg_evening': round(avg_e, 1),
            'avg_weekend': round(avg_w, 1),
            'avg_audio': round(avg_a, 1),
            'top_apps': [a['name'] for a in c_apps[:6]],
            'apps': c_apps
        })

    final_clusters.sort(key=lambda c: c['total_launches'], reverse=True)

    title_by_cid = {c['id']: c['title'] for c in final_clusters}
    color_by_cid = {c['id']: c['color'] for c in final_clusters}
    for a in app_meta:
        a['cluster_title'] = title_by_cid.get(a['cluster'], '')
        a['color'] = color_by_cid.get(a['cluster'], '#6366f1')

    print(f"Clustering complete: {len(app_meta)} apps clustered. Silhouette Score: {sil_score:.3f}")
    for c in final_clusters:
        print(f"[{c['icon']} {c['title']}] ({c['app_count']} apps, {c['total_launches']} lancements - {c['launch_share']}%): {', '.join(c['top_apps'])}")

    return {
        'silhouette_score': round(float(sil_score), 3),
        'pca_variance_ratio': [round(float(v), 3) for v in pca.explained_variance_ratio_],
        'clusters': final_clusters,
        'apps': sorted(app_meta, key=lambda a: a['count'], reverse=True)
    }

if __name__ == '__main__':
    res = run_clustering()
    with open('dashboard_data.json') as f:
        data = json.load(f)
    data['clustering'] = res
    with open('dashboard_data.json', 'w') as f:
        json.dump(data, f, indent=2)
    print("Updated dashboard_data.json with clean clustering!")
